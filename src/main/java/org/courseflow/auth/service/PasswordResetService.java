package org.courseflow.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.courseflow.auth.dto.ResetPasswordRequest;
import org.courseflow.auth.entity.PasswordResetToken;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AccountPasswordSnapshot;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.system.CourseFlowMetrics;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.common.validation.PasswordBytes;
import org.courseflow.identity.entity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {

  private final Clock clock;
  private final CourseFlowMetrics metrics;
  private final TransactionTemplate transaction;
  private final boolean includeTestToken;

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

  private static final Duration REQUEST_COOLDOWN = Duration.ofMinutes(2);

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserAccountRepository userAccountRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final CredentialLifecycle credentialLifecycle;
  private final PasswordEncoder passwordEncoder;
  private final JavaMailSender mailSender;
  private final String senderEmail;
  private final String resetPageUrl;

  public PasswordResetService(
      UserAccountRepository userAccountRepository,
      PasswordResetTokenRepository tokenRepository,
      CredentialLifecycle credentialLifecycle,
      PasswordEncoder passwordEncoder,
      JavaMailSender mailSender,
      @Value("${spring.mail.username}") String senderEmail,
      @Value("${courseflow.password-reset-url}") String resetPageUrl,
      Clock clock,
      PlatformTransactionManager transactionManager,
      Environment environment,
      CourseFlowMetrics metrics) {
    this.clock = clock;
    this.metrics = metrics;
    this.transaction = new TransactionTemplate(transactionManager);
    this.includeTestToken =
        environment.acceptsProfiles(Profiles.of("dev"))
            && !environment.acceptsProfiles(Profiles.of("prod"));
    this.userAccountRepository = userAccountRepository;
    this.tokenRepository = tokenRepository;
    this.credentialLifecycle = credentialLifecycle;
    this.passwordEncoder = passwordEncoder;
    this.mailSender = mailSender;
    this.senderEmail = senderEmail;
    this.resetPageUrl = resetPageUrl;
  }

  public void requestReset(String email) {
    String normalizedEmail = EmailAddresses.normalize(email);
    ResetDelivery delivery = transaction.execute(status -> createResetToken(normalizedEmail));
    // TransactionTemplate has committed and released the JDBC connection.
    // Do not send on rollback or while holding an account lock.
    if (delivery != null) {
      sendResetEmail(delivery.recipient(), delivery.rawToken());
    }
  }

  private ResetDelivery createResetToken(String normalizedEmail) {
    Long accountId = userAccountRepository.findAccountIdByUserEmail(normalizedEmail).orElse(null);
    if (accountId == null) {
      return null;
    }
    UserAccount account = userAccountRepository.findByIdForUpdate(accountId).orElse(null);
    if (account == null
        || !normalizedEmail.equals(account.getUser().getEmail())
        || account.getUser().getStatus() != UserStatus.ACTIVE) {
      return null;
    }

    Instant now = clock.instant();
    Instant cooldownThreshold = now.plus(TOKEN_LIFETIME.minus(REQUEST_COOLDOWN));
    if (tokenRepository.existsByUserAccount_IdAndUsedAtIsNullAndExpiresAtAfter(
        account.getId(), cooldownThreshold)) {
      return null;
    }

    tokenRepository.invalidateUnusedTokensForAccount(account.getId(), now);
    String rawToken = generateToken();
    tokenRepository.saveAndFlush(
        new PasswordResetToken(account, hashToken(rawToken), now.plus(TOKEN_LIFETIME)));
    return new ResetDelivery(account.getUser().getEmail(), rawToken);
  }

  private record ResetDelivery(String recipient, String rawToken) {
    @Override
    public String toString() {
      return "ResetDelivery[redacted]";
    }
  }

  public void resetPassword(ResetPasswordRequest request) {
    String newPassword = request.newPassword();
    PasswordBytes.requireFits(newPassword, "New password");
    String tokenHash = hashToken(request.token());
    AccountPasswordSnapshot snapshot =
        tokenRepository
            .findValidPasswordSnapshot(tokenHash, clock.instant(), UserStatus.ACTIVE)
            .orElseThrow(this::invalidToken);
    // Avoid BCrypt for invalid tokens. Revalidate both token and hash under lock below.
    if (passwordEncoder.matches(newPassword, snapshot.passwordHash())) {
      throw new BadRequestException("New password must differ from current password");
    }
    String newHash = passwordEncoder.encode(newPassword);
    transaction.executeWithoutResult(
        status -> {
          UserAccount account =
              userAccountRepository
                  .findByIdForUpdate(snapshot.accountId())
                  .orElseThrow(this::invalidToken);
          PasswordResetToken token =
              tokenRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(this::invalidToken);
          Instant now = clock.instant();
          if (!token.isValid(now)
              || !token.getUserAccount().getId().equals(account.getId())
              || account.getUser().getStatus() != UserStatus.ACTIVE
              || !snapshot.passwordHash().equals(account.getPasswordHash())) {
            throw invalidToken();
          }
          account.replacePasswordHash(newHash);
          token.markUsed(now);
          tokenRepository.invalidateUnusedTokensForAccount(account.getId(), now);
          credentialLifecycle.revokeSessions(account.getUser().getId());
        });
  }

  private BadRequestException invalidToken() {
    return new BadRequestException("Invalid or expired reset token");
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);

    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");

      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(hash);

    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private void sendResetEmail(String recipient, String rawToken) {
    String url = resetPageUrl + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

    SimpleMailMessage message = new SimpleMailMessage();

    message.setFrom(senderEmail);
    message.setTo(recipient);
    message.setSubject("CourseFlow - Password reset");

    message.setText(
        "A password reset was requested for CourseFlow.\n\n"
            + "Reset link:\n"
            + url
            + "\n\n"
            + (includeTestToken
                ? "For testing through requests.http, use this one-time token:\n"
                    + rawToken
                    + "\n\n"
                : "")
            + "The token expires in 30 minutes.\n"
            + "If you did not request a reset, "
            + "ignore this message.");

    try {
      mailSender.send(message);
      metrics.resetMailSent();
      log.info("event=password_reset_mail_sent");
    } catch (MailException ex) {
      metrics.resetMailFailed();
      // Do not disclose whether the email exists,
      // and never log the raw reset token.
      log.error(
          "Unable to send password reset email via SMTP: {}; stack={}",
          ex.getClass().getName(),
          Arrays.toString(ex.getStackTrace()));
    }
  }
}


package org.courseflow.auth.service;

import org.courseflow.auth.dto.ResetPasswordRequest;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.PasswordResetToken;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.identity.entity.UserStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetService.class);

    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(30);

    private static final Duration REQUEST_COOLDOWN =
            Duration.ofMinutes(2);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String resetPageUrl;

    public PasswordResetService(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository tokenRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail,
            @Value("${courseflow.password-reset-url}")
            String resetPageUrl
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.resetPageUrl = resetPageUrl;
    }

    @Transactional
    public void requestReset(String email) {

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        UserAccount foundAccount = userAccountRepository
                .findByUser_EmailIgnoreCase(normalizedEmail)
                .orElse(null);

        // The API returns the same response for unknown,
        // inactive and existing users.
        if (foundAccount == null) {
            return;
        }

        UserAccount account = userAccountRepository
                .findByIdForUpdate(foundAccount.getId())
                .orElse(null);

        if (account == null
                || account.getUser().getStatus()
                != UserStatus.ACTIVE) {
            return;
        }

        Instant now = Instant.now();

        // At most one new request per two minutes
        // for this account.
        Instant cooldownThreshold = now.plus(
                TOKEN_LIFETIME.minus(REQUEST_COOLDOWN)
        );

        if (tokenRepository
                .existsByUserAccount_IdAndUsedAtIsNullAndExpiresAtAfter(
                        account.getId(),
                        cooldownThreshold
                )) {
            return;
        }

        // New request invalidates previous reset links.
        tokenRepository.invalidateUnusedTokensForAccount(
                account.getId(),
                now
        );

        String rawToken = generateToken();

        PasswordResetToken resetToken =
                new PasswordResetToken(
                        account,
                        hashToken(rawToken),
                        now.plus(TOKEN_LIFETIME)
                );

        tokenRepository.saveAndFlush(resetToken);

        String recipient = account.getUser().getEmail();

        // Send only after PostgreSQL commits the token.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendResetEmail(recipient, rawToken);
                    }
                }
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        String newPassword = request.newPassword();

        if (newPassword
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException(
                    "New password must not exceed 72 UTF-8 bytes"
            );
        }

        String tokenHash = hashToken(request.token());

        Long accountId = tokenRepository
                .findAccountIdByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new BadRequestException(
                                "Invalid or expired reset token"
                        )
                );

        // Serialize password resets for one account.
        UserAccount account = userAccountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() ->
                        new BadRequestException(
                                "Invalid or expired reset token"
                        )
                );

        PasswordResetToken token = tokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() ->
                        new BadRequestException(
                                "Invalid or expired reset token"
                        )
                );

        Instant now = Instant.now();

        if (!token.isValid(now)
                || account.getUser().getStatus()
                != UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Invalid or expired reset token"
            );
        }

        if (passwordEncoder.matches(
                newPassword,
                account.getPasswordHash()
        )) {
            throw new BadRequestException(
                    "New password must differ from current password"
            );
        }

        account.replacePasswordHash(
                passwordEncoder.encode(newPassword)
        );

        // Mark the current token as used.
        token.markUsed(now);

        // Invalidate any other outstanding reset tokens.
        tokenRepository.invalidateUnusedTokensForAccount(
                account.getId(),
                now
        );

        // Every JWT issued before the reset is revoked.
        authSessionRepository
                .findAllByUserAccount_User_IdAndRevokedFalse(
                        account.getUser().getId()
                )
                .forEach(AuthSession::revoke);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }

    private void sendResetEmail(
            String recipient,
            String rawToken
    ) {
        String url = resetPageUrl
                + "?token="
                + URLEncoder.encode(
                rawToken,
                StandardCharsets.UTF_8
        );

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("CourseFlow - Password reset");

        message.setText(
                "A password reset was requested for CourseFlow.\n\n"
                        + "Reset link:\n"
                        + url
                        + "\n\n"
                        + "For testing through requests.http, "
                        + "use this one-time token:\n"
                        + rawToken
                        + "\n\n"
                        + "The token expires in 30 minutes.\n"
                        + "If you did not request a reset, "
                        + "ignore this message."
        );

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // Do not disclose whether the email exists,
            // and never log the raw reset token.
            log.error(
                    "Unable to send password reset email via SMTP",
                    ex
            );
        }
    }
}

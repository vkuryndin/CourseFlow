package org.courseflow.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AccountPasswordSnapshot;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.common.validation.PasswordBytes;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

  private final Clock clock;
  private final AccountCreator accountCreator;
  private final TransactionTemplate transaction;

  private final UserAccountRepository userAccountRepository;
  private final AuthSessionRepository authSessionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final String dummyPasswordHash;

  public AuthService(
      UserAccountRepository userAccountRepository,
      AuthSessionRepository authSessionRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      Clock clock,
      PlatformTransactionManager transactionManager,
      AccountCreator accountCreator) {
    this.clock = clock;
    this.accountCreator = accountCreator;
    this.transaction = new TransactionTemplate(transactionManager);
    this.userAccountRepository = userAccountRepository;
    this.authSessionRepository = authSessionRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  public RegistrationResponse register(RegistrationRequest request) {
    return accountCreator.create(
        request.displayName(),
        request.email(),
        Set.of(UserRole.STUDENT),
        request.password(),
        false,
        RegistrationResponse::from);
  }

  public LoginResponse login(LoginRequest request) {
    String email = EmailAddresses.normalize(request.email());
    PasswordBytes.requireFits(request.password(), "Password");

    AccountPasswordSnapshot snapshot =
        userAccountRepository.findPasswordByEmail(email).orElse(null);
    // Comparable BCrypt work for an unknown email; no database lock while hashing.
    boolean matches =
        passwordEncoder.matches(
            request.password(), snapshot == null ? dummyPasswordHash : snapshot.passwordHash());
    if (snapshot == null || !matches) throw invalidCredentials();

    return transaction.execute(
        status -> {
          // Serialize with account updates and password resets. The hash must
          // still be identical to the one verified outside the transaction.
          UserAccount account =
              userAccountRepository
                  .findByIdForUpdate(snapshot.accountId())
                  .orElseThrow(this::invalidCredentials);
          User user = account.getUser();
          if (!snapshot.passwordHash().equals(account.getPasswordHash())
              || !email.equals(user.getEmail())
              || user.getStatus() != UserStatus.ACTIVE
              || user.getRoles().isEmpty()) {
            throw invalidCredentials();
          }
          Instant now = clock.instant();
          AuthSession session = new AuthSession(account, now.plus(1, ChronoUnit.HOURS), now);
          AuthSession savedSession = authSessionRepository.saveAndFlush(session);
          String accessToken = jwtService.generateAccessToken(savedSession);
          return new LoginResponse(
              accessToken,
              "Bearer",
              savedSession.getExpiresAt(),
              user.getId(),
              user.getDisplayName(),
              user.getRoles(),
              account.isPasswordChangeRequired());
        });
  }

  private BadCredentialsException invalidCredentials() {
    return new BadCredentialsException("Invalid email or password");
  }
}

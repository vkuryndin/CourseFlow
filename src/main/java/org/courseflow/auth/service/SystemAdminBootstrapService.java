package org.courseflow.auth.service;

import java.time.Clock;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.common.validation.PasswordBytes;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.identity.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminBootstrapService {

  private final Clock clock;
  private final JdbcTemplate jdbcTemplate;

  private final UserDirectory userDirectory;
  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${COURSEFLOW_ADMIN_EMAIL:}")
  private String adminEmail;

  @Value("${COURSEFLOW_ADMIN_PASSWORD:}")
  private String adminPassword;

  public SystemAdminBootstrapService(
      UserDirectory userDirectory,
      UserAccountRepository userAccountRepository,
      PasswordEncoder passwordEncoder,
      Clock clock,
      JdbcTemplate jdbcTemplate) {
    this.clock = clock;
    this.jdbcTemplate = jdbcTemplate;
    this.userDirectory = userDirectory;
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void initialize() {
    // Same database-wide lock as the invariant initializer. Released at commit.
    jdbcTemplate.execute("SELECT pg_advisory_xact_lock(62130001)");
    if (userDirectory.systemAdminExists()) {
      return;
    }

    if (adminEmail.isBlank() || adminPassword.isBlank()) {
      throw new IllegalStateException(
          "Configure COURSEFLOW_ADMIN_EMAIL and " + "COURSEFLOW_ADMIN_PASSWORD");
    }

    String email = EmailAddresses.normalize(adminEmail);

    if (adminPassword.length() < 8 || !PasswordBytes.fits(adminPassword)) {
      throw new IllegalStateException(
          "System ADMIN password must contain at least "
              + "8 characters and at most 72 UTF-8 bytes");
    }

    if (userDirectory.emailInUse(email)) {
      throw new IllegalStateException("System ADMIN email is already used");
    }

    User admin = userDirectory.createSystemAdmin(email, clock.instant());

    UserAccount account =
        new UserAccount(admin, passwordEncoder.encode(adminPassword), clock.instant());

    userAccountRepository.saveAndFlush(account);
  }
}

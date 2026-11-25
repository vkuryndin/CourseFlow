package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class SystemAdminBootstrapServiceTest {
  private final UserDirectory directory = mock(UserDirectory.class);
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final SystemAdminBootstrapService service =
      new SystemAdminBootstrapService(directory, accounts, encoder, CLOCK, jdbc);

  private void configure(String email, String password) {
    ReflectionTestUtils.setField(service, "adminEmail", email);
    ReflectionTestUtils.setField(service, "adminPassword", password);
  }

  @Test
  void existingAdminSkipsCreation() {
    when(directory.systemAdminExists()).thenReturn(true);
    service.initialize();
    verify(jdbc).execute("SELECT pg_advisory_xact_lock(62130001)");
    verifyNoInteractions(accounts, encoder);
  }

  @Test
  void blankEmailRejected() {
    configure("", "Valid-pass-1");
    assertThrows(IllegalStateException.class, service::initialize);
    verifyNoInteractions(accounts, encoder);
  }

  @Test
  void blankPasswordRejected() {
    configure("admin@example.test", " ");
    assertThrows(IllegalStateException.class, service::initialize);
  }

  @Test
  void shortPasswordRejected() {
    configure("admin@example.test", "short");
    assertThrows(IllegalStateException.class, service::initialize);
  }

  @Test
  void longUtf8PasswordRejected() {
    configure("admin@example.test", "я".repeat(37));
    assertThrows(IllegalStateException.class, service::initialize);
  }

  @Test
  void duplicateEmailRejected() {
    configure(" ADMIN@EXAMPLE.TEST ", "Valid-pass-1");
    when(directory.emailInUse("admin@example.test")).thenReturn(true);
    assertThrows(IllegalStateException.class, service::initialize);
    verifyNoInteractions(accounts, encoder);
  }

  @Test
  void createsAdminWithEncodedPassword() {
    configure(" ADMIN@EXAMPLE.TEST ", "Valid-pass-1");
    User admin =
        id(new User("System Administrator", "admin@example.test", Set.of(UserRole.ADMIN), NOW), 1);
    admin.markAsSystemAdmin();
    when(directory.createSystemAdmin("admin@example.test", NOW)).thenReturn(admin);
    when(encoder.encode("Valid-pass-1")).thenReturn("encoded-password");
    service.initialize();
    ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
    verify(accounts).saveAndFlush(captor.capture());
    assertSame(admin, captor.getValue().getUser());
    assertEquals("encoded-password", captor.getValue().getPasswordHash());
    assertFalse(captor.getValue().isPasswordChangeRequired());
  }

  @Test
  void runnerDelegatesInitialization() {
    var initializer = mock(SystemAdminBootstrapService.class);
    new SystemAdminBootstrap(initializer).run(mock(ApplicationArguments.class));
    verify(initializer).initialize();
  }
}

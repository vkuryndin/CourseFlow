package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.auth.api.AccountCreator;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AccountPasswordSnapshot;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AuthServiceTest {
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JwtService jwt = mock(JwtService.class);
  private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
  private final AccountCreator creator = mock(AccountCreator.class);
  private final UserAccount account = id(new UserAccount(student(), "hash", NOW), 50);

  private AuthService service() {
    when(encoder.encode(anyString())).thenReturn("dummy");
    when(tx.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer((i) -> new SimpleTransactionStatus());
    return new AuthService(accounts, sessions, encoder, jwt, CLOCK, tx, creator);
  }

  private void validCredentials() {
    when(accounts.findPasswordByEmail("student@example.test"))
        .thenReturn(Optional.of(new AccountPasswordSnapshot(50L, "hash")));
    when(encoder.matches("password", "hash")).thenReturn(true);
    when(accounts.findByIdForUpdate(50L)).thenReturn(Optional.of(account));
  }

  @Test
  void unknownEmailStillRunsPasswordCheck() {
    var service = service();

    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("missing@example.test", "password")));
    verify(encoder).matches("password", "dummy");
    verifyNoInteractions(sessions, tx);
  }

  @Test
  void wrongPasswordDoesNotCreateSession() {
    var service = service();
    when(accounts.findPasswordByEmail("student@example.test"))
        .thenReturn(Optional.of(new AccountPasswordSnapshot(50L, "hash")));

    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("student@example.test", "wrong")));
    verifyNoInteractions(sessions, tx);
  }

  @Test
  void loginChecksHashAgainAfterAcquiringLock() {
    var service = service();
    validCredentials();
    account.replacePasswordHash("changed");

    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("student@example.test", "password")));
    verifyNoInteractions(sessions);
  }

  @Test
  void inactiveUserCannotLogin() {
    var service = service();
    validCredentials();
    account.getUser().deactivate();

    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("student@example.test", "password")));
    verifyNoInteractions(sessions);
  }

  @Test
  void loginCreatesOneHourSessionAndReturnsFlag() {
    var service = service();
    validCredentials();
    account.requirePasswordChange();
    when(sessions.saveAndFlush(any(AuthSession.class))).thenAnswer((i) -> i.getArgument(0));
    when(jwt.generateAccessToken(any(AuthSession.class))).thenReturn("jwt");

    var result = service.login(new LoginRequest(" STUDENT@EXAMPLE.TEST ", "password"));

    assertEquals("jwt", result.accessToken());
    assertEquals("Bearer", result.tokenType());
    assertEquals(10L, result.userId());
    assertEquals(NOW.plusSeconds(3600), result.expiresAt());
    assertTrue(result.passwordChangeRequired());
    var captor = org.mockito.ArgumentCaptor.forClass(AuthSession.class);
    verify(sessions).saveAndFlush(captor.capture());
    assertSame(account, captor.getValue().getUserAccount());
  }

  @Test
  void longPasswordRejectedBeforeAccountLookup() {
    var service = service();

    assertThrows(
        BadRequestException.class,
        () -> service.login(new LoginRequest("student@example.test", "a".repeat(73))));
    verifyNoInteractions(accounts, sessions);
  }
}

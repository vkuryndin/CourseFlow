package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.auth.dto.ChangePasswordRequest;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AccountPasswordSnapshot;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PasswordServiceTest {
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final CredentialLifecycle lifecycle = mock(CredentialLifecycle.class);
  private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
  private final PasswordService service =
      new PasswordService(accounts, lifecycle, tokens, encoder, CLOCK, tx);
  private final UserAccount account = id(new UserAccount(student(), "hash", NOW), 50);

  private void setup() {
    when(accounts.findPasswordByUserId(10L))
        .thenReturn(Optional.of(new AccountPasswordSnapshot(50L, "hash")));
    when(encoder.matches("old-password", "hash")).thenReturn(true);
    when(encoder.encode("new-password")).thenReturn("new-hash");
    when(accounts.findByIdForUpdate(50L)).thenReturn(Optional.of(account));
    when(tx.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer((i) -> new SimpleTransactionStatus());
  }

  @Test
  void passwordChangeUpdatesHashAndRevokesCredentials() {
    setup();
    account.requirePasswordChange();
    service.changePassword(10L, new ChangePasswordRequest("old-password", "new-password"));

    assertEquals("new-hash", account.getPasswordHash());
    assertFalse(account.isPasswordChangeRequired());
    verify(tokens).invalidateUnusedTokensForAccount(50L, NOW);
    verify(lifecycle).revokeSessions(10L);
  }

  @Test
  void rejectsWrongOrSamePassword() {
    setup();

    assertThrows(
        BadRequestException.class,
        () -> service.changePassword(10L, new ChangePasswordRequest("wrong", "new-password")));
    assertThrows(
        BadRequestException.class,
        () ->
            service.changePassword(10L, new ChangePasswordRequest("old-password", "old-password")));
    verifyNoInteractions(tx, tokens, lifecycle);
  }

  @Test
  void stalePasswordHashCannotOverwriteConcurrentReset() {
    setup();
    account.replacePasswordHash("other");

    assertThrows(
        BadRequestException.class,
        () ->
            service.changePassword(10L, new ChangePasswordRequest("old-password", "new-password")));
    assertEquals("other", account.getPasswordHash());
    verifyNoInteractions(tokens, lifecycle);
  }

  @Test
  void inactiveAccountRejected() {
    setup();
    account.getUser().deactivate();

    assertThrows(
        AccessDeniedException.class,
        () ->
            service.changePassword(10L, new ChangePasswordRequest("old-password", "new-password")));
    verifyNoInteractions(tokens, lifecycle);
  }

  @Test
  void missingAccountRejected() {
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            service.changePassword(10L, new ChangePasswordRequest("old-password", "new-password")));
  }
}

package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;

class CredentialLifecycleTest {
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final CredentialLifecycle service =
      new CredentialLifecycle(sessions, tokens, accounts, CLOCK);

  @Test
  void revokesSessionsByUserId() {
    service.revokeSessions(10L);

    verify(sessions).revokeActiveSessionsByUserId(10L);
  }

  @Test
  void invalidatesTokensOnlyIfAccountExists() {
    service.invalidateResetTokens(10L);

    verifyNoInteractions(tokens);
    var a = id(new UserAccount(student(), "hash", NOW), 50);
    when(accounts.findByUser_Id(10L)).thenReturn(Optional.of(a));
    service.invalidateResetTokens(10L);
    verify(tokens).invalidateUnusedTokensForAccount(50L, NOW);
  }

  @Test
  void locksAccountAndDeletesCredentialsInOrder() {
    var a = id(new UserAccount(student(), "hash", NOW), 50);
    when(accounts.findByUser_Id(10L)).thenReturn(Optional.of(a));
    service.lockAccountIfPresent(10L);
    service.deleteCredentials(10L);
    var order = inOrder(accounts, sessions, tokens);
    order.verify(accounts).findByUserIdForUpdate(10L);
    order.verify(sessions).deleteAllByAccountId(50L);
    order.verify(tokens).deleteAllByAccountId(50L);
    order.verify(accounts).delete(a);
  }

  @Test
  void missingAccountCannotBeDeleted() {
    assertThrows(BusinessConflictException.class, () -> service.deleteCredentials(10L));
    verifyNoInteractions(sessions, tokens);
  }
}

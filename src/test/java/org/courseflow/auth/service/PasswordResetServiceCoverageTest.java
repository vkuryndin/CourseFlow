package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.auth.dto.ResetPasswordRequest;
import org.courseflow.auth.entity.PasswordResetToken;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AccountPasswordSnapshot;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.system.CourseFlowMetrics;
import org.courseflow.identity.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PasswordResetServiceCoverageTest {
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
  private final CredentialLifecycle lifecycle = mock(CredentialLifecycle.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JavaMailSender mail = mock(JavaMailSender.class);
  private final CourseFlowMetrics metrics = mock(CourseFlowMetrics.class);
  private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
  private final UserAccount account = id(new UserAccount(student(), "old-hash", NOW), 11);

  private PasswordResetService service(String... profiles) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    when(tx.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer(i -> new SimpleTransactionStatus());
    return new PasswordResetService(
        accounts,
        tokens,
        lifecycle,
        encoder,
        mail,
        "sender@example.test",
        "https://example.test/reset",
        CLOCK,
        tx,
        environment,
        metrics);
  }

  private void existingAccount() {
    when(accounts.findAccountIdByUserEmail("student@example.test")).thenReturn(Optional.of(11L));
    when(accounts.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
  }

  private void snapshot() {
    when(tokens.findValidPasswordSnapshot(anyString(), eq(NOW), eq(UserStatus.ACTIVE)))
        .thenReturn(Optional.of(new AccountPasswordSnapshot(11L, "old-hash")));
    when(accounts.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
    when(encoder.encode("new-password")).thenReturn("new-hash");
  }

  @Test
  void smtpFailureDoesNotExposeAccountExistence() {
    existingAccount();
    doThrow(new MailSendException("offline")).when(mail).send(any(SimpleMailMessage.class));
    service().requestReset("student@example.test");
    verify(metrics).resetMailFailed();
    verify(metrics, never()).resetMailSent();
  }

  @Test
  void deactivatedAccountDoesNotReceiveToken() {
    existingAccount();
    account.getUser().deactivate();
    service().requestReset("student@example.test");
    verifyNoInteractions(tokens, mail);
  }

  @Test
  void changedEmailDoesNotReceiveToken() {
    existingAccount();
    account.getUser().updateDetails("Student", "changed@example.test");
    service().requestReset("student@example.test");
    verifyNoInteractions(tokens, mail);
  }

  @Test
  void developmentMailIncludesTestToken() {
    existingAccount();
    service("dev").requestReset("student@example.test");
    var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail).send(message.capture());
    assertTrue(message.getValue().getText().contains("For testing through requests.http"));
  }

  @Test
  void productionSuppressesDebugTokenEvenWithDevProfile() {
    existingAccount();
    service("dev", "prod").requestReset("student@example.test");
    var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail).send(message.capture());
    assertFalse(message.getValue().getText().contains("For testing through requests.http"));
  }

  @Test
  void samePasswordRejectedBeforeLock() {
    snapshot();
    when(encoder.matches("new-password", "old-hash")).thenReturn(true);
    assertThrows(
        BadRequestException.class,
        () -> service().resetPassword(new ResetPasswordRequest("token", "new-password")));
    verifyNoInteractions(lifecycle);
    verify(accounts, never()).findByIdForUpdate(11L);
  }

  @Test
  void expiredTokenRejectedAfterLock() {
    snapshot();
    var token = new PasswordResetToken(account, "hash", NOW);
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
    assertThrows(
        BadRequestException.class,
        () -> service().resetPassword(new ResetPasswordRequest("token", "new-password")));
    assertEquals("old-hash", account.getPasswordHash());
    verifyNoInteractions(lifecycle);
  }

  @Test
  void changedPasswordHashRejectsStaleReset() {
    snapshot();
    account.replacePasswordHash("changed-hash");
    var token = new PasswordResetToken(account, "hash", NOW.plusSeconds(60));
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
    assertThrows(
        BadRequestException.class,
        () -> service().resetPassword(new ResetPasswordRequest("token", "new-password")));
    assertEquals("changed-hash", account.getPasswordHash());
    assertNull(token.getUsedAt());
    verifyNoInteractions(lifecycle);
  }
}

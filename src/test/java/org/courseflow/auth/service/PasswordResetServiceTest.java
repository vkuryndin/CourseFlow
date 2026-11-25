package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.auth.api.CredentialLifecycle;
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
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PasswordResetServiceTest {
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
  private final CredentialLifecycle lifecycle = mock(CredentialLifecycle.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JavaMailSender mail = mock(JavaMailSender.class);
  private final CourseFlowMetrics metrics = mock(CourseFlowMetrics.class);
  private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
  private final PasswordResetService service =
      new PasswordResetService(
          accounts,
          tokens,
          lifecycle,
          encoder,
          mail,
          "sender@example.test",
          "https://example.test/reset",
          CLOCK,
          tx,
          mock(Environment.class),
          metrics);
  private final UserAccount account = id(new UserAccount(student(), "old-hash", NOW), 11);

  private void transaction() {
    when(tx.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer((i) -> new SimpleTransactionStatus());
  }

  private void existingAccount() {
    transaction();
    when(accounts.findAccountIdByUserEmail("student@example.test")).thenReturn(Optional.of(11L));
    when(accounts.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
  }

  @Test
  void unknownEmailDoesNotSendMail() {
    transaction();
    service.requestReset(" UNKNOWN@EXAMPLE.TEST ");

    verify(accounts).findAccountIdByUserEmail("unknown@example.test");
    verifyNoInteractions(tokens, mail, encoder);
  }

  @Test
  void cooldownPreventsSendingAnotherToken() {
    existingAccount();
    when(tokens.existsByUserAccount_IdAndUsedAtIsNullAndExpiresAtAfter(11L, NOW.plusSeconds(1680)))
        .thenReturn(true);
    service.requestReset("student@example.test");

    verify(tokens, never()).saveAndFlush(any());
    verifyNoInteractions(mail);
  }

  @Test
  void storesHashedTokenAndSendsResetLink() {
    existingAccount();
    service.requestReset("student@example.test");
    var token = ArgumentCaptor.forClass(PasswordResetToken.class);

    verify(tokens).saveAndFlush(token.capture());
    assertEquals(64, token.getValue().getTokenHash().length());
    assertEquals(NOW.plusSeconds(1800), token.getValue().getExpiresAt());
    var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail).send(message.capture());
    assertEquals("student@example.test", message.getValue().getTo()[0]);
    assertTrue(message.getValue().getText().contains("https://example.test/reset?token="));
    assertFalse(message.getValue().getText().contains(token.getValue().getTokenHash()));
    verify(metrics).resetMailSent();
  }

  @Test
  void invalidTokenDoesNotHashNewPassword() {
    assertThrows(
        BadRequestException.class,
        () -> service.resetPassword(new ResetPasswordRequest("invalid", "new-password")));
    verifyNoInteractions(encoder, lifecycle, tx);
  }

  @Test
  void resetChangesPasswordAndRevokesSessions() {
    transaction();
    var token = new PasswordResetToken(account, "hash", NOW.plusSeconds(60));
    when(tokens.findValidPasswordSnapshot(anyString(), eq(NOW), eq(UserStatus.ACTIVE)))
        .thenReturn(Optional.of(new AccountPasswordSnapshot(11L, "old-hash")));
    when(accounts.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
    when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
    when(encoder.encode("new-password")).thenReturn("new-hash");
    service.resetPassword(new ResetPasswordRequest("token", "new-password"));

    assertEquals("new-hash", account.getPasswordHash());
    assertEquals(NOW, token.getUsedAt());
    verify(tokens).invalidateUnusedTokensForAccount(11L, NOW);
    verify(lifecycle).revokeSessions(10L);
  }
}

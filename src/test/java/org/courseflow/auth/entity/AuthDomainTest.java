package org.courseflow.auth.entity;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthDomainTest {

  @Test
  void passwordChangeFlagClearedOnReplacement() {
    var user = student();
    UserAccount account = new UserAccount(user, "hash", NOW);

    assertSame(user, account.getUser());
    assertEquals(NOW, account.getCreatedAt());
    assertFalse(account.isPasswordChangeRequired());
    account.requirePasswordChange();
    assertTrue(account.isPasswordChangeRequired());
    account.replacePasswordHash("new-hash");
    assertEquals("new-hash", account.getPasswordHash());
    assertFalse(account.isPasswordChangeRequired());
  }

  @Test
  void invalidHashDoesNotClearFlag() {
    UserAccount account = new UserAccount(student(), "hash", NOW);
    account.requirePasswordChange();

    assertThrows(IllegalArgumentException.class, () -> account.replacePasswordHash(null));
    assertThrows(IllegalArgumentException.class, () -> account.replacePasswordHash("  "));
    assertEquals("hash", account.getPasswordHash());
    assertTrue(account.isPasswordChangeRequired());
  }

  @Test
  void sessionExpiryAndRevocation() {
    UserAccount account = new UserAccount(student(), "hash", NOW);
    Instant end = NOW.plusSeconds(60);
    AuthSession session = new AuthSession(account, end, NOW);

    assertSame(account, session.getUserAccount());
    assertEquals(NOW, session.getCreatedAt());
    assertEquals(end, session.getExpiresAt());
    assertTrue(session.isActive(end.minusNanos(1)));
    assertFalse(session.isActive(end));
    assertFalse(session.isActive(end.plusSeconds(1)));
    session.revoke();
    assertTrue(session.isRevoked());
    assertFalse(session.isActive(NOW));
  }

  @Test
  void resetTokenIsSingleUseAndExpiresAtBoundary() {
    UserAccount account = new UserAccount(student(), "hash", NOW);
    Instant end = NOW.plusSeconds(60);
    PasswordResetToken token = new PasswordResetToken(account, "token-hash", end);

    assertSame(account, token.getUserAccount());
    assertEquals("token-hash", token.getTokenHash());
    assertEquals(end, token.getExpiresAt());
    assertTrue(token.isValid(end.minusNanos(1)));
    assertFalse(token.isValid(end));
    token.markUsed(NOW);
    assertEquals(NOW, token.getUsedAt());
    assertFalse(token.isValid(NOW));
  }
}

package org.courseflow.integration.auth;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.courseflow.integration.support.JsonChecks.str;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.courseflow.auth.service.ExpiredCredentialsCleanup;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Positive and expired credential paths, with mail intercepted at the SMTP boundary. */
class AuthLifecycleIT extends HttpIntegrationTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ExpiredCredentialsCleanup cleanup;
  @MockitoBean private JavaMailSender mailSender;

  @Test
  void adminRecoverySendsOneLinkAndEnforcesCooldown() throws Exception {
    Actor actor = actor();
    String path = "/api/users/" + actor.id() + "/password-reset";
    expect(api("POST", path, actor.jwt(), null), 403);
    expect(api("POST", path, actor.admin(), null), 202);
    expect(api("POST", path, actor.admin(), null), 202);
    var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender, times(1)).send(captor.capture());
    String token = token(captor.getValue(), actor.email());
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM password_reset_tokens t JOIN user_accounts a ON t.user_account_id = a.id
                WHERE a.user_id = ? AND t.used_at IS NULL
                """,
            actor.id()));
    assertEquals(
        0L, count("SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?", token));
    expect(
        api(
            "POST",
            "/api/auth/reset-password",
            null,
            Map.of("token", token, "newPassword", NEW_PASSWORD)),
        204);
    expect(api("GET", "/api/users/me", actor.jwt(), null), 401);
    assertFalse(login(actor.email(), NEW_PASSWORD).isBlank());
  }

  @Test
  void expiredRecoveryTokenIsRejectedWithoutChangingPassword() throws Exception {
    Actor actor = actor();
    expect(api("POST", "/api/users/" + actor.id() + "/password-reset", actor.admin(), null), 202);
    var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    String token = token(captor.getValue(), actor.email());
    assertEquals(
        1,
        jdbc.update(
            """
                UPDATE password_reset_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE user_account_id = (SELECT id FROM user_accounts WHERE user_id = ?)
                """,
            actor.id()));
    expect(
        api(
            "POST",
            "/api/auth/reset-password",
            null,
            Map.of("token", token, "newPassword", NEW_PASSWORD)),
        400);
    expect(api("GET", "/api/users/me", actor.jwt(), null), 200);
    assertFalse(login(actor.email(), PASSWORD).isBlank());
  }

  @Test
  void expiredSessionIsRejectedAndCleanupPreservesNewSession() throws Exception {
    Actor actor = actor();
    assertEquals(
        1,
        jdbc.update(
            """
                UPDATE auth_sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '2 days'
                WHERE user_account_id = (SELECT id FROM user_accounts WHERE user_id = ?)
                """,
            actor.id()));
    expect(api("GET", "/api/users/me", actor.jwt(), null), 401);
    String fresh = login(actor.email(), PASSWORD);
    cleanup.cleanupSessions();
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM auth_sessions s JOIN user_accounts a ON s.user_account_id = a.id
                WHERE a.user_id = ?
                """,
            actor.id()));
    expect(api("GET", "/api/users/me", fresh, null), 200);
  }

  @Test
  void credentialCleanupRemovesOldResetTokensButKeepsFreshOnes() throws Exception {
    Actor old = actor();
    expect(api("POST", "/api/users/" + old.id() + "/password-reset", old.admin(), null), 202);
    assertEquals(
        1,
        jdbc.update(
            """
                UPDATE password_reset_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '2 days'
                WHERE user_account_id = (SELECT id FROM user_accounts WHERE user_id = ?)
                """,
            old.id()));
    Actor fresh = actor();
    expect(api("POST", "/api/users/" + fresh.id() + "/password-reset", fresh.admin(), null), 202);
    cleanup.cleanupResetTokens();
    assertEquals(0L, resetTokenCount(old.id()));
    assertEquals(1L, resetTokenCount(fresh.id()));
  }

  private Actor actor() throws Exception {
    String admin = login(adminEmail, adminPassword);
    String email = "auth." + UUID.randomUUID() + "@courseflow.local";
    Object body =
        expect(
            api(
                "POST",
                "/api/users",
                admin,
                Map.of(
                    "displayName",
                    "Auth lifecycle",
                    "email",
                    email,
                    "password",
                    PASSWORD,
                    "passwordChangeRequired",
                    false,
                    "roles",
                    List.of("STUDENT"))),
            201);
    long id = ((Number) field(body, "id")).longValue();
    return new Actor(id, email, login(email, PASSWORD), admin);
  }

  private String login(String email, String password) throws Exception {
    return str(
        field(
            expect(
                api("POST", "/api/auth/login", null, Map.of("email", email, "password", password)),
                200),
            "accessToken"));
  }

  private static String token(SimpleMailMessage message, String email) {
    assertArrayEquals(new String[] {email}, message.getTo());
    var matcher = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)").matcher(message.getText());
    assertTrue(matcher.find(), "Missing recovery token in test mail");
    return matcher.group(1);
  }

  private long resetTokenCount(long user) {
    return count(
        """
                SELECT count(*) FROM password_reset_tokens t JOIN user_accounts a ON t.user_account_id = a.id
                WHERE a.user_id = ?
                """,
        user);
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private record Actor(long id, String email, String jwt, String admin) {}
}

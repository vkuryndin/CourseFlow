package org.courseflow.security;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.courseflow.auth.api.SessionAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtSessionValidatorTest {
  private final SessionAccess sessions = mock(SessionAccess.class);
  private final JwtSessionValidator validator = new JwtSessionValidator(sessions, CLOCK);
  private final UUID session = UUID.fromString("12345678-1234-1234-1234-123456789abc");

  private Jwt token(String subject, String jti, List<String> roles, boolean required) {
    var builder =
        Jwt.withTokenValue("test")
            .header("alg", "HS256")
            .subject(subject)
            .claim("passwordChangeRequired", required);
    if (jti != null) {
      builder.claim("jti", jti);
    }
    if (roles != null) {
      builder.claim("roles", roles);
    }
    return builder.build();
  }

  @Test
  void validatesCurrentRolesIndependentOfTokenOrder() {
    when(sessions.activeRoles(session, 10L, NOW, false)).thenReturn(List.of("STUDENT", "TEACHER"));

    assertFalse(
        validator
            .validate(token("10", session.toString(), List.of("TEACHER", "STUDENT"), false))
            .hasErrors());
  }

  @Test
  void rejectsMissingOrMalformedSessionId() {
    assertTrue(validator.validate(token("10", null, List.of("STUDENT"), false)).hasErrors());
    assertTrue(validator.validate(token("10", "bad", List.of("STUDENT"), false)).hasErrors());
    verifyNoInteractions(sessions);
  }

  @Test
  void rejectsInvalidSubject() {
    for (String sub : List.of("bad", "0", "-1", "010")) {
      assertTrue(
          validator
              .validate(token(sub, session.toString(), List.of("STUDENT"), false))
              .hasErrors());
    }
    verifyNoInteractions(sessions);
  }

  @Test
  void rejectsRevokedSessionAndChangedRoles() {
    assertTrue(
        validator.validate(token("10", session.toString(), List.of("STUDENT"), false)).hasErrors());
    when(sessions.activeRoles(session, 10L, NOW, false)).thenReturn(List.of("TEACHER"));
    assertTrue(
        validator.validate(token("10", session.toString(), List.of("STUDENT"), false)).hasErrors());
    assertTrue(validator.validate(token("10", session.toString(), null, false)).hasErrors());
  }

  @Test
  void passesPasswordChangeFlagToSessionLookup() {
    when(sessions.activeRoles(session, 10L, NOW, true)).thenReturn(List.of("STUDENT"));

    assertFalse(
        validator.validate(token("10", session.toString(), List.of("STUDENT"), true)).hasErrors());
    verify(sessions).activeRoles(session, 10L, NOW, true);
  }

  @Test
  void currentUserExtractsIdAndRoles() {
    var jwt = token("10", session.toString(), List.of("STUDENT"), false);

    assertEquals(10L, CurrentUser.id(jwt));
    var auth =
        new UsernamePasswordAuthenticationToken(
            "user", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    assertTrue(CurrentUser.isAdmin(auth));
    assertFalse(CurrentUser.hasRole(auth, "STUDENT"));
  }
}

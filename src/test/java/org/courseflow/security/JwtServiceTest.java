package org.courseflow.security;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {
  @Test
  void generatesTokenWithSortedRolesSessionIdAndPasswordChangeFlag() {
    var user =
        id(
            new User(
                "Alice", "alice@example.test", Set.of(UserRole.TEACHER, UserRole.STUDENT), NOW),
            10);
    var account = new UserAccount(user, "hash", NOW);
    account.requirePasswordChange();
    var session = new AuthSession(account, NOW.plusSeconds(3600), NOW);
    var sessionId = UUID.randomUUID();
    ReflectionTestUtils.setField(session, "id", sessionId);
    JwtEncoder encoder = mock(JwtEncoder.class);
    Jwt jwt = Jwt.withTokenValue("encoded-token").header("alg", "HS256").subject("10").build();
    when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

    assertEquals("encoded-token", new JwtService(encoder).generateAccessToken(session));

    var captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
    verify(encoder).encode(captor.capture());
    var claims = captor.getValue().getClaims();
    assertEquals("10", claims.getSubject());
    assertEquals(sessionId.toString(), claims.getId());
    assertEquals(NOW, claims.getIssuedAt());
    assertEquals(NOW.plusSeconds(3600), claims.getExpiresAt());
    assertEquals(List.of("STUDENT", "TEACHER"), claims.getClaimAsStringList("roles"));
    assertEquals(Boolean.TRUE, claims.getClaimAsBoolean("passwordChangeRequired"));
    assertEquals("HS256", captor.getValue().getJwsHeader().getAlgorithm().getName());
  }
}

package org.courseflow.security;

import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.test.util.ReflectionTestUtils;

class JwtConfigTest {
  private final JwtConfig config = new JwtConfig();

  @Test
  void rejectsShortOrInvalidSigningKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> config.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[31])));
    assertThrows(IllegalArgumentException.class, () -> config.jwtSigningKey("***"));
  }

  @Test
  void signsAndDecodesJwtUsingConfiguredKey() {
    var key = config.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
    assertEquals("HmacSHA256", key.getAlgorithm());
    var validator = mock(JwtSessionValidator.class);
    when(validator.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
    var now = Instant.now();
    var session =
        new AuthSession(new UserAccount(student(), "hash", now), now.plusSeconds(3600), now);
    ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
    var token = new JwtService(config.jwtEncoder(key)).generateAccessToken(session);
    var decoded = config.jwtDecoder(key, validator, Clock.systemUTC()).decode(token);
    assertEquals("10", decoded.getSubject());
    assertEquals(session.getId().toString(), decoded.getId());
    assertEquals("courseflow", decoded.getClaimAsString("iss"));
    assertEquals(now.getEpochSecond(), decoded.getIssuedAt().getEpochSecond());
    assertEquals(now.plusSeconds(3600).getEpochSecond(), decoded.getExpiresAt().getEpochSecond());
    assertEquals(java.util.List.of("STUDENT"), decoded.getClaimAsStringList("roles"));
    assertEquals(Boolean.FALSE, decoded.getClaimAsBoolean("passwordChangeRequired"));
    byte[] otherBytes = new byte[32];
    otherBytes[0] = 1;
    var otherKey = config.jwtSigningKey(Base64.getEncoder().encodeToString(otherBytes));
    var otherDecoder = config.jwtDecoder(otherKey, validator, Clock.systemUTC());
    assertThrows(org.springframework.security.oauth2.jwt.JwtException.class, () -> otherDecoder.decode(token));
  }
}

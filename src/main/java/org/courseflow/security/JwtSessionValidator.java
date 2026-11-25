package org.courseflow.security;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.courseflow.auth.api.SessionAccess;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtSessionValidator implements OAuth2TokenValidator<Jwt> {

  private final Clock clock;

  private final SessionAccess sessionAccess;

  public JwtSessionValidator(SessionAccess sessionAccess, Clock clock) {
    this.clock = clock;
    this.sessionAccess = sessionAccess;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    String sessionId = jwt.getId();

    if (sessionId == null || sessionId.isBlank()) {
      return invalid();
    }

    UUID id;

    try {
      id = UUID.fromString(sessionId);
    } catch (IllegalArgumentException ex) {
      return invalid();
    }

    Long userId;
    try {
      userId = Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException ex) {
      return invalid();
    }
    if (userId <= 0 || !userId.toString().equals(jwt.getSubject())) {
      return invalid();
    }

    List<String> currentRoles =
        sessionAccess.activeRoles(
            id,
            userId,
            clock.instant(),
            Boolean.TRUE.equals(jwt.getClaimAsBoolean("passwordChangeRequired")));
    if (currentRoles.isEmpty()) {
      return invalid();
    }

    List<String> tokenRoles = jwt.getClaimAsStringList("roles");

    if (tokenRoles == null || !currentRoles.equals(tokenRoles.stream().sorted().toList())) {
      return invalid();
    }

    return OAuth2TokenValidatorResult.success();
  }

  private OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Invalid or revoked authentication session", null));
  }
}

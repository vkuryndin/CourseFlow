
package org.courseflow.security;

import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.identity.entity.UserStatus;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Component
public class JwtSessionValidator
        implements OAuth2TokenValidator<Jwt> {

    private final AuthSessionRepository sessionRepository;

    public JwtSessionValidator(
            AuthSessionRepository sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
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

        List<String> currentRoles = sessionRepository
                .findValidSessionRoles(id, userId, UserStatus.ACTIVE, Instant.now())
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();
        if (currentRoles.isEmpty()) {
            return invalid();
        }

        List<String> tokenRoles =
                jwt.getClaimAsStringList("roles");

        if (tokenRoles == null
                || !currentRoles.equals(
                tokenRoles.stream().sorted().toList()
        )) {
            return invalid();
        }


        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        "invalid_token",
                        "Invalid or revoked authentication session",
                        null
                )
        );
    }
}


package org.courseflow.auth.service;

import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.repository.AuthSessionRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LogoutService {

    private final AuthSessionRepository authSessionRepository;

    public LogoutService(
            AuthSessionRepository authSessionRepository
    ) {
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional
    public void logout(Jwt jwt) {
        UUID sessionId = UUID.fromString(jwt.getId());

        AuthSession session = authSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new BadCredentialsException(
                        "Authentication session not found"
                ));

        session.revoke();
    }
}

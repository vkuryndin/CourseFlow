package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.common.exception.InvalidSessionException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class LogoutServiceTest {
    private final AuthSessionRepository repo = mock(AuthSessionRepository.class);
    private final LogoutService service = new LogoutService(repo);
    private final UUID session = UUID.randomUUID();

    @Test
    void revokesExistingSession() {
        var a = new AuthSession(new UserAccount(student(), "hash", NOW), NOW.plusSeconds(60), NOW);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn(session.toString());
        when(repo.findById(session)).thenReturn(Optional.of(a));
        service.logout(jwt);

        assertTrue(a.isRevoked());
    }

    @Test
    void rejectsMissingSession() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn(session.toString());

        assertThrows(InvalidSessionException.class, () -> service.logout(jwt));
    }
}

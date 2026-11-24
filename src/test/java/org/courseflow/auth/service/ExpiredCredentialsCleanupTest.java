package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;

class ExpiredCredentialsCleanupTest {

    @Test
    void cleanupUsesOneDayRetention() {
        var sessions = mock(AuthSessionRepository.class);
        var tokens = mock(PasswordResetTokenRepository.class);
        var service = new ExpiredCredentialsCleanup(sessions, tokens, CLOCK);
        when(sessions.deleteExpiredBefore(NOW.minus(Duration.ofDays(1)))).thenReturn(2);
        when(tokens.deleteExpiredBefore(NOW.minus(Duration.ofDays(1)))).thenReturn(3);
        service.cleanupSessions();
        service.cleanupResetTokens();

        verify(sessions).deleteExpiredBefore(NOW.minus(Duration.ofDays(1)));
        verify(tokens).deleteExpiredBefore(NOW.minus(Duration.ofDays(1)));
    }
}

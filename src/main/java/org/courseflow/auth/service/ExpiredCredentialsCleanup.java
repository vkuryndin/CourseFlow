package org.courseflow.auth.service;

import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Retain recently expired records for diagnostics; never remove live credentials. */
@Component
public class ExpiredCredentialsCleanup {
    private static final Logger log = LoggerFactory.getLogger(ExpiredCredentialsCleanup.class);
    private static final Duration RETENTION = Duration.ofDays(1);
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository tokens;

    public ExpiredCredentialsCleanup(AuthSessionRepository sessions,
                                     PasswordResetTokenRepository tokens) {
        this.sessions = sessions;
        this.tokens = tokens;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    public void cleanupSessions() {
        int removed = sessions.deleteExpiredBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Expired authentication sessions removed: {}", removed);
        }
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 3_600_000)
    @Transactional
    public void cleanupResetTokens() {
        int removed = tokens.deleteExpiredBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Expired password reset tokens removed: {}", removed);
        }
    }
}

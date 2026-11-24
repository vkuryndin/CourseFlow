package org.courseflow.assessment.service;

import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persist expired attempts independently of GET requests, in bounded batches. */
@Component
public class ExpiredQuizAttemptsCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ExpiredQuizAttemptsCleanup(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    @Transactional
    public void expireAttempts() {
        jdbcTemplate.update("""
                WITH expired AS (
                    SELECT id FROM quiz_submissions
                    WHERE status = 'IN_PROGRESS' AND expires_at <= ?
                    ORDER BY expires_at, id
                    LIMIT 250
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE quiz_submissions s
                SET status = 'EXPIRED', finished_at = s.expires_at
                FROM expired e
                WHERE s.id = e.id AND s.status = 'IN_PROGRESS'
                """, Timestamp.from(clock.instant()));
    }
}

package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ExpiredQuizAttemptsCleanupTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  @Test
  void expiresOnlyBoundedBatchAtCurrentTime() {
    new ExpiredQuizAttemptsCleanup(jdbc, CLOCK).expireAttempts();
    verify(jdbc)
        .update(
            org.mockito.ArgumentMatchers.<String>argThat(
                sql ->
                    sql.contains("expires_at <= ?")
                        && sql.contains("LIMIT 250")
                        && sql.contains("SKIP LOCKED")),
            eq(Timestamp.from(NOW)));
  }
}

package org.courseflow.integration.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.courseflow.assessment.service.ExpiredQuizAttemptsCleanup;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.courseflow.integration.support.ConcurrentCalls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;

/** Calls the actual scheduled service, without waiting a minute for scheduler ticks. */
class ConcurrentCleanupIT extends AdvancedHttpTest {
  @Autowired private DataSource dataSource;
  @Autowired private ExpiredQuizAttemptsCleanup cleanup;
  @Autowired private ScheduledAnnotationBeanPostProcessor scheduler;

  @BeforeAll
  void stopAutomaticTicksInThisTestContext() {
    // Run real cleanup methods explicitly; avoid uncontrolled scheduler races.
    scheduler.getScheduledTasks().forEach(ScheduledTask::cancel);
  }

  @Test
  void cleanupSkipsLockedExpiredAttemptAndProcessesUnlockedAttempt() throws Exception {
    Graph g = graph();
    Actor other = actor(java.util.List.of("STUDENT"));
    enroll(g.course(), student);
    enroll(g.course(), other);
    QuizGraph q = quiz(g.module());
    long locked = start(q, student);
    long free = start(q, other);
    // Commit the free expired row. Make the locked row expired in a separate committed
    // statement, then acquire the row lock before the explicit cleanup invocation.
    expire(locked);
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (var statement =
            connection.prepareStatement(
                "SELECT id FROM quiz_submissions WHERE id = ? FOR UPDATE")) {
          statement.setLong(1, locked);
          try (var result = statement.executeQuery()) {
            assertTrue(result.next());
          }
        }
        assertEquals(
            "IN_PROGRESS", status(locked), "Attempt must be pending when testing SKIP LOCKED");
        expire(free);
        var executor = Executors.newSingleThreadExecutor();
        try {
          executor.submit(cleanup::expireAttempts).get(10, TimeUnit.SECONDS);
          assertEquals("IN_PROGRESS", status(locked));
          assertEquals("EXPIRED", status(free));
        } finally {
          connection.rollback();
          executor.shutdownNow();
          executor.awaitTermination(10, TimeUnit.SECONDS);
        }
      } finally {
        connection.rollback();
      }
    }
    cleanup.expireAttempts();
    assertEquals("EXPIRED", status(locked));
  }

  @Test
  void twoCleanupInvocationsDoNotCorruptAttemptState() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long attempt = start(q, student);
    expire(attempt);
    ConcurrentCalls.pair(
        () -> {
          cleanup.expireAttempts();
          return true;
        },
        () -> {
          cleanup.expireAttempts();
          return true;
        });
    assertExpired(attempt);
  }

  @Test
  void cleanupRacingWithExpiredSubmissionCannotPersistAnswers() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long attempt = start(q, student);
    expire(attempt);
    var results =
        ConcurrentCalls.pair(
            () ->
                api(
                        "POST",
                        attemptPath(q) + "/" + attempt + "/submit",
                        student.token(),
                        answers(q, true))
                    .status(),
            () -> {
              cleanup.expireAttempts();
              return 0;
            });
    assertEquals(409, results.get(0));
    assertExpired(attempt);
    assertEquals(
        0L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
  }

  @Test
  void cleanupRacingWithValidSubmissionDoesNotExpireIt() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long attempt = start(q, student);
    var results =
        ConcurrentCalls.pair(
            () ->
                api(
                        "POST",
                        attemptPath(q) + "/" + attempt + "/submit",
                        student.token(),
                        answers(q, true))
                    .status(),
            () -> {
              cleanup.expireAttempts();
              return 0;
            });
    assertEquals(200, results.get(0));
    assertEquals("SUBMITTED", status(attempt));
    assertEquals(
        100,
        jdbc.queryForObject(
            "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
  }

  @Test
  void cleanupLeavesAlreadySubmittedAttemptAndItsAnswersIntact() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long attempt = start(q, student);
    expect(
        api("POST", attemptPath(q) + "/" + attempt + "/submit", student.token(), answers(q, true)),
        200);
    var expiry = clock.instant().minusSeconds(1);
    jdbc.update(
        "UPDATE quiz_submissions SET taken_at = ?, expires_at = ?, finished_at = ? WHERE id = ?",
        Timestamp.from(expiry.minus(20, ChronoUnit.MINUTES)),
        Timestamp.from(expiry),
        Timestamp.from(expiry.minusSeconds(1)),
        attempt);
    cleanup.expireAttempts();
    assertEquals("SUBMITTED", status(attempt));
    assertEquals(
        1L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
  }

  @Test
  void cleanupProcessesAtMost250ExpiredAttemptsPerBatch() throws Exception {
    Graph g = graph();
    QuizGraph q = quiz(g.module());
    // Database fixture: 251 different synthetic students, no credentials required.
    // The job operates on attempts and does not authenticate students.
    String prefix = "batch-" + unique();
    var expiry = clock.instant().minusSeconds(1);
    assertEquals(
        251,
        jdbc.update(
            """
                INSERT INTO users (display_name, email, status, system_admin, created_at, version)
                SELECT 'Batch student', ? || '-' || n || '@courseflow.local', 'ACTIVE', false, ?, 0
                FROM generate_series(1, 251) AS n
                """,
            prefix,
            Timestamp.from(expiry.minusSeconds(3600))));
    assertEquals(
        251,
        jdbc.update(
            """
                INSERT INTO quiz_submissions (quiz_id, student_id, attempt_number, taken_at, expires_at, status)
                SELECT ?, id, 1, ?, ?, 'IN_PROGRESS' FROM users WHERE email LIKE ?
                """,
            q.quiz(),
            Timestamp.from(expiry.minus(20, ChronoUnit.MINUTES)),
            Timestamp.from(expiry),
            prefix + "%"));
    cleanup.expireAttempts();
    assertEquals(
        250L,
        count(
            "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'EXPIRED'",
            q.quiz()));
    assertEquals(
        1L,
        count(
            "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'IN_PROGRESS'",
            q.quiz()));
    cleanup.expireAttempts();
    assertEquals(
        251L,
        count(
            "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'EXPIRED' AND finished_at = expires_at",
            q.quiz()));
  }

  private void expire(long id) {
    var expiry = clock.instant().minusSeconds(1);
    assertEquals(
        1,
        jdbc.update(
            "UPDATE quiz_submissions SET taken_at = ?, expires_at = ? WHERE id = ?",
            Timestamp.from(expiry.minus(20, ChronoUnit.MINUTES)),
            Timestamp.from(expiry),
            id));
  }

  private String status(long id) {
    return jdbc.queryForObject(
        "SELECT status FROM quiz_submissions WHERE id = ?", String.class, id);
  }

  private void assertExpired(long id) {
    assertEquals("EXPIRED", status(id));
    assertEquals(
        1L,
        count(
            "SELECT count(*) FROM quiz_submissions WHERE id = ? AND finished_at = expires_at AND score IS NULL",
            id));
  }
}

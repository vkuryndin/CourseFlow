package org.courseflow.integration.boundary;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** The application clock is fixed, so exact deadline comparisons never depend on sleeps. */
@Import(TemporalBoundaryIT.FixedClockConfiguration.class)
class TemporalBoundaryIT extends AdvancedHttpTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock boundaryClock() {
            return Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
        }
    }

    @Test
    void assignmentSubmissionOneMicrosecondBeforeDeadlineSucceeds() throws Exception {
        assignmentBoundary(1, 201);
    }

    @Test
    void assignmentSubmissionExactlyAtDeadlineIsRejected() throws Exception {
        assignmentBoundary(0, 409);
    }

    @Test
    void assignmentSubmissionOneMicrosecondAfterDeadlineIsRejected() throws Exception {
        assignmentBoundary(-1, 409);
    }

    @Test
    void quizSubmissionOneMicrosecondBeforeExpirySucceeds() throws Exception {
        quizBoundary(1, 200);
    }

    @Test
    void quizSubmissionExactlyAtExpiryIsRejected() throws Exception {
        quizBoundary(0, 409);
    }

    @Test
    void quizSubmissionOneMicrosecondAfterExpiryIsRejected() throws Exception {
        quizBoundary(-1, 409);
    }

    @Test
    void scheduleWithEqualStartAndEndIsRejected() throws Exception {
        Graph g = graph();
        Instant future = clock.instant().plus(2, ChronoUnit.DAYS);
        session(g.lesson(), future, future, 400);
    }

    @Test
    void sessionStartingAtCurrentTimeIsRejected() throws Exception {
        Graph g = graph();
        session(g.lesson(), clock.instant(), clock.instant().plusSeconds(60), 400);
    }

    @Test
    void adjacentSessionsForSameTeacherDoNotOverlap() throws Exception {
        Graph g = graph();
        Instant start = clock.instant().plus(4, ChronoUnit.DAYS);
        session(g.lesson(), start, start.plusSeconds(3600), 201);
        session(g.lesson(), start.plusSeconds(3600), start.plusSeconds(7200), 201);
        assertEquals(2L, count("SELECT count(*) FROM lesson_sessions WHERE lesson_id = ?", g.lesson()));
    }

    @Test
    void sessionOverlappingByOneMicrosecondIsRejected() throws Exception {
        Graph g = graph();
        Instant start = clock.instant().plus(5, ChronoUnit.DAYS);
        session(g.lesson(), start, start.plusSeconds(3600), 201);
        session(g.lesson(), start.plusSeconds(3600).minus(1, ChronoUnit.MICROS), start.plusSeconds(7200), 409);
        assertEquals(1L, count("SELECT count(*) FROM lesson_sessions WHERE lesson_id = ?", g.lesson()));
    }

    @Test
    void extendingAssignmentToExactlyItsExistingDeadlineIsRejected() throws Exception {
        Graph g = graph();
        long assignment = assignment(g.lesson());
        String path = "/api/lessons/" + g.lesson() + "/assignments/" + assignment;
        Object current = expect(api("GET", path, teacher.token(), null), 200);
        expect(api("PATCH", path + "/deadline", teacher.token(), Map.of("dueAt", field(current, "dueAt"),
                "version", number(current, "version"))), 400);
        assertEquals(number(current, "version"), jdbc.queryForObject("SELECT version FROM assignments WHERE id = ?", Long.class, assignment));
    }

    @Test
    void extendingAssignmentByOneMicrosecondSucceeds() throws Exception {
        Graph g = graph();
        long assignment = assignment(g.lesson());
        String path = "/api/lessons/" + g.lesson() + "/assignments/" + assignment;
        Object current = expect(api("GET", path, teacher.token(), null), 200);
        Instant due = Instant.parse(field(current, "dueAt").toString()).plus(1, ChronoUnit.MICROS);
        expect(api("PATCH", path + "/deadline", teacher.token(), Map.of("dueAt", due.toString(), "version", number(current, "version"))), 200);
        assertEquals(due, jdbc.queryForObject("SELECT due_at FROM assignments WHERE id = ?", Timestamp.class, assignment).toInstant());
    }

    private void assignmentBoundary(long offsetMicros, int expected) throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        long assignment = assignment(g.lesson());
        // Set a committed fixture rather than racing the wall clock.
        Instant due = clock.instant().plus(offsetMicros, ChronoUnit.MICROS);
        assertEquals(1, jdbc.update("UPDATE assignments SET due_at = ? WHERE id = ?", Timestamp.from(due), assignment));
        expect(api("POST", "/api/assignments/" + assignment + "/submissions", student.token(), Map.of("content", "Answer")), expected);
        assertEquals(expected == 201 ? 1L : 0L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment));
    }

    private void quizBoundary(long offsetMicros, int expected) throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        QuizGraph q = quiz(g.module());
        long attempt = start(q, student);
        Instant expiry = clock.instant().plus(offsetMicros, ChronoUnit.MICROS);
        jdbc.update("UPDATE quiz_submissions SET taken_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(expiry.minus(20, ChronoUnit.MINUTES)), Timestamp.from(expiry), attempt);
        expect(api("POST", attemptPath(q) + "/" + attempt + "/submit", student.token(), answers(q, true)), expected);
        assertEquals(expected == 200 ? "SUBMITTED" : "EXPIRED", jdbc.queryForObject("SELECT status FROM quiz_submissions WHERE id = ?", String.class, attempt));
        assertEquals(expected == 200 ? 1L : 0L, count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
    }

    private void session(long lesson, Instant starts, Instant ends, int expected) throws Exception {
        expect(api("POST", "/api/lessons/" + lesson + "/sessions", teacher.token(), Map.of("startsAt", starts.toString(), "endsAt", ends.toString(), "format", "OFFLINE")), expected);
    }
}

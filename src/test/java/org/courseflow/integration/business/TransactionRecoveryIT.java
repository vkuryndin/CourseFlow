package org.courseflow.integration.business;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;

/** Force a real late database error, after the service has begun persisting quiz answers. */
class TransactionRecoveryIT extends AdvancedHttpTest {
  @Test
  void failedQuizCompletionRollsBackAnswersAndAllowsRetry() throws Exception {
    Graph graph = graph();
    enroll(graph.course(), student);
    QuizGraph quiz = quiz(graph.module());
    long secondQuestion =
        id(
            expect(
                api(
                    "POST",
                    "/api/quizzes/" + quiz.quiz() + "/questions",
                    teacher.token(),
                    questionPayload(2, 2)),
                201));
    long secondCorrect =
        jdbc.queryForObject(
            "SELECT id FROM answer_options WHERE question_id = ? AND is_correct",
            Long.class,
            secondQuestion);
    long attempt = start(quiz, student);
    String path = attemptPath(quiz) + "/" + attempt + "/submit";
    Map<String, Object> payload =
        Map.of(
            "answers",
            List.of(
                Map.of("questionId", quiz.question(), "optionIds", List.of(quiz.correct())),
                Map.of("questionId", secondQuestion, "optionIds", List.of(secondCorrect))));

    assertIncomplete(attempt);
    // The probe sequence is nontransactional in PostgreSQL. Its value proves the
    // final UPDATE saw both answer rows before the forced error rolled them back.
    try {
      jdbc.execute("CREATE SEQUENCE cf_test_completion_probe START WITH 1");
      jdbc.execute(
          """
                    CREATE FUNCTION cf_test_fail_completion() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.id = %d AND NEW.status = 'SUBMITTED' THEN
                            IF (SELECT count(*) FROM quiz_answer_selections
                                WHERE quiz_submission_id = NEW.id) = 2 THEN
                                PERFORM nextval('cf_test_completion_probe');
                            END IF;
                            RAISE EXCEPTION 'cf_test_forced_completion_failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """
              .formatted(attempt));
      jdbc.execute(
          """
                    CREATE TRIGGER cf_test_fail_completion BEFORE UPDATE ON quiz_submissions
                    FOR EACH ROW EXECUTE FUNCTION cf_test_fail_completion()
                    """);
      Object failure = expect(api("POST", path, student.token(), payload), 500);
      assertEquals("INTERNAL_ERROR", field(failure, "code"));
      assertEquals("Internal server error", field(failure, "message"));
      assertEquals(
          Boolean.TRUE,
          jdbc.queryForObject("SELECT is_called FROM cf_test_completion_probe", Boolean.class),
          "The failed transaction must have reached the final update with both answer rows");
      assertIncomplete(attempt);
    } finally {
      jdbc.execute("DROP TRIGGER IF EXISTS cf_test_fail_completion ON quiz_submissions");
      jdbc.execute("DROP FUNCTION IF EXISTS cf_test_fail_completion()");
      jdbc.execute("DROP SEQUENCE IF EXISTS cf_test_completion_probe");
    }

    Object result = expect(api("POST", path, student.token(), payload), 200);
    assertEquals(attempt, id(result));
    assertEquals("SUBMITTED", field(result, "status"));
    assertEquals(100L, number(result, "score"));
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM quiz_submissions
                WHERE id = ? AND status = 'SUBMITTED' AND score = 100 AND finished_at IS NOT NULL
                """,
            attempt));
    assertEquals(
        2L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM quiz_answer_selections
                WHERE quiz_submission_id = ? AND question_id = ? AND answer_option_id = ?
                """,
            attempt,
            quiz.question(),
            quiz.correct()));
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM quiz_answer_selections
                WHERE quiz_submission_id = ? AND question_id = ? AND answer_option_id = ?
                """,
            attempt,
            secondQuestion,
            secondCorrect));
    assertEquals(
        "BUSINESS_CONFLICT",
        field(expect(api("POST", path, student.token(), payload), 409), "code"));
    assertEquals(
        2L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
  }

  private void assertIncomplete(long attempt) {
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM quiz_submissions
                WHERE id = ? AND status = 'IN_PROGRESS' AND score IS NULL AND finished_at IS NULL
                """,
            attempt));
    assertEquals(
        0L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
  }
}

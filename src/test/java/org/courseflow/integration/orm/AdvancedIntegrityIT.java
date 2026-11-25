package org.courseflow.integration.orm;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Map;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Negative database-level checks, intentionally bypassing controller validation. */
class AdvancedIntegrityIT extends AdvancedHttpTest {
  @Test
  void duplicateModulePositionRejectedByDatabase() throws Exception {
    Graph g = graph();
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO modules (course_id, title, position, version)
                SELECT course_id, 'Duplicate', position, 0 FROM modules WHERE id = ?
                """,
                g.module()));
    assertEquals(1L, count("SELECT count(*) FROM modules WHERE course_id = ?", g.course()));
  }

  @Test
  void duplicateLessonPositionRejectedByDatabase() throws Exception {
    Graph g = graph();
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO lessons (module_id, title, content, position, version)
                SELECT module_id, 'Duplicate', content, position, 0 FROM lessons WHERE id = ?
                """,
                g.lesson()));
    assertEquals(1L, count("SELECT count(*) FROM lessons WHERE module_id = ?", g.module()));
  }

  @Test
  void duplicateQuestionPositionRejectedByDatabase() throws Exception {
    QuizGraph q = quiz(graph().module());
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO questions (quiz_id, text, type, position, version)
                SELECT quiz_id, text, type, position, 0 FROM questions WHERE id = ?
                """,
                q.question()));
    assertEquals(1L, count("SELECT count(*) FROM questions WHERE quiz_id = ?", q.quiz()));
  }

  @Test
  void duplicateQuizForModuleRejectedByDatabase() throws Exception {
    Graph g = graph();
    QuizGraph q = quiz(g.module());
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO quizzes (module_id, title, time_limit, max_attempts, version)
                SELECT module_id, title, time_limit, max_attempts, 0 FROM quizzes WHERE id = ?
                """,
                q.quiz()));
    assertEquals(1L, count("SELECT count(*) FROM quizzes WHERE module_id = ?", g.module()));
  }

  @Test
  void invalidQuizTimeLimitRejectedByDatabase() throws Exception {
    QuizGraph q = quiz(graph().module());
    constraint(
        "23514", () -> jdbc.update("UPDATE quizzes SET time_limit = 241 WHERE id = ?", q.quiz()));
    assertEquals(
        20,
        jdbc.queryForObject(
            "SELECT time_limit FROM quizzes WHERE id = ?", Integer.class, q.quiz()));
  }

  @Test
  void invalidMaxAttemptsRejectedByDatabase() throws Exception {
    QuizGraph q = quiz(graph().module());
    constraint(
        "23514", () -> jdbc.update("UPDATE quizzes SET max_attempts = 11 WHERE id = ?", q.quiz()));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT max_attempts FROM quizzes WHERE id = ?", Integer.class, q.quiz()));
  }

  @Test
  void invalidAssignmentMaxScoreRejectedByDatabase() throws Exception {
    long a = assignment(graph().lesson());
    constraint("23514", () -> jdbc.update("UPDATE assignments SET max_score = 0 WHERE id = ?", a));
    assertEquals(
        100,
        jdbc.queryForObject("SELECT max_score FROM assignments WHERE id = ?", Integer.class, a));
  }

  @Test
  void duplicateHomeworkSubmissionRejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long a = assignment(g.lesson());
    long submission =
        id(
            expect(
                api(
                    "POST",
                    "/api/assignments/" + a + "/submissions",
                    student.token(),
                    Map.of("content", "Answer")),
                201));
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO submissions (assignment_id, student_id, content, submitted_at, status, version)
                SELECT assignment_id, student_id, content, submitted_at, status, 0 FROM submissions WHERE id = ?
                """,
                submission));
    assertEquals(1L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", a));
  }

  @Test
  void negativeHomeworkScoreRejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long a = assignment(g.lesson());
    long submission =
        id(
            expect(
                api(
                    "POST",
                    "/api/assignments/" + a + "/submissions",
                    student.token(),
                    Map.of("content", "Answer")),
                201));
    constraint(
        "23514", () -> jdbc.update("UPDATE submissions SET score = -1 WHERE id = ?", submission));
    assertNull(
        jdbc.queryForObject(
            "SELECT score FROM submissions WHERE id = ?", Integer.class, submission));
  }

  @Test
  void duplicateQuizAttemptNumberRejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long attempt = start(q, student);
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO quiz_submissions (quiz_id, student_id, attempt_number, taken_at, expires_at, status)
                SELECT quiz_id, student_id, attempt_number, taken_at, expires_at, status FROM quiz_submissions WHERE id = ?
                """,
                attempt));
    assertEquals(1L, count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ?", q.quiz()));
  }

  @Test
  void invalidQuizAttemptNumberRejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long attempt = start(quiz(g.module()), student);
    constraint(
        "23514",
        () -> jdbc.update("UPDATE quiz_submissions SET attempt_number = 0 WHERE id = ?", attempt));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT attempt_number FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
  }

  @Test
  void quizScoreOver100RejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long attempt = start(quiz(g.module()), student);
    constraint(
        "23514",
        () -> jdbc.update("UPDATE quiz_submissions SET score = 101 WHERE id = ?", attempt));
    assertNull(
        jdbc.queryForObject(
            "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
  }

  @Test
  void zeroDurationQuizAttemptRejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long attempt = start(quiz(g.module()), student);
    constraint(
        "23514",
        () ->
            jdbc.update("UPDATE quiz_submissions SET expires_at = taken_at WHERE id = ?", attempt));
    assertEquals(
        1L,
        count(
            "SELECT count(*) FROM quiz_submissions WHERE id = ? AND expires_at > taken_at",
            attempt));
  }

  @Test
  void duplicateAnswerSelectionRejectedByDatabase() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long attempt = start(q, student);
    expect(
        api("POST", attemptPath(q) + "/" + attempt + "/submit", student.token(), answers(q, true)),
        200);
    constraint(
        "23505",
        () ->
            jdbc.update(
                """
                INSERT INTO quiz_answer_selections (quiz_submission_id, question_id, answer_option_id)
                SELECT quiz_submission_id, question_id, answer_option_id FROM quiz_answer_selections WHERE quiz_submission_id = ?
                """,
                attempt));
    assertEquals(
        1L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
  }

  @Test
  void secondSystemAdministratorFlagRejectedByDatabase() {
    constraint(
        "23505",
        () -> jdbc.update("UPDATE users SET system_admin = true WHERE id = ?", student.id()));
    assertEquals(1L, count("SELECT count(*) FROM users WHERE system_admin"));
  }

  @Test
  void duplicateRoleMembershipRejectedByDatabase() {
    constraint(
        "23505",
        () ->
            jdbc.update(
                "INSERT INTO user_roles (user_id, role) VALUES (?, 'STUDENT')", student.id()));
    assertEquals(
        1L,
        count(
            "SELECT count(*) FROM user_roles WHERE user_id = ? AND role = 'STUDENT'",
            student.id()));
  }

  @Test
  void duplicateCourseTagJoinRowRejectedByDatabase() throws Exception {
    long course = course();
    long tag =
        id(expect(api("POST", "/api/tags", admin, Map.of("name", "Integrity-" + unique())), 201));
    expect(api("POST", "/api/courses/" + course + "/tags/" + tag, teacher.token(), null), 204);
    constraint(
        "23505",
        () -> jdbc.update("INSERT INTO course_tag (course_id, tag_id) VALUES (?, ?)", course, tag));
    assertEquals(
        1L,
        count("SELECT count(*) FROM course_tag WHERE course_id = ? AND tag_id = ?", course, tag));
  }

  private static void constraint(String expected, Runnable operation) {
    var error = assertThrows(DataIntegrityViolationException.class, operation::run);
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && expected.equals(sql.getSQLState())) return;
    }
    fail("Expected PostgreSQL SQLSTATE " + expected, error);
  }
}

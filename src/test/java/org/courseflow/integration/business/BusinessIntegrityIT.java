package org.courseflow.integration.business;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;

/** Independent, real HTTP workflows with committed database assertions. */
class BusinessIntegrityIT extends AdvancedHttpTest {

  @Test
  void alteredJwtSignatureCannotAuthenticateButOriginalStillWorks() throws Exception {
    String original = student.token();
    String[] parts = original.split("\\.");
    assertEquals(3, parts.length);
    byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
    assertTrue(signature.length > 0);
    signature[0] ^= 1;
    String altered =
        parts[0]
            + "."
            + parts[1]
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    expect(api("GET", "/api/users/me", altered, null), 401);
    assertEquals(student.id(), id(expect(api("GET", "/api/users/me", original, null), 200)));
  }

  @Test
  void deactivationRevokesAllExistingSessionsWithoutDeletingEnrollments() throws Exception {
    Actor target = actor(List.of("STUDENT"));
    long course = course();
    enroll(course, target);
    String second = login(target.email(), PASSWORD);
    assertNotEquals(target.token(), second);
    assertEquals(target.id(), id(expect(api("GET", "/api/users/me", target.token(), null), 200)));
    assertEquals(target.id(), id(expect(api("GET", "/api/users/me", second, null), 200)));
    expect(api("POST", "/api/users/" + target.id() + "/deactivate", admin, null), 200);
    expect(api("GET", "/api/users/me", target.token(), null), 401);
    expect(api("GET", "/api/users/me", second, null), 401);
    assertEquals(
        "INACTIVE",
        jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, target.id()));
    assertEquals(
        1L,
        count(
            "SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?",
            course,
            target.id()));
  }

  @Test
  void maximumLessonContentRoundTripsAndRejectedUpdateLeavesStoredValueUnchanged()
      throws Exception {
    Graph g = graph();
    String path =
        "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons/" + g.lesson();
    String content = "Я漢".repeat(10_000);
    Object before = expect(api("GET", path, teacher.token(), null), 200);
    Object updated =
        expect(
            api(
                "PUT",
                path,
                teacher.token(),
                Map.of(
                    "title",
                    "Boundary lesson",
                    "content",
                    content,
                    "version",
                    number(before, "version"))),
            200);
    assertEquals(content, field(updated, "content"));
    assertEquals(
        content,
        jdbc.queryForObject("SELECT content FROM lessons WHERE id = ?", String.class, g.lesson()));
    Object failure =
        expect(
            api(
                "PUT",
                path,
                teacher.token(),
                Map.of(
                    "title",
                    "Forbidden update",
                    "content",
                    content + "X",
                    "version",
                    number(updated, "version"))),
            400);
    assertEquals(400L, number(failure, "status"));
    assertEquals(updated, expect(api("GET", path, teacher.token(), null), 200));
    assertEquals(
        content,
        jdbc.queryForObject("SELECT content FROM lessons WHERE id = ?", String.class, g.lesson()));
    assertEquals(
        number(updated, "version"),
        jdbc.queryForObject("SELECT version FROM lessons WHERE id = ?", Long.class, g.lesson()));
  }

  @Test
  void invalidQuizAnswerCannotPartiallyPersistAndRetryCalculatesActualScore() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long second =
        id(
            expect(
                api(
                    "POST",
                    "/api/quizzes/" + q.quiz() + "/questions",
                    teacher.token(),
                    Map.of(
                        "text",
                        "Select both correct answers",
                        "type",
                        "MULTIPLE_CHOICE",
                        "position",
                        2,
                        "options",
                        List.of(
                            Map.of("text", "A", "correct", true),
                            Map.of("text", "B", "correct", true),
                            Map.of("text", "C", "correct", false)))),
                201));
    long third =
        id(
            expect(
                api(
                    "POST",
                    "/api/quizzes/" + q.quiz() + "/questions",
                    teacher.token(),
                    questionPayload(3, 2)),
                201));
    long attempt = start(q, student);
    String path = attemptPath(q) + "/" + attempt;
    Object firstAnswer = Map.of("questionId", q.question(), "optionIds", List.of(q.correct()));
    Object invalid =
        expect(
            api(
                "POST",
                path + "/submit",
                student.token(),
                Map.of(
                    "answers",
                    List.of(
                        firstAnswer,
                        Map.of("questionId", second, "optionIds", List.of(q.correct()))))),
            400);
    assertEquals("BAD_REQUEST", field(invalid, "code"));
    assertEquals("Answer option does not belong to question: " + second, field(invalid, "message"));
    assertEquals(
        "IN_PROGRESS",
        jdbc.queryForObject(
            "SELECT status FROM quiz_submissions WHERE id = ?", String.class, attempt));
    assertNull(
        jdbc.queryForObject(
            "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
    assertEquals(
        0L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
    List<Long> correctOptions =
        jdbc.queryForList(
            "SELECT id FROM answer_options WHERE question_id = ? AND is_correct ORDER BY id DESC",
            Long.class,
            second);
    assertEquals(2, correctOptions.size());
    // Deliberately omit third question: 2 / 3 is truncated to 66, not rounded up.
    Object completed =
        expect(
            api(
                "POST",
                path + "/submit",
                student.token(),
                Map.of(
                    "answers",
                    List.of(
                        firstAnswer, Map.of("questionId", second, "optionIds", correctOptions)))),
            200);
    assertEquals(66L, number(completed, "score"));
    assertEquals("SUBMITTED", field(completed, "status"));
    assertEquals(
        66,
        jdbc.queryForObject(
            "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
    assertEquals(
        3L,
        count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
    assertEquals(
        0L,
        count(
            "SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ? AND question_id = ?",
            attempt,
            third));
    expect(api("POST", path + "/submit", student.token(), Map.of("answers", List.of())), 409);
    assertEquals(
        66,
        jdbc.queryForObject(
            "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
  }

  @Test
  void submittedAndInProgressAttemptFiltersReturnDifferentKnownIds() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    QuizGraph q = quiz(g.module());
    long first = start(q, student);
    expect(
        api("POST", attemptPath(q) + "/" + first + "/submit", student.token(), answers(q, false)),
        200);
    long second = start(q, student);
    String path = attemptPath(q) + "/me";
    Object submitted = expect(api("GET", path + "?status=SUBMITTED", student.token(), null), 200);
    Object active = expect(api("GET", path + "?status=IN_PROGRESS", student.token(), null), 200);
    assertEquals(1L, number(submitted, "totalElements"));
    assertEquals(1L, number(active, "totalElements"));
    assertEquals(1, ((List<?>) field(submitted, "content")).size());
    assertEquals(1, ((List<?>) field(active, "content")).size());
    assertEquals(first, id(((List<?>) field(submitted, "content")).get(0)));
    assertEquals(second, id(((List<?>) field(active, "content")).get(0)));
  }
}

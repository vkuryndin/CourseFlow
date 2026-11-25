package org.courseflow.integration.boundary;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;

/** Exact accepted and rejected HTTP/DTO boundaries with real persistence. */
class ApiBoundaryIT extends AdvancedHttpTest {

  @Test
  void categoryNameLength99() throws Exception {
    var payload =
        new HashMap<>(Map.of("name", "Category-" + unique(), "description", "Description"));
    payload.put("name", "x".repeat(99 - 36) + unique());
    expect(api("POST", "/api/categories", admin, payload), 201);
  }

  @Test
  void categoryNameLength100() throws Exception {
    var payload =
        new HashMap<>(Map.of("name", "Category-" + unique(), "description", "Description"));
    payload.put("name", "x".repeat(100 - 36) + unique());
    expect(api("POST", "/api/categories", admin, payload), 201);
  }

  @Test
  void categoryNameLength101() throws Exception {
    var payload =
        new HashMap<>(Map.of("name", "Category-" + unique(), "description", "Description"));
    payload.put("name", "x".repeat(101 - 36) + unique());
    expect(api("POST", "/api/categories", admin, payload), 400);
  }

  @Test
  void categoryDescriptionLength1999() throws Exception {
    var payload =
        new HashMap<>(Map.of("name", "Category-" + unique(), "description", "Description"));
    payload.put("description", "d".repeat(1999));
    expect(api("POST", "/api/categories", admin, payload), 201);
  }

  @Test
  void categoryDescriptionLength2000() throws Exception {
    var payload =
        new HashMap<>(Map.of("name", "Category-" + unique(), "description", "Description"));
    payload.put("description", "d".repeat(2000));
    expect(api("POST", "/api/categories", admin, payload), 201);
  }

  @Test
  void categoryDescriptionLength2001() throws Exception {
    var payload =
        new HashMap<>(Map.of("name", "Category-" + unique(), "description", "Description"));
    payload.put("description", "d".repeat(2001));
    expect(api("POST", "/api/categories", admin, payload), 400);
  }

  @Test
  void tagNameLength49() throws Exception {
    var payload = Map.of("name", "t".repeat(49 - 36) + unique());
    expect(api("POST", "/api/tags", admin, payload), 201);
  }

  @Test
  void tagNameLength50() throws Exception {
    var payload = Map.of("name", "t".repeat(50 - 36) + unique());
    expect(api("POST", "/api/tags", admin, payload), 201);
  }

  @Test
  void tagNameLength51() throws Exception {
    var payload = Map.of("name", "t".repeat(51 - 36) + unique());
    expect(api("POST", "/api/tags", admin, payload), 400);
  }

  @Test
  void courseTitleLength199() throws Exception {
    var payload = coursePayload();
    payload.put("title", "x".repeat(199));
    expect(api("POST", "/api/courses", teacher.token(), payload), 201);
  }

  @Test
  void courseTitleLength200() throws Exception {
    var payload = coursePayload();
    payload.put("title", "x".repeat(200));
    expect(api("POST", "/api/courses", teacher.token(), payload), 201);
  }

  @Test
  void courseTitleLength201() throws Exception {
    var payload = coursePayload();
    payload.put("title", "x".repeat(201));
    expect(api("POST", "/api/courses", teacher.token(), payload), 400);
  }

  @Test
  void courseDescriptionLength4999() throws Exception {
    var payload = coursePayload();
    payload.put("description", "x".repeat(4999));
    expect(api("POST", "/api/courses", teacher.token(), payload), 201);
  }

  @Test
  void courseDescriptionLength5000() throws Exception {
    var payload = coursePayload();
    payload.put("description", "x".repeat(5000));
    expect(api("POST", "/api/courses", teacher.token(), payload), 201);
  }

  @Test
  void courseDescriptionLength5001() throws Exception {
    var payload = coursePayload();
    payload.put("description", "x".repeat(5001));
    expect(api("POST", "/api/courses", teacher.token(), payload), 400);
  }

  @Test
  void courseDuration0() throws Exception {
    var payload = coursePayload();
    payload.put("duration", 0);
    expect(api("POST", "/api/courses", teacher.token(), payload), 400);
  }

  @Test
  void courseDuration1() throws Exception {
    var payload = coursePayload();
    payload.put("duration", 1);
    expect(api("POST", "/api/courses", teacher.token(), payload), 201);
  }

  @Test
  void courseDuration2() throws Exception {
    var payload = coursePayload();
    payload.put("duration", 2);
    expect(api("POST", "/api/courses", teacher.token(), payload), 201);
  }

  @Test
  void pageSize0() throws Exception {
    var response = api("GET", "/api/courses?page=0&size=0", admin, null);
    expect(response, 400);
  }

  @Test
  void pageSize1() throws Exception {
    var response = api("GET", "/api/courses?page=0&size=1", admin, null);
    expect(response, 200);
    assertEquals(1L, number(response.body(), "size"));
  }

  @Test
  void pageSize100() throws Exception {
    var response = api("GET", "/api/courses?page=0&size=100", admin, null);
    expect(response, 200);
    assertEquals(100L, number(response.body(), "size"));
  }

  @Test
  void pageSize101() throws Exception {
    var response = api("GET", "/api/courses?page=0&size=101", admin, null);
    expect(response, 400);
  }

  @Test
  void courseSearchLength199() throws Exception {
    expect(api("GET", "/api/courses?q=" + "x".repeat(199), admin, null), 200);
  }

  @Test
  void courseSearchLength200() throws Exception {
    expect(api("GET", "/api/courses?q=" + "x".repeat(200), admin, null), 200);
  }

  @Test
  void courseSearchLength201() throws Exception {
    expect(api("GET", "/api/courses?q=" + "x".repeat(201), admin, null), 400);
  }

  @Test
  void pageOffsetOverflowRejected() throws Exception {
    expect(api("GET", "/api/courses?page=21474837&size=100", admin, null), 400);
  }

  @Test
  void moduleTitleLength200() throws Exception {
    long course = course();
    var payload = new HashMap<String, Object>(Map.of("title", "Module", "position", 1));
    payload.put("title", "x".repeat(200));
    expect(api("POST", "/api/courses/" + course + "/modules", teacher.token(), payload), 201);
  }

  @Test
  void moduleTitleLength201() throws Exception {
    long course = course();
    var payload = new HashMap<String, Object>(Map.of("title", "Module", "position", 1));
    payload.put("title", "x".repeat(201));
    expect(api("POST", "/api/courses/" + course + "/modules", teacher.token(), payload), 400);
  }

  @Test
  void moduleDescriptionLength2000() throws Exception {
    long course = course();
    var payload = new HashMap<String, Object>(Map.of("title", "Module", "position", 1));
    payload.put("description", "x".repeat(2000));
    expect(api("POST", "/api/courses/" + course + "/modules", teacher.token(), payload), 201);
  }

  @Test
  void moduleDescriptionLength2001() throws Exception {
    long course = course();
    var payload = new HashMap<String, Object>(Map.of("title", "Module", "position", 1));
    payload.put("description", "x".repeat(2001));
    expect(api("POST", "/api/courses/" + course + "/modules", teacher.token(), payload), 400);
  }

  @Test
  void lessonTitleLength200() throws Exception {
    Graph g = graph();
    var payload =
        new HashMap<String, Object>(Map.of("title", "Lesson", "content", "Content", "position", 2));
    payload.put("title", "x".repeat(200));
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
            teacher.token(),
            payload),
        201);
  }

  @Test
  void lessonTitleLength201() throws Exception {
    Graph g = graph();
    var payload =
        new HashMap<String, Object>(Map.of("title", "Lesson", "content", "Content", "position", 2));
    payload.put("title", "x".repeat(201));
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
            teacher.token(),
            payload),
        400);
  }

  @Test
  void lessonContentLength20000() throws Exception {
    Graph g = graph();
    var payload =
        new HashMap<String, Object>(Map.of("title", "Lesson", "content", "Content", "position", 2));
    payload.put("content", "x".repeat(20000));
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
            teacher.token(),
            payload),
        201);
  }

  @Test
  void lessonContentLength20001() throws Exception {
    Graph g = graph();
    var payload =
        new HashMap<String, Object>(Map.of("title", "Lesson", "content", "Content", "position", 2));
    payload.put("content", "x".repeat(20001));
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
            teacher.token(),
            payload),
        400);
  }

  @Test
  void lessonVideourlLength2048() throws Exception {
    Graph g = graph();
    var payload =
        new HashMap<String, Object>(Map.of("title", "Lesson", "content", "Content", "position", 2));
    payload.put("videoUrl", "https://example.org/" + "x".repeat(2048 - 20));
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
            teacher.token(),
            payload),
        201);
  }

  @Test
  void lessonVideourlLength2049() throws Exception {
    Graph g = graph();
    var payload =
        new HashMap<String, Object>(Map.of("title", "Lesson", "content", "Content", "position", 2));
    payload.put("videoUrl", "https://example.org/" + "x".repeat(2049 - 20));
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
            teacher.token(),
            payload),
        400);
  }

  @Test
  void assignmentTitleLength200() throws Exception {
    Graph g = graph();
    var payload = assignmentPayload();
    payload.put("title", "x".repeat(200));
    expect(
        api("POST", "/api/lessons/" + g.lesson() + "/assignments", teacher.token(), payload), 201);
  }

  @Test
  void assignmentTitleLength201() throws Exception {
    Graph g = graph();
    var payload = assignmentPayload();
    payload.put("title", "x".repeat(201));
    expect(
        api("POST", "/api/lessons/" + g.lesson() + "/assignments", teacher.token(), payload), 400);
  }

  @Test
  void assignmentDescriptionLength20000() throws Exception {
    Graph g = graph();
    var payload = assignmentPayload();
    payload.put("description", "x".repeat(20000));
    expect(
        api("POST", "/api/lessons/" + g.lesson() + "/assignments", teacher.token(), payload), 201);
  }

  @Test
  void assignmentDescriptionLength20001() throws Exception {
    Graph g = graph();
    var payload = assignmentPayload();
    payload.put("description", "x".repeat(20001));
    expect(
        api("POST", "/api/lessons/" + g.lesson() + "/assignments", teacher.token(), payload), 400);
  }

  @Test
  void assignmentMaxScore0() throws Exception {
    Graph g = graph();
    var payload = assignmentPayload();
    payload.put("maxScore", 0);
    expect(
        api("POST", "/api/lessons/" + g.lesson() + "/assignments", teacher.token(), payload), 400);
  }

  @Test
  void assignmentMaxScore1() throws Exception {
    Graph g = graph();
    var payload = assignmentPayload();
    payload.put("maxScore", 1);
    expect(
        api("POST", "/api/lessons/" + g.lesson() + "/assignments", teacher.token(), payload), 201);
  }

  @Test
  void submissionContentLength20000() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long assignment = assignment(g.lesson());
    expect(
        api(
            "POST",
            "/api/assignments/" + assignment + "/submissions",
            student.token(),
            Map.of("content", "x".repeat(20000))),
        201);
    assertEquals(1L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment));
  }

  @Test
  void submissionContentLength20001() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long assignment = assignment(g.lesson());
    expect(
        api(
            "POST",
            "/api/assignments/" + assignment + "/submissions",
            student.token(),
            Map.of("content", "x".repeat(20001))),
        400);
    assertEquals(0L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment));
  }

  @Test
  void gradeScoreMinus1() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long assignment = assignment(g.lesson());
    Object submission =
        expect(
            api(
                "POST",
                "/api/assignments/" + assignment + "/submissions",
                student.token(),
                Map.of("content", "Answer")),
            201);
    String path = "/api/assignments/" + assignment + "/submissions/" + id(submission) + "/grade";
    var response =
        api(
            "PUT",
            path,
            teacher.token(),
            Map.of("version", number(submission, "version"), "score", -1));
    expect(response, 400);
    assertNull(
        jdbc.queryForObject(
            "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)));
  }

  @Test
  void gradeScore0() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long assignment = assignment(g.lesson());
    Object submission =
        expect(
            api(
                "POST",
                "/api/assignments/" + assignment + "/submissions",
                student.token(),
                Map.of("content", "Answer")),
            201);
    String path = "/api/assignments/" + assignment + "/submissions/" + id(submission) + "/grade";
    var response =
        api(
            "PUT",
            path,
            teacher.token(),
            Map.of("version", number(submission, "version"), "score", 0));
    expect(response, 200);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)));
  }

  @Test
  void gradeScore100() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long assignment = assignment(g.lesson());
    Object submission =
        expect(
            api(
                "POST",
                "/api/assignments/" + assignment + "/submissions",
                student.token(),
                Map.of("content", "Answer")),
            201);
    String path = "/api/assignments/" + assignment + "/submissions/" + id(submission) + "/grade";
    var response =
        api(
            "PUT",
            path,
            teacher.token(),
            Map.of("version", number(submission, "version"), "score", 100));
    expect(response, 200);
    assertEquals(
        100,
        jdbc.queryForObject(
            "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)));
  }

  @Test
  void gradeScore101() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    long assignment = assignment(g.lesson());
    Object submission =
        expect(
            api(
                "POST",
                "/api/assignments/" + assignment + "/submissions",
                student.token(),
                Map.of("content", "Answer")),
            201);
    String path = "/api/assignments/" + assignment + "/submissions/" + id(submission) + "/grade";
    var response =
        api(
            "PUT",
            path,
            teacher.token(),
            Map.of("version", number(submission, "version"), "score", 101));
    expect(response, 400);
    assertNull(
        jdbc.queryForObject(
            "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)));
  }

  @Test
  void reviewRating0() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/reviews",
            student.token(),
            Map.of("rating", 0, "comment", "Review")),
        400);
    assertEquals(0L, count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()));
  }

  @Test
  void reviewRating1() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/reviews",
            student.token(),
            Map.of("rating", 1, "comment", "Review")),
        201);
    assertEquals(1L, count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()));
  }

  @Test
  void reviewRating5() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/reviews",
            student.token(),
            Map.of("rating", 5, "comment", "Review")),
        201);
    assertEquals(1L, count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()));
  }

  @Test
  void reviewRating6() throws Exception {
    Graph g = graph();
    enroll(g.course(), student);
    expect(
        api(
            "POST",
            "/api/courses/" + g.course() + "/reviews",
            student.token(),
            Map.of("rating", 6, "comment", "Review")),
        400);
    assertEquals(0L, count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()));
  }

  @Test
  void quizTimeLimit0() throws Exception {
    Graph g = graph();
    expect(
        api(
            "POST",
            "/api/modules/" + g.module() + "/quiz",
            teacher.token(),
            Map.of("title", "Quiz", "timeLimit", 0)),
        400);
  }

  @Test
  void quizTimeLimit1() throws Exception {
    Graph g = graph();
    expect(
        api(
            "POST",
            "/api/modules/" + g.module() + "/quiz",
            teacher.token(),
            Map.of("title", "Quiz", "timeLimit", 1)),
        201);
  }

  @Test
  void quizTimeLimit240() throws Exception {
    Graph g = graph();
    expect(
        api(
            "POST",
            "/api/modules/" + g.module() + "/quiz",
            teacher.token(),
            Map.of("title", "Quiz", "timeLimit", 240)),
        201);
  }

  @Test
  void quizTimeLimit241() throws Exception {
    Graph g = graph();
    expect(
        api(
            "POST",
            "/api/modules/" + g.module() + "/quiz",
            teacher.token(),
            Map.of("title", "Quiz", "timeLimit", 241)),
        400);
  }

  @Test
  void quizMaxAttempts0() throws Exception {
    QuizGraph q = quiz(graph().module());
    Object current = expect(api("GET", "/api/quizzes/" + q.quiz(), teacher.token(), null), 200);
    expect(
        api(
            "PUT",
            "/api/quizzes/" + q.quiz(),
            teacher.token(),
            Map.of(
                "title",
                "Updated quiz",
                "timeLimit",
                20,
                "maxAttempts",
                0,
                "version",
                number(current, "version"))),
        400);
  }

  @Test
  void quizMaxAttempts1() throws Exception {
    QuizGraph q = quiz(graph().module());
    Object current = expect(api("GET", "/api/quizzes/" + q.quiz(), teacher.token(), null), 200);
    expect(
        api(
            "PUT",
            "/api/quizzes/" + q.quiz(),
            teacher.token(),
            Map.of(
                "title",
                "Updated quiz",
                "timeLimit",
                20,
                "maxAttempts",
                1,
                "version",
                number(current, "version"))),
        200);
  }

  @Test
  void quizMaxAttempts10() throws Exception {
    QuizGraph q = quiz(graph().module());
    Object current = expect(api("GET", "/api/quizzes/" + q.quiz(), teacher.token(), null), 200);
    expect(
        api(
            "PUT",
            "/api/quizzes/" + q.quiz(),
            teacher.token(),
            Map.of(
                "title",
                "Updated quiz",
                "timeLimit",
                20,
                "maxAttempts",
                10,
                "version",
                number(current, "version"))),
        200);
  }

  @Test
  void quizMaxAttempts11() throws Exception {
    QuizGraph q = quiz(graph().module());
    Object current = expect(api("GET", "/api/quizzes/" + q.quiz(), teacher.token(), null), 200);
    expect(
        api(
            "PUT",
            "/api/quizzes/" + q.quiz(),
            teacher.token(),
            Map.of(
                "title",
                "Updated quiz",
                "timeLimit",
                20,
                "maxAttempts",
                11,
                "version",
                number(current, "version"))),
        400);
  }

  @Test
  void questionOptionCount1() throws Exception {
    QuizGraph q = quiz(graph().module());
    expect(
        api(
            "POST",
            "/api/quizzes/" + q.quiz() + "/questions",
            teacher.token(),
            questionPayload(2, 1)),
        400);
  }

  @Test
  void questionOptionCount2() throws Exception {
    QuizGraph q = quiz(graph().module());
    expect(
        api(
            "POST",
            "/api/quizzes/" + q.quiz() + "/questions",
            teacher.token(),
            questionPayload(2, 2)),
        201);
  }

  @Test
  void questionOptionCount10() throws Exception {
    QuizGraph q = quiz(graph().module());
    expect(
        api(
            "POST",
            "/api/quizzes/" + q.quiz() + "/questions",
            teacher.token(),
            questionPayload(2, 10)),
        201);
  }

  @Test
  void questionOptionCount11() throws Exception {
    QuizGraph q = quiz(graph().module());
    expect(
        api(
            "POST",
            "/api/quizzes/" + q.quiz() + "/questions",
            teacher.token(),
            questionPayload(2, 11)),
        400);
  }

  @Test
  void questionTextLength5000() throws Exception {
    QuizGraph q = quiz(graph().module());
    var payload = questionPayload(2, 2);
    payload.put("text", "x".repeat(5000));
    expect(api("POST", "/api/quizzes/" + q.quiz() + "/questions", teacher.token(), payload), 201);
  }

  @Test
  void questionTextLength5001() throws Exception {
    QuizGraph q = quiz(graph().module());
    var payload = questionPayload(2, 2);
    payload.put("text", "x".repeat(5001));
    expect(api("POST", "/api/quizzes/" + q.quiz() + "/questions", teacher.token(), payload), 400);
  }

  @Test
  void passwordSevenChars() throws Exception {
    String password = "a".repeat(7);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        400);
    assertEquals(0L, count("SELECT count(*) FROM users WHERE email = ?", address));
  }

  @Test
  void passwordEightChars() throws Exception {
    String password = "a".repeat(8);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        201);
    assertEquals(1L, count("SELECT count(*) FROM users WHERE email = ?", address));
    assertFalse(login(address, password).isBlank());
  }

  @Test
  void passwordAscii72() throws Exception {
    String password = "a".repeat(72);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        201);
    assertEquals(1L, count("SELECT count(*) FROM users WHERE email = ?", address));
    assertFalse(login(address, password).isBlank());
  }

  @Test
  void passwordAscii73() throws Exception {
    String password = "a".repeat(73);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        400);
    assertEquals(0L, count("SELECT count(*) FROM users WHERE email = ?", address));
  }

  @Test
  void passwordUtf8Exactly72() throws Exception {
    String password = "я".repeat(36);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        201);
    assertEquals(1L, count("SELECT count(*) FROM users WHERE email = ?", address));
    assertFalse(login(address, password).isBlank());
  }

  @Test
  void passwordUtf8Over72() throws Exception {
    String password = "я".repeat(36) + "a";
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        400);
    assertEquals(0L, count("SELECT count(*) FROM users WHERE email = ?", address));
  }

  @Test
  void passwordSupplementary72() throws Exception {
    String password = "😀".repeat(18);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        201);
    assertEquals(1L, count("SELECT count(*) FROM users WHERE email = ?", address));
    assertFalse(login(address, password).isBlank());
  }

  @Test
  void passwordSupplementary76() throws Exception {
    String password = "😀".repeat(19);
    String address = email();
    expect(
        api(
            "POST",
            "/api/users",
            admin,
            Map.of(
                "displayName",
                "Boundary",
                "email",
                address,
                "password",
                password,
                "passwordChangeRequired",
                false,
                "roles",
                List.of("STUDENT"))),
        400);
    assertEquals(0L, count("SELECT count(*) FROM users WHERE email = ?", address));
  }
}

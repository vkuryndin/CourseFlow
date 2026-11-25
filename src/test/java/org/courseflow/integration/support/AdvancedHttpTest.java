package org.courseflow.integration.support;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.courseflow.integration.support.JsonChecks.str;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Committed fixtures through the real API. Each scenario creates its own course or resource. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AdvancedHttpTest extends HttpIntegrationTest {
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected Clock clock;
  protected String admin;
  protected Actor teacher;
  protected Actor student;
  protected long categoryId;

  protected record Actor(long id, String token, String email) {}

  protected record Graph(long course, long module, long lesson) {}

  protected record QuizGraph(long quiz, long question, long correct, long wrong) {}

  @BeforeAll
  protected void prepareAdvancedActors() throws Exception {
    admin = login(adminEmail, adminPassword);
    teacher = actor(List.of("TEACHER", "STUDENT"));
    student = actor(List.of("STUDENT"));
    categoryId =
        id(
            expect(
                api(
                    "POST",
                    "/api/categories",
                    admin,
                    Map.of("name", "Advanced-" + unique(), "description", "Test category")),
                201));
  }

  protected Actor actor(List<String> roles) throws Exception {
    String email = email();
    Object user =
        expect(
            api(
                "POST",
                "/api/users",
                admin,
                Map.of(
                    "displayName",
                    "Actor-" + unique(),
                    "email",
                    email,
                    "password",
                    PASSWORD,
                    "passwordChangeRequired",
                    false,
                    "roles",
                    roles)),
            201);
    return new Actor(id(user), login(email, PASSWORD), email);
  }

  protected String login(String email, String password) throws Exception {
    return str(
        field(
            expect(
                api("POST", "/api/auth/login", null, Map.of("email", email, "password", password)),
                200),
            "accessToken"));
  }

  protected Map<String, Object> coursePayload() {
    return new HashMap<>(
        Map.of(
            "title",
            "Course-" + unique(),
            "description",
            "Course description",
            "categoryId",
            categoryId,
            "teacherId",
            teacher.id(),
            "duration",
            56,
            "startDate",
            LocalDate.now(clock).plusDays(1).toString()));
  }

  protected long course() throws Exception {
    return id(expect(api("POST", "/api/courses", teacher.token(), coursePayload()), 201));
  }

  protected Graph graph() throws Exception {
    long course = course();
    long module =
        id(
            expect(
                api(
                    "POST",
                    "/api/courses/" + course + "/modules",
                    teacher.token(),
                    Map.of("title", "Module", "position", 1)),
                201));
    long lesson =
        id(
            expect(
                api(
                    "POST",
                    "/api/courses/" + course + "/modules/" + module + "/lessons",
                    teacher.token(),
                    Map.of("title", "Lesson", "content", "Content", "position", 1)),
                201));
    return new Graph(course, module, lesson);
  }

  protected void enroll(long course, Actor actor) throws Exception {
    expect(api("POST", "/api/courses/" + course + "/enrollments", actor.token(), null), 201);
  }

  protected Map<String, Object> assignmentPayload() {
    return new HashMap<>(
        Map.of(
            "title",
            "Assignment",
            "description",
            "Description",
            "maxScore",
            100,
            "dueAt",
            clock.instant().plus(10, ChronoUnit.DAYS).toString()));
  }

  protected long assignment(long lesson) throws Exception {
    return id(
        expect(
            api(
                "POST",
                "/api/lessons/" + lesson + "/assignments",
                teacher.token(),
                assignmentPayload()),
            201));
  }

  protected QuizGraph quiz(long module) throws Exception {
    long quiz =
        id(
            expect(
                api(
                    "POST",
                    "/api/modules/" + module + "/quiz",
                    teacher.token(),
                    Map.of("title", "Quiz", "timeLimit", 20)),
                201));
    long question =
        id(
            expect(
                api(
                    "POST",
                    "/api/quizzes/" + quiz + "/questions",
                    teacher.token(),
                    questionPayload(1, 2)),
                201));
    long correct =
        jdbc.queryForObject(
            "SELECT id FROM answer_options WHERE question_id = ? AND is_correct",
            Long.class,
            question);
    long wrong =
        jdbc.queryForObject(
            "SELECT id FROM answer_options WHERE question_id = ? AND NOT is_correct",
            Long.class,
            question);
    return new QuizGraph(quiz, question, correct, wrong);
  }

  protected Map<String, Object> questionPayload(int position, int options) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (int i = 0; i < options; i++) {
      list.add(new HashMap<>(Map.of("text", "Option " + i, "correct", i == 0)));
    }
    return new HashMap<>(
        Map.of("text", "Question", "type", "SINGLE_CHOICE", "position", position, "options", list));
  }

  protected long start(QuizGraph quiz, Actor actor) throws Exception {
    return id(expect(api("POST", attemptPath(quiz), actor.token(), null), 201));
  }

  protected String attemptPath(QuizGraph quiz) {
    return "/api/quizzes/" + quiz.quiz() + "/attempts";
  }

  protected Map<String, Object> answers(QuizGraph quiz, boolean correct) {
    return Map.of(
        "answers",
        List.of(
            Map.of(
                "questionId",
                quiz.question(),
                "optionIds",
                List.of(correct ? quiz.correct() : quiz.wrong()))));
  }

  protected long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  protected static String unique() {
    return UUID.randomUUID().toString();
  }

  protected static String email() {
    return "advanced." + unique() + "@courseflow.local";
  }

  protected static long id(Object response) {
    return number(response, "id");
  }

  protected static long number(Object response, String fieldName) {
    return JsonChecks.requiredInteger(response, fieldName);
  }

  protected static void statuses(List<Response> responses, int... expected) {
    assertEquals(
        java.util.Arrays.stream(expected).sorted().boxed().toList(),
        responses.stream().map(Response::status).sorted().toList());
  }

  protected static <T> List<T> parallel(List<Callable<T>> calls) throws Exception {
    return ConcurrentCalls.run(calls);
  }
}

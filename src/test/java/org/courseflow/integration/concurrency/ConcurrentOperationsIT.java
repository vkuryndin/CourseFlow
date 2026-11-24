package org.courseflow.integration.concurrency;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.courseflow.integration.support.JsonChecks.str;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.courseflow.integration.support.ConcurrentCalls;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentOperationsIT extends HttpIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JavaMailSender mailSender;

    private String admin;
    private String teacher;
    private String student;
    private long teacherId;
    private long studentId;
    private long categoryId;

    @BeforeAll
    void createActors() throws Exception {
        admin = login(adminEmail, adminPassword);
        String teacherEmail = email();
        teacherId = id(createUser(teacherEmail, List.of("TEACHER", "STUDENT")));
        teacher = login(teacherEmail, PASSWORD);
        String studentEmail = email();
        studentId = id(createUser(studentEmail, List.of("STUDENT")));
        student = login(studentEmail, PASSWORD);
        categoryId = id(expect(api("POST", "/api/categories", admin,
                Map.of("name", "Race " + unique(), "description", "Concurrent tests")), 201));
    }

    @Test
    void concurrentRegistrationCreatesOneAccount() throws Exception {
        String address = email();
        var payload = Map.of("displayName", "Race registration", "email", address, "password", PASSWORD);
        var responses = ConcurrentCalls.pair(
                () -> api("POST", "/api/auth/register", null, payload),
                () -> api("POST", "/api/auth/register", null, payload));
        statuses(responses, 201, 409);
        assertEquals(1L, count("SELECT count(*) FROM users WHERE email = ?", address));
        assertEquals(1L, count("""
                SELECT count(*) FROM user_accounts a JOIN users u ON a.user_id = u.id
                WHERE u.email = ?
                """, address));
    }

    @Test
    void concurrentEnrollmentIsIdempotent() throws Exception {
        long course = createCourse();
        String path = "/api/courses/" + course + "/enrollments";
        var responses = ConcurrentCalls.pair(
                () -> api("POST", path, student, null),
                () -> api("POST", path, student, null));
        statuses(responses, 200, 201);
        assertEquals(id(responses.get(0).body()), id(responses.get(1).body()));
        assertEquals(1L, count("SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?",
                course, studentId));
    }

    @Test
    void concurrentHomeworkCreatesOnlyOneSubmission() throws Exception {
        Fixture f = fixture(true);
        long assignment = createAssignment(f.lesson());
        String path = "/api/assignments/" + assignment + "/submissions";
        var responses = ConcurrentCalls.pair(
                () -> api("POST", path, student, Map.of("content", "First answer")),
                () -> api("POST", path, student, Map.of("content", "Second answer")));
        statuses(responses, 201, 409);
        assertEquals(1L, count("SELECT count(*) FROM submissions WHERE assignment_id = ? AND student_id = ?",
                assignment, studentId));
        var winner = responses.stream().filter(r -> r.status() == 201).findFirst().orElseThrow();
        assertEquals(id(winner.body()), id(expect(api("GET", path + "/me", student, null), 200)));
    }

    @Test
    void concurrentQuizStartResumesTheSameAttempt() throws Exception {
        Fixture f = fixture(true);
        QuizFixture quiz = createQuiz(f.module());
        String path = "/api/quizzes/" + quiz.quiz() + "/attempts";
        var responses = ConcurrentCalls.pair(
                () -> api("POST", path, student, null),
                () -> api("POST", path, student, null));
        statuses(responses, 200, 201);
        assertEquals(id(responses.get(0).body()), id(responses.get(1).body()));
        assertEquals(1L, count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND student_id = ?",
                quiz.quiz(), studentId));
    }

    @Test
    void concurrentQuizSubmitCommitsOnlyOneResult() throws Exception {
        Fixture f = fixture(true);
        QuizFixture quiz = createQuiz(f.module());
        String base = "/api/quizzes/" + quiz.quiz() + "/attempts";
        long attempt = id(expect(api("POST", base, student, null), 201));
        var correct = Map.of("answers", List.of(Map.of("questionId", quiz.question(),
                "optionIds", List.of(quiz.correct()))));
        var wrong = Map.of("answers", List.of(Map.of("questionId", quiz.question(),
                "optionIds", List.of(quiz.wrong()))));
        var responses = ConcurrentCalls.pair(
                () -> api("POST", base + "/" + attempt + "/submit", student, correct),
                () -> api("POST", base + "/" + attempt + "/submit", student, wrong));
        statuses(responses, 200, 409);
        var winner = responses.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        assertEquals(intField(winner.body(), "score"), jdbc.queryForObject(
                "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt));
        assertEquals("SUBMITTED", jdbc.queryForObject(
                "SELECT status FROM quiz_submissions WHERE id = ?", String.class, attempt));
        assertEquals(1L, count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt));
        long selected = jdbc.queryForObject("SELECT answer_option_id FROM quiz_answer_selections WHERE quiz_submission_id = ?",
                Long.class, attempt);
        assertEquals(intField(winner.body(), "score") == 100 ? quiz.correct() : quiz.wrong(), selected);
    }

    @Test
    void concurrentReviewCannotCreateDuplicate() throws Exception {
        Fixture f = fixture(true);
        String path = "/api/courses/" + f.course() + "/reviews";
        var responses = ConcurrentCalls.pair(
                () -> api("POST", path, student, Map.of("rating", 4, "comment", "First")),
                () -> api("POST", path, student, Map.of("rating", 5, "comment", "Second")));
        statuses(responses, 201, 409);
        assertEquals(1L, count("SELECT count(*) FROM course_reviews WHERE course_id = ? AND student_id = ?",
                f.course(), studentId));
        var winner = responses.stream().filter(r -> r.status() == 201).findFirst().orElseThrow();
        assertEquals(intField(winner.body(), "rating"), jdbc.queryForObject(
                "SELECT rating FROM course_reviews WHERE course_id = ? AND student_id = ?", Integer.class,
                f.course(), studentId));
    }

    @Test
    void concurrentOverlappingSessionsAcrossCoursesRejectOne() throws Exception {
        Fixture a = fixture(false);
        Fixture b = fixture(false);
        Instant starts = LocalDate.now(ZoneOffset.UTC).plusDays(6).atTime(10, 0).toInstant(ZoneOffset.UTC);
        // Same teacher, different courses and the same time interval.
        var payload = Map.of("startsAt", starts.toString(), "endsAt", starts.plus(1, ChronoUnit.HOURS).toString(),
                "format", "ONLINE", "meetingUrl", "https://example.org/meeting");
        var responses = ConcurrentCalls.pair(
                () -> api("POST", "/api/lessons/" + a.lesson() + "/sessions", teacher, payload),
                () -> api("POST", "/api/lessons/" + b.lesson() + "/sessions", teacher, payload));
        statuses(responses, 201, 409);
        assertEquals(1L, count("SELECT count(*) FROM lesson_sessions WHERE lesson_id IN (?, ?)", a.lesson(), b.lesson()));
    }

    @Test
    void concurrentGradingRejectsStaleVersion() throws Exception {
        Fixture f = fixture(true);
        long assignment = createAssignment(f.lesson());
        Object submission = expect(api("POST", "/api/assignments/" + assignment + "/submissions", student,
                Map.of("content", "Grade me")), 201);
        long submissionId = id(submission);
        long version = longField(submission, "version");
        String path = "/api/assignments/" + assignment + "/submissions/" + submissionId + "/grade";
        var responses = ConcurrentCalls.pair(
                () -> api("PUT", path, teacher, Map.of("version", version, "score", 80, "feedback", "First")),
                () -> api("PUT", path, teacher, Map.of("version", version, "score", 95, "feedback", "Second")));
        statuses(responses, 200, 409);
        var winner = responses.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        assertEquals(intField(winner.body(), "score"), jdbc.queryForObject(
                "SELECT score FROM submissions WHERE id = ?", Integer.class, submissionId));
        assertEquals(str(field(winner.body(), "feedback")), jdbc.queryForObject(
                "SELECT feedback FROM submissions WHERE id = ?", String.class, submissionId));
        assertEquals(version + 1, jdbc.queryForObject("SELECT version FROM submissions WHERE id = ?", Long.class, submissionId));
    }

    @Test
    void concurrentCategoryUpdatesRejectStaleVersion() throws Exception {
        Object category = expect(api("POST", "/api/categories", admin,
                Map.of("name", "Race " + unique(), "description", "Category race")), 201);
        long categoryId = id(category);
        long version = longField(category, "version");
        String first = "Race first " + unique();
        String second = "Race second " + unique();
        String path = "/api/categories/" + categoryId;
        var responses = ConcurrentCalls.pair(
                () -> api("PUT", path, admin, Map.of("version", version, "name", first, "description", "First change")),
                () -> api("PUT", path, admin, Map.of("version", version, "name", second, "description", "Second change")));
        statuses(responses, 200, 409);
        var winner = responses.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        assertEquals(str(field(winner.body(), "name")), jdbc.queryForObject(
                "SELECT name FROM categories WHERE id = ?", String.class, categoryId));
        assertEquals(version + 1, jdbc.queryForObject("SELECT version FROM categories WHERE id = ?", Long.class, categoryId));
    }

    @Test
    void concurrentModuleCreationRejectsDuplicatePosition() throws Exception {
        long course = createCourse();
        String path = "/api/courses/" + course + "/modules";
        var responses = ConcurrentCalls.pair(
                () -> api("POST", path, teacher, Map.of("title", "First", "position", 1)),
                () -> api("POST", path, teacher, Map.of("title", "Second", "position", 1)));
        statuses(responses, 201, 409);
        assertEquals(1L, count("SELECT count(*) FROM modules WHERE course_id = ? AND position = 1", course));
    }

    @Test
    void concurrentResetTokenCanBeUsedOnlyOnce() throws Exception {
        String address = email();
        long user = id(createUser(address, List.of("STUDENT")));
        String originalJwt = login(address, PASSWORD);
        expect(api("POST", "/api/auth/forgot-password", null, Map.of("email", address)), 202);
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(10_000)).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertArrayEquals(new String[] {address}, message.getTo());
        var matcher = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)").matcher(message.getText());
        assertTrue(matcher.find(), "Reset link missing from captured test email");
        String token = matcher.group(1);
        var payload = Map.of("token", token, "newPassword", NEW_PASSWORD);
        var responses = ConcurrentCalls.pair(
                () -> api("POST", "/api/auth/reset-password", null, payload),
                () -> api("POST", "/api/auth/reset-password", null, payload));
        statuses(responses, 204, 400);
        assertEquals(1L, count("""
                SELECT count(*) FROM password_reset_tokens t JOIN user_accounts a ON t.user_account_id = a.id
                WHERE a.user_id = ? AND t.used_at IS NOT NULL
                """, user));
        expect(api("GET", "/api/users/me", originalJwt, null), 401);
        expect(api("POST", "/api/auth/login", null, Map.of("email", address, "password", PASSWORD)), 401);
        assertFalse(login(address, NEW_PASSWORD).isBlank());
        expect(api("POST", "/api/auth/reset-password", null, payload), 400);
    }

    @Test
    void concurrentInvalidLoginCannotExceedEmailBudget() throws Exception {
        String address = email();
        createUser(address, List.of("STUDENT"));
        List<Callable<Response>> attempts = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            attempts.add(() -> api("POST", "/api/auth/login", null,
                    Map.of("email", address, "password", "WrongPassword123!")));
        }
        var responses = ConcurrentCalls.run(attempts);
        assertEquals(12L, responses.stream().filter(r -> r.status() == 401).count());
        assertEquals(4L, responses.stream().filter(r -> r.status() == 429).count());
        for (var response : responses) {
            if (response.status() == 429) assertTrue(Long.parseLong(response.header("Retry-After")) > 0);
        }
    }

    private Object createUser(String email, List<String> roles) throws Exception {
        return expect(api("POST", "/api/users", admin, Map.of("displayName", "Race " + unique(),
                "email", email, "password", PASSWORD, "passwordChangeRequired", false, "roles", roles)), 201);
    }

    private String login(String email, String password) throws Exception {
        return str(field(expect(api("POST", "/api/auth/login", null, Map.of("email", email, "password", password)), 200), "accessToken"));
    }

    private long createCourse() throws Exception {
        return id(expect(api("POST", "/api/courses", teacher, Map.of("title", "Race " + unique(),
                "description", "Race test", "categoryId", categoryId, "teacherId", teacherId,
                "duration", 56, "startDate", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString())), 201));
    }

    private Fixture fixture(boolean enroll) throws Exception {
        long course = createCourse();
        long module = id(expect(api("POST", "/api/courses/" + course + "/modules", teacher,
                Map.of("title", "Race module", "position", 1)), 201));
        long lesson = id(expect(api("POST", "/api/courses/" + course + "/modules/" + module + "/lessons", teacher,
                Map.of("title", "Race lesson", "content", "Test content", "position", 1)), 201));
        if (enroll) expect(api("POST", "/api/courses/" + course + "/enrollments", student, null), 201);
        return new Fixture(course, module, lesson);
    }

    private long createAssignment(long lesson) throws Exception {
        return id(expect(api("POST", "/api/lessons/" + lesson + "/assignments", teacher,
                Map.of("title", "Race assignment", "description", "Test assignment", "maxScore", 100,
                        "dueAt", Instant.now().plus(10, ChronoUnit.DAYS).toString())), 201));
    }

    private QuizFixture createQuiz(long module) throws Exception {
        long quiz = id(expect(api("POST", "/api/modules/" + module + "/quiz", teacher,
                Map.of("title", "Race quiz", "timeLimit", 20)), 201));
        Object question = expect(api("POST", "/api/quizzes/" + quiz + "/questions", teacher,
                Map.of("text", "Choose one", "type", "SINGLE_CHOICE", "position", 1,
                        "options", List.of(Map.of("text", "Correct", "correct", true),
                                Map.of("text", "Wrong", "correct", false)))), 201);
        long correct = jdbc.queryForObject("SELECT id FROM answer_options WHERE question_id = ? AND is_correct = true", Long.class, id(question));
        long wrong = jdbc.queryForObject("SELECT id FROM answer_options WHERE question_id = ? AND is_correct = false", Long.class, id(question));
        return new QuizFixture(quiz, id(question), correct, wrong);
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private static String unique() { return UUID.randomUUID().toString(); }
    private static String email() { return "race." + unique() + "@courseflow.local"; }
    private static long id(Object response) { return longField(response, "id"); }
    private static long longField(Object response, String name) { return ((Number) field(response, name)).longValue(); }
    private static int intField(Object response, String name) { return ((Number) field(response, name)).intValue(); }
    private static void statuses(List<Response> responses, int... expected) {
        assertEquals(java.util.Arrays.stream(expected).sorted().boxed().toList(),
                responses.stream().map(Response::status).sorted().toList());
    }
    private record Fixture(long course, long module, long lesson) {}
    private record QuizFixture(long quiz, long question, long correct, long wrong) {}
}

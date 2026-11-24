package org.courseflow.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.service.AuthService;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

/** Opt-in local load measurements, using the existing isolated Testcontainers database. */
@Tag("performance")
@EnabledIfSystemProperty(named = "courseflow.performance", matches = "true")
class PerformanceIT extends AdvancedHttpTest {

    private static final int STUDENTS = 100;
    private static final int TEACHERS = 20;
    private static final int ADMINS = 5;
    private static final Duration SHORT = Duration.ofSeconds(10);
    private static final Duration PAUSE = Duration.ofMillis(200);

    @Autowired
    private AuthService authService;

    private final List<LoadActor> students = new ArrayList<>();
    private final List<LoadActor> teachers = new ArrayList<>();
    private final List<String> admins = new ArrayList<>();
    private final List<Long> gradebookCourses = new ArrayList<>();
    private LoadGraph reading;

    private record LoadActor(long id, String token) {}

    private record LoadGraph(long course, long module, long lesson) {}
    private Path report;

    @Override
    @BeforeAll
    protected void prepareAdvancedActors() throws Exception {
        // IDEA may disable JUnit conditions when a class is run explicitly.
        assumeTrue(Boolean.getBoolean("courseflow.performance"),
                "Enable explicitly with -Dcourseflow.performance=true");
        super.prepareAdvancedActors();
        assertEquals("courseflow_test", jdbc.queryForObject("SELECT current_database()", String.class));

        report = Path.of("build", "reports", "performance", "performance-" + unique() + ".csv")
                .toAbsolutePath();
        Files.createDirectories(report.getParent());
        Files.writeString(report,
                "scenario,operation,users,seconds,requests,successes,errors,error_pct,rps,"
                        + "avg_ms,min_ms,p50_ms,p95_ms,p99_ms,max_ms,outcomes\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("Performance report: " + report);
        System.out.println("Preparing load fixtures (not included in measurements)...");

        students.add(new LoadActor(student.id(), student.token()));
        for (int i = 1; i < STUDENTS; i++) {
            students.add(loadActor(List.of("STUDENT")));
        }
        teachers.add(new LoadActor(teacher.id(), teacher.token()));
        for (int i = 1; i < TEACHERS; i++) {
            teachers.add(loadActor(List.of("TEACHER")));
        }
        admins.add(admin);
        for (int i = 1; i < ADMINS; i++) {
            admins.add(loadActor(List.of("ADMIN")).token());
        }

        reading = graphFor(teachers.get(0));
        for (LoadActor actor : students) {
            enroll(reading.course(), actor);
        }

        // Each teacher owns a real course with committed submissions in the gradebook.
        for (int i = 0; i < TEACHERS; i++) {
            LoadActor owner = teachers.get(i);
            LoadGraph g = i == 0 ? reading : graphFor(owner);
            gradebookCourses.add(g.course());
            long assignment = id(expect(api("POST", "/api/lessons/" + g.lesson() + "/assignments",
                    owner.token(), assignmentPayload()), 201));
            for (int j = 0; j < 5; j++) {
                LoadActor actor = students.get(i * 5 + j);
                if (i != 0) {
                    enroll(g.course(), actor);
                }
                expect(api("POST", "/api/assignments/" + assignment + "/submissions",
                        actor.token(), Map.of("content", "Performance fixture")), 201);
            }
        }
        for (int i = 0; i < 10; i++) {
            course();
        }
        // Warm the main read routes and HTTP client before measurements.
        expect(api("GET", catalogPath(), student.token(), null), 200);
        expect(api("GET", lessonPath(), student.token(), null), 200);
        expect(api("GET", gradebookPath(0), teacher.token(), null), 200);
        expect(api("GET", "/api/dashboard/student", student.token(), null), 200);
        expect(api("GET", "/api/dashboard/teacher", teacher.token(), null), 200);
        expect(api("GET", "/api/dashboard/admin", admin, null), 200);
        System.out.println("Load fixtures ready: 100 students, 20 teachers, 5 administrators.");
    }

    @Test
    void catalogWithOneHundredUsers() throws Exception {
        report("catalog_100", LoadRunner.sustained(100, SHORT, PAUSE,
                (user, probe) -> call(probe, "catalog", 200, "GET", catalogPath(),
                        students.get(user).token(), null)), 100);
    }

    @Test
    void lessonsWithOneHundredUsers() throws Exception {
        report("lessons_100", LoadRunner.sustained(100, SHORT, PAUSE,
                (user, probe) -> call(probe, "lesson", 200, "GET", lessonPath(),
                        students.get(user).token(), null)), 100);
    }

    @Test
    void fiftyConcurrentEnrollments() throws Exception {
        long course = course();
        report("enrollments_50", LoadRunner.once(50, (user, probe) ->
                call(probe, "enroll", 201, "POST", "/api/courses/" + course + "/enrollments",
                        students.get(user).token(), null)), 50);
        assertEquals(50L, count("SELECT count(DISTINCT student_id) FROM enrollments "
                + "WHERE course_id = ? AND status = 'ACTIVE'", course));
    }

    @Test
    void fiftyConcurrentHomeworkSubmissions() throws Exception {
        Graph g = graph();
        long assignment = assignment(g.lesson());
        enrollFirstFifty(g.course());
        report("homework_50", LoadRunner.once(50, (user, probe) ->
                call(probe, "submit_homework", 201, "POST",
                        "/api/assignments/" + assignment + "/submissions", students.get(user).token(),
                        Map.of("content", "Performance answer " + user))), 50);
        assertEquals(50L, count("SELECT count(DISTINCT student_id) FROM submissions WHERE assignment_id = ?",
                assignment));
    }

    @Test
    void fiftyConcurrentQuizWorkflows() throws Exception {
        Graph g = graph();
        QuizGraph q = quiz(g.module());
        enrollFirstFifty(g.course());
        report("quiz_50", LoadRunner.once(50, (user, probe) -> {
            LoadActor actor = students.get(user);
            Response started = call(probe, "start_quiz", 201, "POST", attemptPath(q), actor.token(), null);
            if (started != null && started.status() == 201) {
                call(probe, "submit_quiz", 200, "POST",
                        attemptPath(q) + "/" + id(started.body()) + "/submit", actor.token(), answers(q, true));
            }
        }), 100);
        assertEquals(50L, count("SELECT count(DISTINCT student_id) FROM quiz_submissions "
                + "WHERE quiz_id = ? AND status = 'SUBMITTED' AND score = 100", q.quiz()));
    }

    @Test
    void twentyTeachersReadGradebooks() throws Exception {
        report("gradebook_20", LoadRunner.sustained(20, SHORT, PAUSE,
                (user, probe) -> call(probe, "gradebook", 200, "GET", gradebookPath(user),
                        teachers.get(user).token(), null)), 20);
    }

    @Test
    void mixedLoadWithOneHundredUsers() throws Exception {
        AtomicIntegerArray iterations = new AtomicIntegerArray(100);
        report("mixed_100", LoadRunner.sustained(100, Duration.ofSeconds(20), PAUSE, (user, probe) -> {
            int iteration = iterations.getAndIncrement(user);
            if (user < 80) {
                studentRead(probe, user, iteration);
            } else if (user < 95) {
                int index = user - 80;
                String token = teachers.get(index).token();
                if (iteration % 2 == 0) {
                    call(probe, "gradebook", 200, "GET", gradebookPath(index), token, null);
                } else {
                    call(probe, "teacher_dashboard", 200, "GET", "/api/dashboard/teacher", token, null);
                }
            } else {
                String token = admins.get(user - 95);
                if (iteration % 2 == 0) {
                    call(probe, "admin_users", 200, "GET", "/api/users?page=0&size=20", token, null);
                } else {
                    call(probe, "admin_dashboard", 200, "GET", "/api/dashboard/admin", token, null);
                }
            }
        }), 100);
        assertEquals(100L, count("SELECT count(DISTINCT student_id) FROM enrollments "
                + "WHERE course_id = ? AND status = 'ACTIVE'", reading.course()));
    }

    @Test
    void sustainedLoadWithFiftyStudents() throws Exception {
        int seconds = Integer.getInteger("courseflow.performance.soakSeconds", 300);
        assertTrue(seconds >= 1 && seconds <= 1800, "soakSeconds must be in range 1..1800");
        AtomicIntegerArray iterations = new AtomicIntegerArray(50);
        report("soak_50_" + seconds + "s", LoadRunner.sustained(50, Duration.ofSeconds(seconds),
                Duration.ofSeconds(1), (user, probe) -> studentRead(probe, user,
                        iterations.getAndIncrement(user))), 50);
        assertEquals(100L, count("SELECT count(DISTINCT student_id) FROM enrollments "
                + "WHERE course_id = ? AND status = 'ACTIVE'", reading.course()));
    }

    private LoadActor loadActor(List<String> roles) throws Exception {
        String email = email();
        Object created = expect(api("POST", "/api/users", admin, Map.of(
                "displayName", "Load-" + unique(), "email", email, "password", PASSWORD,
                "passwordChangeRequired", false, "roles", roles)), 201);
        // Fixture login only: real BCrypt, sessions and JWT, but not the per-IP HTTP limiter.
        // All timed requests still use real HTTP and normal Spring Security.
        var login = authService.login(new LoginRequest(email, PASSWORD));
        assertEquals(id(created), login.userId().longValue());
        return new LoadActor(id(created), login.accessToken());
    }

    private LoadGraph graphFor(LoadActor owner) throws Exception {
        var payload = coursePayload();
        payload.put("teacherId", owner.id());
        long course = id(expect(api("POST", "/api/courses", owner.token(), payload), 201));
        long module = id(expect(api("POST", "/api/courses/" + course + "/modules", owner.token(),
                Map.of("title", "Module", "position", 1)), 201));
        long lesson = id(expect(api("POST", "/api/courses/" + course + "/modules/" + module + "/lessons",
                owner.token(), Map.of("title", "Lesson", "content", "Performance content", "position", 1)), 201));
        return new LoadGraph(course, module, lesson);
    }

    private void enroll(long course, LoadActor actor) throws Exception {
        expect(api("POST", "/api/courses/" + course + "/enrollments", actor.token(), null), 201);
    }

    private void enrollFirstFifty(long course) throws Exception {
        for (int i = 0; i < 50; i++) {
            enroll(course, students.get(i));
        }
    }

    private String catalogPath() {
        return "/api/courses?page=0&size=20";
    }

    private String lessonPath() {
        return "/api/courses/" + reading.course() + "/modules/" + reading.module()
                + "/lessons/" + reading.lesson();
    }

    private String gradebookPath(int user) {
        return "/api/courses/" + gradebookCourses.get(user) + "/gradebook?page=0&size=20";
    }

    private void studentRead(LoadRunner.Probe probe, int user, int iteration) throws Exception {
        String token = students.get(user).token();
        switch (iteration % 5) {
            case 0 -> call(probe, "catalog", 200, "GET", catalogPath(), token, null);
            case 1 -> call(probe, "lesson", 200, "GET", lessonPath(), token, null);
            case 2 -> call(probe, "progress", 200, "GET", "/api/users/me/progress", token, null);
            case 3 -> call(probe, "student_dashboard", 200, "GET", "/api/dashboard/student", token, null);
            default -> call(probe, "repeat_enrollment", 200, "POST",
                    "/api/courses/" + reading.course() + "/enrollments", token, null);
        }
    }

    private Response call(LoadRunner.Probe probe, String operation, int expected,
                          String method, String path, String token, Object body) throws InterruptedException {
        return probe.request(operation, expected, () -> api(method, path, token, body), Response::status);
    }

    private void report(String scenario, LoadRunner.Result result, int minimumRequests) throws Exception {
        List<LoadRunner.Sample> samples = result.samples();
        appendRow(scenario, "ALL", result, samples);
        Map<String, List<LoadRunner.Sample>> byOperation = new TreeMap<>();
        for (LoadRunner.Sample sample : samples) {
            byOperation.computeIfAbsent(sample.operation(), key -> new ArrayList<>()).add(sample);
        }
        for (var entry : byOperation.entrySet()) {
            appendRow(scenario, entry.getKey(), result, entry.getValue());
        }
        assertTrue(samples.size() >= minimumRequests, "Too few completed requests: " + scenario);
        assertEquals(0L, samples.stream().filter(s -> !s.successful()).count(),
                "Unexpected HTTP statuses or request exceptions. See " + report);
    }

    private void appendRow(String scenario, String operation, LoadRunner.Result result,
                           List<LoadRunner.Sample> samples) throws Exception {
        long[] durations = samples.stream().mapToLong(LoadRunner.Sample::nanos).sorted().toArray();
        long errors = samples.stream().filter(s -> !s.successful()).count();
        double errorPct = samples.isEmpty() ? 0 : errors * 100.0 / samples.size();
        double rps = samples.size() / result.seconds();
        double avg = samples.stream().mapToDouble(s -> s.nanos() / 1_000_000.0).average().orElse(0);
        Map<String, Long> outcomes = new TreeMap<>();
        for (LoadRunner.Sample sample : samples) {
            String key = sample.exception().isEmpty()
                    ? "HTTP_" + sample.status() + "_expected_" + sample.expected()
                    : sample.exception();
            outcomes.merge(key, 1L, Long::sum);
        }
        String row = String.format(Locale.ROOT,
                "%s,%s,%d,%.3f,%d,%d,%d,%.3f,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s%n",
                scenario, operation, result.users(), result.seconds(), samples.size(), samples.size() - errors,
                errors, errorPct, rps, avg, percentile(durations, 0), percentile(durations, 0.50),
                percentile(durations, 0.95), percentile(durations, 0.99), percentile(durations, 1),
                outcomes.toString().replace(",", ";"));
        Files.writeString(report, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        if (operation.equals("ALL")) {
            System.out.printf(Locale.ROOT, "%s: users=%d requests=%d seconds=%.2f RPS=%.2f "
                            + "avg=%.2f ms p95=%.2f ms p99=%.2f ms errors=%d (%s)%n",
                    scenario, result.users(), samples.size(), result.seconds(), rps, avg,
                    percentile(durations, 0.95), percentile(durations, 0.99), errors, outcomes);
        }
    }

    private static double percentile(long[] sorted, double quantile) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(quantile * sorted.length) - 1);
        return sorted[index] / 1_000_000.0;
    }
}

package org.courseflow.performance;

import static org.courseflow.integration.support.JsonChecks.field;
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
import java.util.HashSet;
import java.util.Set;
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
    private List<Long> catalogIds;
    private final Map<String, List<Long>> teacherCourseIds = new java.util.HashMap<>();
    private final Map<String, List<Long>> studentDashboardCourseIds = new java.util.HashMap<>();
    private final List<List<Long>> gradebookAssignments = new ArrayList<>();
    private final Map<Long, Set<Long>> submittedByAssignment = new java.util.HashMap<>();

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
            List<Long> columns = new ArrayList<>();
            columns.add(assignment);
            Set<Long> submitted = new HashSet<>();
            submittedByAssignment.put(assignment, submitted);
            for (int j = 0; j < 5; j++) {
                LoadActor actor = students.get(i * 5 + j);
                if (i != 0) {
                    enroll(g.course(), actor);
                }
                expect(api("POST", "/api/assignments/" + assignment + "/submissions",
                        actor.token(), Map.of("content", "Performance fixture")), 201);
                submitted.add(actor.id());
            }
            if (i == 0) {
                for (int column = 2; column <= 10; column++) {
                    long lesson = id(expect(api("POST", "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
                            owner.token(), Map.of("title", "Load lesson " + column, "content", "Load content " + column, "position", column)), 201));
                    long task = id(expect(api("POST", "/api/lessons/" + lesson + "/assignments", owner.token(), assignmentPayload()), 201));
                    columns.add(task);
                    Set<Long> answered = new HashSet<>();
                    submittedByAssignment.put(task, answered);
                    for (int j = 0; j < 10; j++) {
                        LoadActor actor = students.get((column - 1) * 10 + j);
                        expect(api("POST", "/api/assignments/" + task + "/submissions", actor.token(), Map.of("content", "Load fixture " + column)), 201);
                        answered.add(actor.id());
                    }
                }
            }
            gradebookAssignments.add(List.copyOf(columns));
        }
        for (int i = 0; i < 80; i++) {
            course();
        }
        catalogIds = jdbc.queryForList("SELECT id FROM courses ORDER BY id LIMIT 100", Long.class);
        assertEquals(100, catalogIds.size());
        // Warm the main read routes and HTTP client before measurements.
        expect(api("GET", catalogPath(0), student.token(), null), 200);
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
                (user, probe) -> call(probe, "catalog", 200, "GET", catalogPath(user),
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
        for (LoadActor actor : students.subList(0, 50)) {
            assertEquals(1L, count("SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ? AND status = 'ACTIVE'", course, actor.id()));
        }
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
        for (int i = 0; i < 50; i++) {
            assertEquals("Performance answer " + i, jdbc.queryForObject(
                    "SELECT content FROM submissions WHERE assignment_id = ? AND student_id = ?", String.class, assignment, students.get(i).id()));
        }
    }

    @Test
    void fiftyConcurrentQuizWorkflows() throws Exception {
        Graph g = graph();
        QuizGraph q = quiz(g.module());
        List<Map<String, Object>> selections = new ArrayList<>();
        selections.add(Map.of("questionId", q.question(), "optionIds", List.of(q.correct())));
        for (int i = 2; i <= 50; i++) {
            long question = id(expect(api("POST", "/api/quizzes/" + q.quiz() + "/questions", teacher.token(), questionPayload(i, 2)), 201));
            long option = jdbc.queryForObject("SELECT id FROM answer_options WHERE question_id = ? AND is_correct", Long.class, question);
            selections.add(Map.of("questionId", question, "optionIds", List.of(option)));
        }
        Map<String, Object> answerBody = Map.of("answers", List.copyOf(selections));
        enrollFirstFifty(g.course());
        report("quiz_50", LoadRunner.once(50, (user, probe) -> {
            LoadActor actor = students.get(user);
            Response started = call(probe, "start_quiz", 201, "POST", attemptPath(q), actor.token(), null);
            if (started != null && started.status() == 201) {
                call(probe, "submit_quiz", 200, "POST",
                        attemptPath(q) + "/" + id(started.body()) + "/submit", actor.token(), answerBody);
            }
        }), 100);
        assertEquals(50L, count("SELECT count(DISTINCT student_id) FROM quiz_submissions "
                + "WHERE quiz_id = ? AND status = 'SUBMITTED' AND score = 100", q.quiz()));
        assertEquals(2500L, count("SELECT count(*) FROM quiz_answer_selections a JOIN quiz_submissions s ON s.id = a.quiz_submission_id JOIN answer_options o ON o.id = a.answer_option_id WHERE s.quiz_id = ? AND o.is_correct", q.quiz()));
        for (LoadActor actor : students.subList(0, 50)) {
            assertEquals(50L, count("SELECT count(*) FROM quiz_answer_selections a JOIN quiz_submissions s ON s.id = a.quiz_submission_id WHERE s.quiz_id = ? AND s.student_id = ?", q.quiz(), actor.id()));
        }
    }

    @Test
    void twentyTeachersReadGradebooks() throws Exception {
        report("gradebook_20", LoadRunner.sustained(20, SHORT, PAUSE,
                (user, probe) -> call(probe, "gradebook", 200, "GET", gradebookPath(user),
                        teachers.get(user).token(), null)), 20);
    }

    @Test
    void mixedLoadWithOneHundredUsers() throws Exception {
        for (LoadActor owner : teachers) {
            teacherCourseIds.put(owner.token(), jdbc.queryForList(
                    "SELECT id FROM courses WHERE teacher_id = ? ORDER BY title, id LIMIT 5", Long.class, owner.id()));
        }
        captureStudentDashboardCourses();
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
    void mixedReadAndWriteBurstWithOneHundredUsers() throws Exception {
        Graph writing = graph();
        long assignment = assignment(writing.lesson());
        enrollFirstFifty(writing.course());
        List<Long> gradingAssignments = new ArrayList<>();
        List<Long> gradingSubmissions = new ArrayList<>();
        List<Long> gradingVersions = new ArrayList<>();
        for (int i = 0; i < TEACHERS; i++) {
            LoadActor owner = teachers.get(i);
            LoadActor learner = students.get(80 + i);
            LoadGraph g = graphFor(owner);
            long task = id(expect(api("POST", "/api/lessons/" + g.lesson() + "/assignments",
                    owner.token(), assignmentPayload()), 201));
            enroll(g.course(), learner);
            Object submission = expect(api("POST", "/api/assignments/" + task + "/submissions",
                    learner.token(), Map.of("content", "Grade fixture " + i)), 201);
            gradingAssignments.add(task);
            gradingSubmissions.add(id(submission));
            gradingVersions.add(number(submission, "version"));
        }
        report("mixed_writes_100", LoadRunner.once(100, (user, probe) -> {
            if (user < 50) {
                call(probe, "submit_homework", 201, "POST", "/api/assignments/" + assignment + "/submissions",
                        students.get(user).token(), Map.of("content", "Concurrent write " + user));
            } else if (user < 80) {
                call(probe, "lesson", 200, "GET", lessonPath(), students.get(user).token(), null);
            } else {
                int index = user - 80;
                call(probe, "grade_homework", 200, "PUT", "/api/assignments/" + gradingAssignments.get(index)
                        + "/submissions/" + gradingSubmissions.get(index) + "/grade", teachers.get(index).token(),
                        Map.of("version", gradingVersions.get(index), "score", 60 + index, "feedback", "Grade " + index));
            }
        }), 100);
        assertEquals(50L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment));
        for (int i = 0; i < 50; i++) {
            assertEquals("Concurrent write " + i, jdbc.queryForObject("SELECT content FROM submissions WHERE assignment_id = ? AND student_id = ?",
                    String.class, assignment, students.get(i).id()));
        }
        for (int i = 0; i < TEACHERS; i++) {
            assertEquals(60 + i, jdbc.queryForObject("SELECT score FROM submissions WHERE id = ?", Integer.class, gradingSubmissions.get(i)));
            assertEquals("Grade " + i, jdbc.queryForObject("SELECT feedback FROM submissions WHERE id = ?", String.class, gradingSubmissions.get(i)));
            assertEquals(gradingVersions.get(i) + 1, jdbc.queryForObject("SELECT version FROM submissions WHERE id = ?", Long.class, gradingSubmissions.get(i)));
        }
    }

    @Test
    void sustainedLoadWithFiftyStudents() throws Exception {
        int seconds = Integer.getInteger("courseflow.performance.soakSeconds", 300);
        assertTrue(seconds >= 1 && seconds <= 1800, "soakSeconds must be in range 1..1800");
        captureStudentDashboardCourses();
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

    private String catalogPath(int user) {
        return "/api/courses?page=" + (user % 5) + "&size=20&sortBy=id&direction=asc";
    }

    private String lessonPath() {
        return "/api/courses/" + reading.course() + "/modules/" + reading.module()
                + "/lessons/" + reading.lesson();
    }

    private String gradebookPath(int user) {
        return "/api/courses/" + gradebookCourses.get(user) + "/gradebook?page=0&size=" + (user == 0 ? 100 : 20);
    }

    private void studentRead(LoadRunner.Probe probe, int user, int iteration) throws Exception {
        String token = students.get(user).token();
        switch (iteration % 5) {
            case 0 -> call(probe, "catalog", 200, "GET", catalogPath(user), token, null);
            case 1 -> call(probe, "lesson", 200, "GET", lessonPath(), token, null);
            case 2 -> call(probe, "progress", 200, "GET", "/api/users/me/progress?includeDetails=false&courseId=" + reading.course(), token, null);
            case 3 -> call(probe, "student_dashboard", 200, "GET", "/api/dashboard/student", token, null);
            default -> call(probe, "repeat_enrollment", 200, "POST",
                    "/api/courses/" + reading.course() + "/enrollments", token, null);
        }
    }

    private Response call(LoadRunner.Probe probe, String operation, int expected,
                          String method, String path, String token, Object body) throws InterruptedException {
        return probe.request(operation, expected, () -> api(method, path, token, body), Response::status,
                response -> validateResponse(operation, path, token, body, response.body()));
    }

    private void captureStudentDashboardCourses() {
        for (LoadActor actor : students) {
            studentDashboardCourseIds.put(actor.token(), jdbc.queryForList("""
                    SELECT c.id FROM courses c JOIN enrollments e ON e.course_id = c.id
                    WHERE e.student_id = ? AND e.status = 'ACTIVE' AND c.status = 'ACTIVE'
                    ORDER BY c.title, c.id LIMIT 5
                    """, Long.class, actor.id()));
        }
    }

    private void validateResponse(String operation, String path, String token, Object request, Object body) {
        switch (operation) {
            case "catalog" -> {
                int page = Integer.parseInt(path.substring(path.indexOf("page=") + 5, path.indexOf('&')));
                assertEquals(page, number(body, "page"));
                assertEquals(catalogIds.subList(page * 20, page * 20 + 20), rows(body).stream().map(PerformanceIT::id).toList());
            }
            case "lesson" -> {
                assertEquals(reading.lesson(), id(body));
                assertEquals(reading.module(), number(body, "moduleId"));
                assertEquals("Performance content", field(body, "content"));
            }
            case "progress" -> {
                List<?> courses = rows(body);
                assertEquals(1, courses.size());
                assertEquals(reading.course(), number(courses.get(0), "courseId"));
                assertEquals("ACTIVE", field(courses.get(0), "enrollmentStatus"));
                assertEquals(10L, number(courses.get(0), "totalAssessments"));
            }
            case "student_dashboard" -> {
                assertTrue(number(body, "activeCourseCount") >= 1);
                assertEquals(studentDashboardCourseIds.get(token), rows(field(body, "courses")).stream()
                        .map(c -> number(c, "courseId")).toList());
                assertTrue(field(body, "upcomingDeadlines") instanceof List<?>);
            }
            case "enroll", "repeat_enrollment" -> {
                assertEquals(studentId(token), number(body, "studentId"));
                assertEquals(pathId(path, 3), number(body, "courseId"));
                assertEquals("ACTIVE", field(body, "status"));
                assertTrue(id(body) > 0);
            }
            case "submit_homework" -> {
                assertEquals(studentId(token), number(body, "studentId"));
                assertEquals(pathId(path, 3), number(body, "assignmentId"));
                assertEquals(field(request, "content"), field(body, "content"));
                assertEquals("SUBMITTED", field(body, "status"));
                assertTrue(id(body) > 0);
            }
            case "grade_homework" -> {
                assertEquals(pathId(path, 3), number(body, "assignmentId"));
                assertEquals(pathId(path, 5), id(body));
                assertEquals(number(request, "score"), number(body, "score"));
                assertEquals(field(request, "feedback"), field(body, "feedback"));
                assertEquals(number(request, "version") + 1, number(body, "version"));
                assertEquals("GRADED", field(body, "status"));
            }
            case "start_quiz", "submit_quiz" -> {
                assertEquals(studentId(token), number(body, "studentId"));
                assertEquals(pathId(path, 3), number(body, "quizId"));
                assertEquals(1L, number(body, "attemptNumber"));
                assertTrue(id(body) > 0);
                if (operation.equals("start_quiz")) {
                    assertEquals("IN_PROGRESS", field(body, "status"));
                } else {
                    assertEquals(pathId(path, 5), id(body));
                    assertEquals("SUBMITTED", field(body, "status"));
                    assertEquals(100L, number(body, "score"));
                }
            }
            case "gradebook" -> {
                long course = pathId(path, 3);
                int owner = gradebookCourses.indexOf(course);
                assertTrue(owner >= 0);
                assertEquals(course, number(body, "courseId"));
                List<Long> expectedColumns = gradebookAssignments.get(owner);
                Object columns = field(body, "columns");
                assertTrue(columns instanceof List<?>);
                assertEquals(expectedColumns, ((List<?>) columns).stream().map(PerformanceIT::id).toList());
                List<?> rows = rows(field(body, "students"));
                assertEquals(owner == 0 ? 100 : 5, rows.size());
                Set<Long> distinct = new HashSet<>();
                for (Object row : rows) {
                    long studentId = number(row, "studentId");
                    assertTrue(distinct.add(studentId));
                    assertTrue((owner == 0 ? students : students.subList(owner * 5, owner * 5 + 5)).stream().anyMatch(a -> a.id() == studentId));
                    Object cells = field(row, "results");
                    assertTrue(cells instanceof List<?> list && list.size() == expectedColumns.size());
                    for (int i = 0; i < expectedColumns.size(); i++) {
                        Object cell = ((List<?>) cells).get(i);
                        long assessment = expectedColumns.get(i);
                        assertEquals(assessment, number(cell, "assessmentId"));
                        assertEquals("ASSIGNMENT", field(cell, "type"));
                        assertEquals(submittedByAssignment.get(assessment).contains(studentId) ? "SUBMITTED" : "NOT_SUBMITTED", field(cell, "status"));
                    }
                }
            }
            case "teacher_dashboard" -> {
                int owner = -1;
                for (int i = 0; i < teachers.size(); i++) {
                    if (teachers.get(i).token().equals(token)) owner = i;
                }
                assertTrue(owner >= 0);
                assertTrue(number(body, "totalCourseCount") >= 1);
                assertTrue(number(body, "activeStudentCount") >= 5);
                Object courses = field(body, "courses");
                assertTrue(courses instanceof List<?>);
                assertEquals(teacherCourseIds.get(token), ((List<?>) courses).stream().map(c -> number(c, "courseId")).toList());
            }
            case "admin_users" -> {
                assertEquals(20, rows(body).size());
                assertTrue(number(body, "totalElements") >= STUDENTS + TEACHERS + ADMINS);
                assertEquals(20L, rows(body).stream().map(PerformanceIT::id).distinct().count());
            }
            case "admin_dashboard" -> {
                Object users = field(body, "users");
                assertTrue(number(users, "active") >= STUDENTS + TEACHERS + ADMINS);
                assertEquals(number(users, "total"), number(users, "active") + number(users, "inactive") + number(users, "deleted"));
                assertTrue(number(field(body, "courses"), "active") >= 20);
            }
            default -> throw new AssertionError("Missing response validator for " + operation);
        }
    }

    private static List<?> rows(Object page) {
        Object content = field(page, "content");
        if (!(content instanceof List<?> list)) throw new AssertionError("Expected page content");
        return list;
    }

    private long studentId(String token) {
        return students.stream().filter(a -> a.token().equals(token)).findFirst().orElseThrow().id();
    }

    private static long pathId(String path, int index) {
        return Long.parseLong(path.split("/")[index]);
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
                "Unexpected HTTP statuses, invalid response bodies or request exceptions. See " + report);
        assertEquals((long) result.users(), samples.stream().map(LoadRunner.Sample::user).distinct().count(),
                "Every virtual user must execute at least one request");
        for (var entry : byOperation.entrySet()) {
            String operation = entry.getKey();
            boolean heavy = operation.equals("submit_quiz") || operation.equals("start_quiz") || operation.equals("gradebook");
            long p95Limit = Long.getLong("courseflow.performance." + (heavy ? "heavyP95Ms" : "p95Ms"), heavy ? 2000L : 500L);
            long p99Limit = Long.getLong("courseflow.performance." + (heavy ? "heavyP99Ms" : "p99Ms"), heavy ? 4000L : 1000L);
            long[] sorted = entry.getValue().stream().mapToLong(LoadRunner.Sample::nanos).sorted().toArray();
            assertTrue(p95Limit > 0 && p99Limit >= p95Limit, "Invalid performance thresholds");
            assertTrue(percentile(sorted, 0.95) <= p95Limit, scenario + "/" + operation + " p95 exceeds " + p95Limit + " ms");
            assertTrue(percentile(sorted, 0.99) <= p99Limit, scenario + "/" + operation + " p99 exceeds " + p99Limit + " ms");
        }
        double minimumRps = Double.parseDouble(System.getProperty("courseflow.performance.minRps." + scenario,
                scenario.startsWith("soak_") ? "20" : scenario.equals("gradebook_20") ? "40" :
                        scenario.equals("catalog_100") || scenario.equals("lessons_100") || scenario.equals("mixed_100") ? "150" : "0"));
        assertTrue(Double.isFinite(minimumRps) && minimumRps >= 0, "Invalid minimum RPS");
        assertTrue(samples.size() / result.seconds() >= minimumRps, scenario + " throughput below " + minimumRps + " RPS");
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

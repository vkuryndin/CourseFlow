package org.courseflow.integration.concurrency;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.courseflow.integration.support.ConcurrentCalls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Separate authenticated users, separate HTTP requests and committed transactions. */
class MultiUserConcurrencyIT extends AdvancedHttpTest {
    private final List<Actor> students = new ArrayList<>();

    @BeforeAll
    void createStudents() throws Exception {
        students.add(student);
        for (int i = 1; i < 20; i++) {
            students.add(actor(List.of("STUDENT")));
        }
    }

    @Test
    void twentyStudentsEnrollInTheSameCourse() throws Exception {
        long course = course();
        enrollAll(course);
        assertEquals(20L, count("SELECT count(DISTINCT student_id) FROM enrollments WHERE course_id = ? AND status = 'ACTIVE'", course));
    }

    @Test
    void twentyStudentsSubmitIndependentHomework() throws Exception {
        Graph g = graph();
        enrollAll(g.course());
        long assignment = assignment(g.lesson());
        submitAll(assignment);
        assertEquals(20L, count("SELECT count(DISTINCT student_id) FROM submissions WHERE assignment_id = ?", assignment));
        for (Actor actor : students) {
            assertEquals("Answer " + actor.id(), jdbc.queryForObject(
                    "SELECT content FROM submissions WHERE assignment_id = ? AND student_id = ?",
                    String.class, assignment, actor.id()));
        }
    }

    @Test
    void twentyStudentsStartTheirOwnQuizAttempts() throws Exception {
        Graph g = graph();
        enrollAll(g.course());
        QuizGraph q = quiz(g.module());
        startAll(q);
        assertEquals(20L, count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND attempt_number = 1 AND status = 'IN_PROGRESS'", q.quiz()));
        assertEquals(20L, count("SELECT count(DISTINCT student_id) FROM quiz_submissions WHERE quiz_id = ?", q.quiz()));
    }

    @Test
    void twentyStudentsSubmitQuizWithDifferentAnswers() throws Exception {
        Graph g = graph();
        enrollAll(g.course());
        QuizGraph q = quiz(g.module());
        List<Long> attempts = startAll(q);
        List<Callable<Response>> calls = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            Actor actor = students.get(i);
            long attempt = attempts.get(i);
            boolean correct = i % 2 == 0;
            calls.add(() -> api("POST", attemptPath(q) + "/" + attempt + "/submit", actor.token(), answers(q, correct)));
        }
        for (Response response : parallel(calls)) expect(response, 200);
        assertEquals(20L, count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'SUBMITTED'", q.quiz()));
        assertEquals(10L, count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND score = 100", q.quiz()));
        assertEquals(10L, count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND score = 0", q.quiz()));
        for (int i = 0; i < attempts.size(); i++) {
            assertEquals(i % 2 == 0 ? q.correct() : q.wrong(), jdbc.queryForObject(
                    "SELECT answer_option_id FROM quiz_answer_selections WHERE quiz_submission_id = ?", Long.class, attempts.get(i)));
        }
    }

    @Test
    void twentyStudentsCreateIndependentReviews() throws Exception {
        long course = course();
        enrollAll(course);
        List<Callable<Response>> calls = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            Actor actor = students.get(i);
            int rating = 1 + i % 5;
            calls.add(() -> api("POST", "/api/courses/" + course + "/reviews", actor.token(),
                    Map.of("rating", rating, "comment", "Review " + actor.id())));
        }
        for (Response response : parallel(calls)) expect(response, 201);
        assertEquals(20L, count("SELECT count(DISTINCT student_id) FROM course_reviews WHERE course_id = ?", course));
        assertEquals(60L, jdbc.queryForObject("SELECT sum(rating) FROM course_reviews WHERE course_id = ?", Long.class, course));
    }

    @Test
    void teacherAndAdministratorCannotOverwriteSameCourseVersion() throws Exception {
        long course = course();
        String path = "/api/courses/" + course;
        long version = number(expect(api("GET", path, admin, null), 200), "version");
        var first = coursePayload();
        first.put("version", version);
        first.put("title", "Teacher update");
        var second = coursePayload();
        second.put("version", version);
        second.put("title", "Administrator update");
        var responses = ConcurrentCalls.pair(() -> api("PUT", path, teacher.token(), first),
                () -> api("PUT", path, admin, second));
        statuses(responses, 200, 409);
        Object winner = responses.stream().filter(r -> r.status() == 200).findFirst().orElseThrow().body();
        assertEquals(field(winner, "title"), jdbc.queryForObject("SELECT title FROM courses WHERE id = ?", String.class, course));
        assertEquals(version + 1, jdbc.queryForObject("SELECT version FROM courses WHERE id = ?", Long.class, course));
    }

    @Test
    void teacherAndAdministratorGradeTheSameSubmission() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        long assignment = assignment(g.lesson());
        Object submission = expect(api("POST", "/api/assignments/" + assignment + "/submissions", student.token(), Map.of("content", "Answer")), 201);
        long id = id(submission);
        long version = number(submission, "version");
        String path = "/api/assignments/" + assignment + "/submissions/" + id + "/grade";
        var responses = ConcurrentCalls.pair(
                () -> api("PUT", path, teacher.token(), Map.of("version", version, "score", 35)),
                () -> api("PUT", path, admin, Map.of("version", version, "score", 85)));
        statuses(responses, 200, 409);
        Object winner = responses.stream().filter(r -> r.status() == 200).findFirst().orElseThrow().body();
        assertEquals(number(winner, "score"), jdbc.queryForObject("SELECT score FROM submissions WHERE id = ?", Long.class, id));
    }

    @Test
    void studentReadsOwnSubmissionWhileTeacherGradesIt() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        long assignment = assignment(g.lesson());
        String path = "/api/assignments/" + assignment + "/submissions";
        Object submission = expect(api("POST", path, student.token(), Map.of("content", "Answer")), 201);
        var results = ConcurrentCalls.pair(
                () -> api("PUT", path + "/" + id(submission) + "/grade", teacher.token(), Map.of("version", number(submission, "version"), "score", 90, "feedback", "Graded")),
                () -> api("GET", path + "/me", student.token(), null));
        expect(results.get(0), 200);
        Object read = expect(results.get(1), 200);
        assertEquals(id(submission), id(read));
        if (field(read, "score") instanceof Number score) {
            assertEquals(90, score.intValue());
            assertEquals("Graded", field(read, "feedback"));
            assertEquals("GRADED", field(read, "status"));
        } else {
            assertEquals("SUBMITTED", field(read, "status"));
        }
        assertEquals(90L, number(expect(api("GET", path + "/me", student.token(), null), 200), "score"));
    }

    @Test
    void administratorDeactivationRacingWithStudentEnrollment() throws Exception {
        Actor target = actor(List.of("STUDENT"));
        long course = course();
        var responses = ConcurrentCalls.pair(
                () -> api("POST", "/api/users/" + target.id() + "/deactivate", admin, null),
                () -> api("POST", "/api/courses/" + course + "/enrollments", target.token(), null));
        expect(responses.get(0), 200);
        int status = responses.get(1).status();
        assertTrue(List.of(201, 401, 403, 409).contains(status), "Unexpected enrollment status " + status);
        assertEquals(status == 201 ? 1L : 0L, count("SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, target.id()));
        assertEquals("INACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, target.id()));
        expect(api("GET", "/api/users/me", target.token(), null), 401);
    }

    @Test
    void deletingAssignmentAndSubmittingCannotBothSucceed() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        long assignment = assignment(g.lesson());
        var responses = ConcurrentCalls.pair(
                () -> api("DELETE", "/api/lessons/" + g.lesson() + "/assignments/" + assignment, teacher.token(), null),
                () -> api("POST", "/api/assignments/" + assignment + "/submissions", student.token(), Map.of("content", "Answer")));
        if (responses.get(0).status() == 204) {
            expect(responses.get(1), 404);
            assertEquals(0L, count("SELECT count(*) FROM assignments WHERE id = ?", assignment));
            assertEquals(0L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment));
        } else {
            expect(responses.get(0), 409);
            expect(responses.get(1), 201);
            assertEquals(1L, count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment));
        }
    }

    @Test
    void archivingCourseWhileStudentsEnrollPreservesEveryCommittedEnrollment() throws Exception {
        long course = course();
        List<Callable<Response>> tasks = new ArrayList<>();
        tasks.add(() -> api("POST", "/api/courses/" + course + "/archive", admin, null));
        for (Actor actor : students) {
            tasks.add(() -> api("POST", "/api/courses/" + course + "/enrollments", actor.token(), null));
        }
        var responses = parallel(tasks);
        expect(responses.get(0), 200);
        long accepted = 0;
        for (int i = 1; i < responses.size(); i++) {
            int status = responses.get(i).status();
            assertTrue(status == 201 || status == 409, "Unexpected enrollment status " + status);
            long actor = students.get(i - 1).id();
            assertEquals(status == 201 ? 1L : 0L, count("SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, actor));
            if (status == 201) accepted++;
        }
        assertEquals(accepted, count("SELECT count(*) FROM enrollments WHERE course_id = ?", course));
        assertEquals("ARCHIVED", jdbc.queryForObject("SELECT status FROM courses WHERE id = ?", String.class, course));
        expect(api("POST", "/api/courses/" + course + "/enrollments", student.token(), null), 409);
    }

    @Test
    void quizEditRacingWithAttemptStartNeverChangesQuizAfterAttempt() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        QuizGraph q = quiz(g.module());
        String path = "/api/quizzes/" + q.quiz();
        long version = number(expect(api("GET", path, teacher.token(), null), 200), "version");
        var responses = ConcurrentCalls.pair(
                () -> api("PUT", path, teacher.token(), Map.of("title", "New title", "timeLimit", 40, "maxAttempts", 3, "version", version)),
                () -> api("POST", attemptPath(q), student.token(), null));
        expect(responses.get(1), 201);
        int status = responses.get(0).status();
        assertTrue(status == 200 || status == 409);
        long attempt = id(responses.get(1).body());
        assertEquals(status == 200 ? 40L : 20L, jdbc.queryForObject(
                "SELECT CAST(EXTRACT(EPOCH FROM (expires_at-taken_at))/60 AS bigint) FROM quiz_submissions WHERE id = ?", Long.class, attempt));
        Object before = expect(api("GET", path, teacher.token(), null), 200);
        Object conflict = expect(api("PUT", path, teacher.token(), Map.of("title", "Forbidden edit",
                "timeLimit", 20, "maxAttempts", 2, "version", number(before, "version"))), 409);
        assertEquals("BUSINESS_CONFLICT", field(conflict, "code"));
        assertEquals("Cannot modify or delete Quiz after student attempts", field(conflict, "message"));
        assertEquals(before, expect(api("GET", path, teacher.token(), null), 200));
    }

    @Test
    void oneStudentCannotReadAnotherStudentsAttemptDuringSubmission() throws Exception {
        Graph g = graph();
        Actor other = students.get(1);
        enroll(g.course(), student);
        enroll(g.course(), other);
        QuizGraph q = quiz(g.module());
        long attempt = start(q, student);
        String path = attemptPath(q) + "/" + attempt;
        var responses = ConcurrentCalls.pair(() -> api("POST", path + "/submit", student.token(), answers(q, true)),
                () -> api("GET", path, other.token(), null));
        Object completed = expect(responses.get(0), 200);
        assertEquals(attempt, id(completed));
        assertEquals(100L, number(completed, "score"));
        expect(responses.get(1), 404);
        expect(api("GET", path + "/result", other.token(), null), 404);
    }

    @Test
    void studentWithdrawAndOtherStudentEnrollmentDoNotInterfere() throws Exception {
        long course = course();
        Actor other = students.get(1);
        enroll(course, student);
        String path = "/api/courses/" + course + "/enrollments";
        var responses = ConcurrentCalls.pair(() -> api("DELETE", path + "/me", student.token(), null),
                () -> api("POST", path, other.token(), null));
        expect(responses.get(0), 204);
        assertEquals(other.id(), number(expect(responses.get(1), 201), "studentId"));
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT status FROM enrollments WHERE course_id = ? AND student_id = ?", String.class, course, student.id()));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM enrollments WHERE course_id = ? AND student_id = ?", String.class, course, other.id()));
    }

    @Test
    void unrelatedStudentCannotGradeWhileTeacherGrades() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        long assignment = assignment(g.lesson());
        String path = "/api/assignments/" + assignment + "/submissions";
        Object submission = expect(api("POST", path, student.token(), Map.of("content", "Answer")), 201);
        String grade = path + "/" + id(submission) + "/grade";
        long version = number(submission, "version");
        var responses = ConcurrentCalls.pair(
                () -> api("PUT", grade, teacher.token(), Map.of("version", version, "score", 70, "feedback", "Teacher")),
                () -> api("PUT", grade, students.get(1).token(), Map.of("version", version, "score", 12, "feedback", "Forbidden")));
        Object graded = expect(responses.get(0), 200);
        assertEquals(70L, number(graded, "score"));
        assertEquals("Teacher", field(graded, "feedback"));
        expect(responses.get(1), 403);
        assertEquals(version + 1, jdbc.queryForObject("SELECT version FROM submissions WHERE id = ?", Long.class, id(submission)));
        assertEquals("Teacher", jdbc.queryForObject("SELECT feedback FROM submissions WHERE id = ?", String.class, id(submission)));
        assertEquals(70, jdbc.queryForObject("SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)));
    }

    private void enrollAll(long course) throws Exception {
        List<Callable<Response>> calls = new ArrayList<>();
        for (Actor actor : students) {
            calls.add(() -> api("POST", "/api/courses/" + course + "/enrollments", actor.token(), null));
        }
        List<Response> responses = parallel(calls);
        for (int i = 0; i < responses.size(); i++) {
            expect(responses.get(i), 201);
            assertEquals(students.get(i).id(), number(responses.get(i).body(), "studentId"));
        }
    }

    private void submitAll(long assignment) throws Exception {
        List<Callable<Response>> calls = new ArrayList<>();
        for (Actor actor : students) {
            calls.add(() -> api("POST", "/api/assignments/" + assignment + "/submissions", actor.token(), Map.of("content", "Answer " + actor.id())));
        }
        for (Response response : parallel(calls)) expect(response, 201);
    }

    private List<Long> startAll(QuizGraph quiz) throws Exception {
        List<Callable<Response>> calls = new ArrayList<>();
        for (Actor actor : students) {
            calls.add(() -> api("POST", attemptPath(quiz), actor.token(), null));
        }
        var responses = parallel(calls);
        List<Long> attempts = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            Object body = expect(responses.get(i), 201);
            assertEquals(students.get(i).id(), number(body, "studentId"));
            attempts.add(id(body));
        }
        return attempts;
    }
}

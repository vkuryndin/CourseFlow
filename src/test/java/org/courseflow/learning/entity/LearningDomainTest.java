package org.courseflow.learning.entity;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.assignment;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class LearningDomainTest {

    @Test
    void enrollmentLifecyclePreservesOriginalDate() {
        var user = student();
        var course = course();
        var e = Enrollment.createForStudent(user, course, NOW);

        assertSame(user, e.getStudent());
        assertSame(course, e.getCourse());
        assertEquals(EnrollmentStatus.ACTIVE, e.getStatus());
        e.withdraw(NOW.plusSeconds(10));
        e.withdraw(NOW.plusSeconds(20));
        assertEquals(EnrollmentStatus.WITHDRAWN, e.getStatus());
        assertEquals(NOW.plusSeconds(10), e.getEndedAt());
        e.reactivate();
        assertEquals(EnrollmentStatus.ACTIVE, e.getStatus());
        assertNull(e.getEndedAt());
        assertEquals(NOW, e.getEnrolledAt());
    }

    @Test
    void extendingDeadlinePreservesDefinitionAndGrade() {
        Assignment a = assignment();
        Submission s = new Submission(a, student(), "Work", NOW);
        s.grade(80, "Good");
        Instant extension = a.getDueAt().plusSeconds(1);
        a.extendDeadline(extension);

        assertEquals(extension, a.getDueAt());
        assertEquals("Task", a.getTitle());
        assertEquals("Implement", a.getDescription());
        assertEquals(100, a.getMaxScore());
        assertEquals(80, s.getScore());
    }

    @Test
    void rejectDeadlineShorteningOrEqualityOrNull() {
        Assignment a = assignment();
        Instant due = a.getDueAt();

        assertThrows(IllegalArgumentException.class, () -> a.extendDeadline(null));
        assertThrows(IllegalArgumentException.class, () -> a.extendDeadline(due));
        assertThrows(IllegalArgumentException.class, () -> a.extendDeadline(due.minusSeconds(1)));
        assertEquals(due, a.getDueAt());
    }

    @Test
    void assignmentDefinitionCanBeUpdated() {
        Assignment a = assignment();
        a.updateDetails("New", "Text", NOW, 50);

        assertEquals("New", a.getTitle());
        assertEquals("Text", a.getDescription());
        assertEquals(NOW, a.getDueAt());
        assertEquals(50, a.getMaxScore());
    }

    @Test
    void submissionInitialStateAndRegrading() {
        Assignment a = assignment();
        var student = student();
        Submission s = new Submission(a, student, "Work", NOW);

        assertSame(a, s.getAssignment());
        assertSame(student, s.getStudent());
        assertEquals("Work", s.getContent());
        assertEquals(NOW, s.getSubmittedAt());
        assertEquals(SubmissionStatus.SUBMITTED, s.getStatus());
        assertNull(s.getScore());
        s.grade(0, "First");
        assertEquals(SubmissionStatus.GRADED, s.getStatus());
        assertEquals(0, s.getScore());
        s.grade(100, "Final");
        assertEquals(100, s.getScore());
        assertEquals("Final", s.getFeedback());
    }

    @Test
    void invalidGradeDoesNotOverwriteExistingGrade() {
        Submission s = new Submission(assignment(), student(), "Work", NOW);
        s.grade(50, "Keep");
        for (Integer score : Arrays.asList(null, -1, 101)) {
            assertThrows(IllegalArgumentException.class, () -> s.grade(score, "Invalid"));
        }
        assertEquals(50, s.getScore());
        assertEquals("Keep", s.getFeedback());
    }
}

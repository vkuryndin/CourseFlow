package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.QueryMocks.count;
import static org.courseflow.testing.QueryMocks.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.MyAssignmentResponse;
import org.courseflow.learning.dto.MyQuizAttemptResponse;
import org.courseflow.learning.dto.MySubmissionResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.junit.jupiter.api.Test;

class StudentLearningServiceTest {
  private final EntityManager em = mock(EntityManager.class);
  private final StudentLearningService service = new StudentLearningService(em, CLOCK);

  @Test
  void assignmentsApplyActiveEnrollmentCourseAndStatusFilters() {
    var row =
        new MyAssignmentResponse(
            70L, 60L, "Lesson", 40L, "ORM", "Task", "Text", NOW, 100, null, "OVERDUE", null);
    var data =
        query(
            em,
            MyAssignmentResponse.class,
            List.of(row),
            "studentId",
            "courseId",
            "now",
            "courseStatus",
            "enrollmentStatus",
            "status");
    var total =
        count(em, 21, "studentId", "courseId", "now", "courseStatus", "enrollmentStatus", "status");

    var result = service.getAssignments(10L, 40L, MyAssignmentResponse.Status.OVERDUE, 2, 10);

    assertEquals(List.of(row), result.content());
    assertEquals(21L, result.totalElements());
    verify(data).setFirstResult(20);
    verify(data).setMaxResults(10);
    verify(data).setParameter("studentId", 10L);
    verify(data).setParameter("courseStatus", CourseStatus.ACTIVE);
    verify(data).setParameter("enrollmentStatus", EnrollmentStatus.ACTIVE);
    verify(data).setParameter("status", "OVERDUE");
    verify(total).setParameter("courseId", 40L);
    verify(em).createQuery(contains("a.dueAt <= :now"), eq(MyAssignmentResponse.class));
  }

  @Test
  void submissionHistoryIncludesWithdrawnCoursesAndBindsEnum() {
    var row =
        new MySubmissionResponse(
            1L, 70L, "Task", 40L, "ORM", "Answer", NOW, SubmissionStatus.GRADED, 90, 100, "Good");
    var data = query(em, MySubmissionResponse.class, List.of(row), "studentId", "status");
    count(em, 1, "studentId", "status");

    assertEquals(
        List.of(row), service.getSubmissions(10L, null, SubmissionStatus.GRADED, 0, 20).content());
    verify(data).setParameter("status", SubmissionStatus.GRADED);
    verify(em)
        .createQuery(
            contains("ORDER BY s.submittedAt DESC, s.id DESC"), eq(MySubmissionResponse.class));
  }

  @Test
  void attemptHistoryCalculatesExpiryAtOneInstant() {
    var data =
        query(em, MyQuizAttemptResponse.class, List.of(), "studentId", "now", "courseId", "status");
    count(em, 0, "studentId", "now", "courseId", "status");

    assertTrue(
        service.getQuizAttempts(10L, 40L, QuizSubmissionStatus.EXPIRED, 0, 20).content().isEmpty());
    verify(data).setParameter("now", NOW);
    verify(data).setParameter("status", "EXPIRED");
    verify(em).createQuery(contains("s.expiresAt <= :now"), eq(MyQuizAttemptResponse.class));
  }

  @Test
  void optionalFiltersCanBeOmitted() {
    query(
        em,
        MyAssignmentResponse.class,
        List.of(),
        "studentId",
        "now",
        "courseStatus",
        "enrollmentStatus");
    query(em, MySubmissionResponse.class, List.of(), "studentId");
    query(em, MyQuizAttemptResponse.class, List.of(), "studentId", "now");
    count(em, 0, "studentId");
    assertTrue(service.getAssignments(10L, null, null, 0, 20).content().isEmpty());
    assertTrue(service.getSubmissions(10L, null, null, 0, 20).content().isEmpty());
    assertTrue(service.getQuizAttempts(10L, null, null, 0, 20).content().isEmpty());
  }

  @Test
  void rejectsInvalidCourseIdBeforeQuerying() {
    assertThrows(BadRequestException.class, () -> service.getAssignments(10L, 0L, null, 0, 20));
    assertThrows(BadRequestException.class, () -> service.getSubmissions(10L, null, null, -1, 20));
    verifyNoInteractions(em);
  }
}

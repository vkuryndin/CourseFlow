package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.assignment;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.api.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.api.StudentAccess;
import org.courseflow.learning.api.EnrollmentAccess;
import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.repository.AssignmentRepository;
import org.courseflow.learning.repository.SubmissionRepository;
import org.courseflow.testing.QueryMocks;
import org.junit.jupiter.api.Test;

class SubmissionServiceReadTest {
  private final AssignmentRepository assignments = mock(AssignmentRepository.class);
  private final SubmissionRepository submissions = mock(SubmissionRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final EntityManager em = mock(EntityManager.class);
  private final SubmissionService service =
      new SubmissionService(
          submissions,
          assignments,
          mock(EnrollmentAccess.class),
          guard,
          mock(StudentAccess.class),
          em,
          CLOCK,
          mock(GradeAudit.class));
  private final org.courseflow.learning.entity.Assignment assignment = assignment();
  private final Submission submission = id(new Submission(assignment, student(), "Work", NOW), 91);

  private void setup() {
    when(assignments.findCourseIdByAssignmentId(70L)).thenReturn(Optional.of(40L));
    when(guard.find(40L)).thenReturn(assignment.getLesson().getModule().getCourse());
    when(assignments.findById(70L)).thenReturn(Optional.of(assignment));
  }

  @Test
  void getOwnSubmission() {
    when(assignments.existsById(70L)).thenReturn(true);
    when(submissions.findByAssignment_IdAndStudent_Id(70L, 10L))
        .thenReturn(Optional.of(submission));
    assertEquals("Work", service.getMySubmission(70L, 10L).content());
  }

  @Test
  void missingAssignmentRejected() {
    assertThrows(ResourceNotFoundException.class, () -> service.getMySubmission(70L, 10L));
    verifyNoInteractions(submissions);
  }

  @Test
  void missingSubmissionRejected() {
    when(assignments.existsById(70L)).thenReturn(true);
    assertThrows(ResourceNotFoundException.class, () -> service.getMySubmission(70L, 10L));
  }

  @Test
  void listWithStatusAndScoreSort() {
    setup();
    submission.grade(90, "Good");
    var data =
        QueryMocks.query(em, Submission.class, List.of(submission), "assignmentId", "status");
    var count = QueryMocks.count(em, 1L, "assignmentId", "status");
    var result =
        service.findByAssignment(70L, 20L, false, SubmissionStatus.GRADED, 0, 10, "score", "desc");
    assertEquals(90, result.content().get(0).score());
    verify(data).setParameter("status", SubmissionStatus.GRADED);
    verify(count).setParameter("assignmentId", 70L);
    verify(em).createQuery(contains("ORDER BY s.score desc, s.id ASC"), eq(Submission.class));
  }

  @Test
  void listWithoutStatusUsesIdSort() {
    setup();
    var data = QueryMocks.query(em, Submission.class, List.of(submission), "assignmentId");
    QueryMocks.count(em, 1L, "assignmentId");
    assertEquals(
        1L, service.findByAssignment(70L, 20L, false, null, 0, 10, "id", "asc").totalElements());
    verify(data, never()).setParameter(eq("status"), any());
  }

  @Test
  void listOtherSupportedSortFields() {
    setup();
    QueryMocks.query(em, Submission.class, List.of(), "assignmentId");
    QueryMocks.count(em, 0L, "assignmentId");
    assertTrue(
        service
            .findByAssignment(70L, 20L, false, null, 0, 10, "submittedAt", "asc")
            .content()
            .isEmpty());
    assertTrue(
        service
            .findByAssignment(70L, 20L, false, null, 0, 10, "status", "asc")
            .content()
            .isEmpty());
  }

  @Test
  void unsupportedSortRejected() {
    assertThrows(
        BadRequestException.class,
        () -> service.findByAssignment(70L, 20L, false, null, 0, 10, "content", "asc"));
    verifyNoInteractions(em);
  }
}

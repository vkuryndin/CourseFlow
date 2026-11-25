package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.assignment;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.dto.GradeSubmissionRequest;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.repository.AssignmentRepository;
import org.courseflow.learning.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

class SubmissionServiceTest {
  private final SubmissionRepository submissions = mock(SubmissionRepository.class);
  private final AssignmentRepository assignments = mock(AssignmentRepository.class);
  private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final StudentAccess students = mock(StudentAccess.class);
  private final GradeAudit audit = mock(GradeAudit.class);
  private final SubmissionService service =
      new SubmissionService(
          submissions,
          assignments,
          enrollments,
          guard,
          students,
          mock(EntityManager.class),
          CLOCK,
          audit);
  private final Assignment a = assignment();
  private final org.courseflow.catalog.entity.Course c = a.getLesson().getModule().getCourse();
  private final org.courseflow.identity.entity.User s = student();
  private final Submission work = id(new Submission(a, s, "Work", NOW), 90);

  private void setup() {
    when(assignments.findCourseIdByAssignmentId(70L)).thenReturn(Optional.of(40L));
    when(guard.lockShared(40L)).thenReturn(c);
    when(assignments.findById(70L)).thenReturn(Optional.of(a));
    when(students.lockActive(10L)).thenReturn(s);
  }

  @Test
  void submitLocksBeforeLoadingAssignmentAndTrimsContent() {
    setup();
    when(submissions.saveAndFlush(any(Submission.class)))
        .thenAnswer((i) -> id(i.getArgument(0), 90));
    var r = service.submit(70L, 10L, " Work ");

    assertEquals("Work", r.content());
    assertEquals(SubmissionStatus.SUBMITTED, r.status());
    var order = inOrder(assignments, guard, students, enrollments, submissions);
    order.verify(assignments).findCourseIdByAssignmentId(70L);
    order.verify(guard).lockShared(40L);
    order.verify(assignments).findById(70L);
    order.verify(students).lockActive(10L);
    order.verify(enrollments).requireActive(eq(10L), eq(40L), anyString());
    order.verify(submissions).saveAndFlush(any(Submission.class));
  }

  @Test
  void duplicateSubmissionRejected() {
    setup();
    when(submissions.findByAssignment_IdAndStudent_Id(70L, 10L)).thenReturn(Optional.of(work));

    assertThrows(BusinessConflictException.class, () -> service.submit(70L, 10L, "Work"));
    verify(submissions, never()).saveAndFlush(any());
  }

  @Test
  void deadlineBoundaryRejected() {
    setup();
    a.updateDetails("Task", "Text", NOW, 100);

    assertThrows(BusinessConflictException.class, () -> service.submit(70L, 10L, "Work"));
    verifyNoInteractions(submissions);
  }

  @Test
  void ownCourseAndArchivedCourseRejected() {
    setup();

    assertThrows(BusinessConflictException.class, () -> service.submit(70L, 20L, "Work"));
    c.archive();
    assertThrows(BusinessConflictException.class, () -> service.submit(70L, 10L, "Work"));
    verifyNoInteractions(submissions);
  }

  @Test
  void enrollmentAccessRequired() {
    setup();
    doThrow(new AccessDeniedException("not enrolled"))
        .when(enrollments)
        .requireActive(eq(10L), eq(40L), anyString());

    assertThrows(AccessDeniedException.class, () -> service.submit(70L, 10L, "Work"));
    verifyNoInteractions(submissions);
  }

  @Test
  void databaseConflictTranslated() {
    setup();
    when(submissions.saveAndFlush(any(Submission.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThrows(BusinessConflictException.class, () -> service.submit(70L, 10L, "Work"));
  }

  @Test
  void missingAssignmentOrSubmissionRejected() {
    assertThrows(ResourceNotFoundException.class, () -> service.submit(70L, 10L, "Work"));
    assertThrows(ResourceNotFoundException.class, () -> service.getMySubmission(70L, 10L));
    when(assignments.existsById(70L)).thenReturn(true);
    assertThrows(ResourceNotFoundException.class, () -> service.getMySubmission(70L, 10L));
  }

  @Test
  void initialGradeAndRegradeAuditPreviousValue() {
    setup();
    when(submissions.findForGrading(90L, 70L)).thenReturn(Optional.of(work));
    service.grade(70L, 90L, new GradeSubmissionRequest(80, " Good ", 0L), 20L, false);

    assertEquals(80, work.getScore());
    assertEquals("Good", work.getFeedback());
    verify(audit).afterSuccessfulCommit(70L, 90L, 20L, null, 80, true, false, 0L, NOW);
    service.grade(70L, 90L, new GradeSubmissionRequest(90, "Good", 0L), 20L, false);
    verify(audit).afterSuccessfulCommit(70L, 90L, 20L, 80, 90, false, true, 0L, NOW);
  }

  @Test
  void rejectedGradeDoesNotEmitAudit() {
    setup();
    when(submissions.findForGrading(90L, 70L)).thenReturn(Optional.of(work));

    assertThrows(
        BadRequestException.class,
        () -> service.grade(70L, 90L, new GradeSubmissionRequest(101, null, 0L), 20L, false));
    assertThrows(
        OptimisticLockingFailureException.class,
        () -> service.grade(70L, 90L, new GradeSubmissionRequest(80, null, 1L), 20L, false));
    assertNull(work.getScore());
    verifyNoInteractions(audit);
  }

  @Test
  void failedFlushDoesNotEmitAudit() {
    setup();
    when(submissions.findForGrading(90L, 70L)).thenReturn(Optional.of(work));
    doThrow(new DataIntegrityViolationException("failed")).when(submissions).flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> service.grade(70L, 90L, new GradeSubmissionRequest(80, null, 0L), 20L, false));
    verifyNoInteractions(audit);
  }
}

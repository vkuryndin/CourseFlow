package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

class EnrollmentServiceTest {
  private final EnrollmentRepository repo = mock(EnrollmentRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final StudentAccess students = mock(StudentAccess.class);
  private final EnrollmentService service = new EnrollmentService(repo, guard, students, CLOCK);
  private final org.courseflow.catalog.entity.Course c = course();
  private final org.courseflow.identity.entity.User s = student();

  private void setup() {
    when(guard.lockShared(40L)).thenReturn(c);
    when(students.lockForEnrollment(10L)).thenReturn(s);
  }

  @Test
  void createsEnrollmentWithCorrectLinksAndDate() {
    setup();
    when(repo.saveAndFlush(any(Enrollment.class))).thenAnswer((i) -> id(i.getArgument(0), 99));

    var result = service.enroll(40L, 10L);

    assertTrue(result.created());
    assertEquals(99L, result.enrollment().id());
    assertEquals(10L, result.enrollment().studentId());
    assertEquals(40L, result.enrollment().courseId());
    assertEquals(NOW, result.enrollment().enrolledAt());
  }

  @Test
  void repeatEnrollmentIsIdempotent() {
    setup();
    var existing = id(Enrollment.createForStudent(s, c, NOW.minusSeconds(100)), 99);
    when(repo.findByStudent_IdAndCourse_Id(10L, 40L)).thenReturn(Optional.of(existing));

    assertFalse(service.enroll(40L, 10L).created());
    existing.withdraw(NOW);
    assertFalse(service.enroll(40L, 10L).created());
    assertEquals(EnrollmentStatus.ACTIVE, existing.getStatus());
    assertNull(existing.getEndedAt());
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void archivedCourseRejectsEnrollmentBeforeStudentLookup() {
    setup();
    c.archive();

    assertThrows(BusinessConflictException.class, () -> service.enroll(40L, 10L));
    verifyNoInteractions(students, repo);
  }

  @Test
  void teacherCannotEnrollInOwnCourse() {
    setup();
    when(students.lockForEnrollment(20L)).thenReturn(c.getTeacher());

    assertThrows(BusinessConflictException.class, () -> service.enroll(40L, 20L));
    verifyNoInteractions(repo);
  }

  @Test
  void withdrawLocksCourseAndStudentBeforeChangingRecord() {
    setup();
    var existing = Enrollment.createForStudent(s, c, NOW.minusSeconds(10));
    when(repo.findByStudent_IdAndCourse_Id(10L, 40L)).thenReturn(Optional.of(existing));
    service.withdraw(40L, 10L);

    assertEquals(EnrollmentStatus.WITHDRAWN, existing.getStatus());
    assertEquals(NOW, existing.getEndedAt());
    var order = inOrder(guard, students, repo);
    order.verify(guard).lockShared(40L);
    order.verify(students).lockExisting(10L);
    order.verify(repo).findByStudent_IdAndCourse_Id(10L, 40L);
  }

  @Test
  void missingEnrollmentRejected() {
    setup();

    assertThrows(ResourceNotFoundException.class, () -> service.withdraw(40L, 10L));
    assertThrows(ResourceNotFoundException.class, () -> service.getMyEnrollment(40L, 10L));
  }

  @Test
  void teacherCannotViewOtherCourseEnrollments() {
    when(guard.find(40L)).thenReturn(c);

    assertThrows(
        AccessDeniedException.class,
        () -> service.getCourseEnrollments(40L, 99L, false, null, 0, 20, "id", "asc"));
    verifyNoInteractions(repo);
  }

  @Test
  void myEnrollmentStatusFilterAndPagination() {
    var p = PageRequest.of(0, 20, Sort.by("id"));
    var e = Enrollment.createForStudent(s, c, NOW);
    when(repo.findAllByStudent_IdAndStatus(10L, EnrollmentStatus.ACTIVE, p))
        .thenReturn(new PageImpl<>(List.of(e), p, 1));

    assertEquals(
        1,
        service.getMyEnrollments(10L, EnrollmentStatus.ACTIVE, 0, 20, "id", "asc").totalElements());
    assertThrows(
        BadRequestException.class, () -> service.getMyEnrollments(10L, null, 0, 20, "bad", "asc"));
  }
}

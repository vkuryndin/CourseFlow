package org.courseflow.identity.service;

import static org.courseflow.testing.Fixtures.student;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.api.StudentAccess;
import org.courseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StudentAccessTest {
  private final UserRepository repo = mock(UserRepository.class);
  private final StudentAccess access = new StudentAccess(repo);

  @Test
  void activeStudentAccepted() {
    var s = student();
    when(repo.findByIdForUpdate(10L)).thenReturn(Optional.of(s));

    assertSame(s, access.lockActive(10L));
    assertSame(s, access.lockForEnrollment(10L));
  }

  @Test
  void missingStudentRejected() {
    assertThrows(ResourceNotFoundException.class, () -> access.lockExisting(10L));
  }

  @Test
  void inactiveStudentRejected() {
    var s = student();
    s.deactivate();
    when(repo.findByIdForUpdate(10L)).thenReturn(Optional.of(s));

    assertThrows(AccessDeniedException.class, () -> access.lockActive(10L));
    assertThrows(BusinessConflictException.class, () -> access.lockForEnrollment(10L));
  }

  @Test
  void teacherWithoutStudentRoleRejected() {
    when(repo.findByIdForUpdate(20L)).thenReturn(Optional.of(teacher()));

    assertThrows(AccessDeniedException.class, () -> access.lockActive(20L));
    assertThrows(AccessDeniedException.class, () -> access.lockForEnrollment(20L));
  }
}

package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.course;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.catalog.api.CourseGuard;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CourseGuardTest {
  private final CourseRepository repo = mock(CourseRepository.class);
  private final CourseGuard guard = new CourseGuard(repo);

  @Test
  void ownerAndAdminMayEditActiveCourse() {
    var c = course();
    when(repo.findByIdForUpdate(40L)).thenReturn(Optional.of(c));

    assertSame(c, guard.lockEditable(40L, 20L, false, "archived"));
    assertSame(c, guard.lockEditable(40L, 99L, true, "archived"));
  }

  @Test
  void nonOwnerDenied() {
    when(repo.findByIdForUpdate(40L)).thenReturn(Optional.of(course()));

    assertThrows(AccessDeniedException.class, () -> guard.lockOwned(40L, 99L, false));
  }

  @Test
  void archivedCourseCannotBeEditedEvenByAdmin() {
    var c = course();
    c.archive();
    when(repo.findByIdForUpdate(40L)).thenReturn(Optional.of(c));

    assertThrows(
        BusinessConflictException.class, () -> guard.lockEditable(40L, 99L, true, "archived"));
  }

  @Test
  void missingCourseRejectedByEveryLookup() {
    assertThrows(ResourceNotFoundException.class, () -> guard.requireExists(40L));
    assertThrows(ResourceNotFoundException.class, () -> guard.lock(40L));
    assertThrows(ResourceNotFoundException.class, () -> guard.find(40L));
    assertThrows(ResourceNotFoundException.class, () -> guard.lockShared(40L));
  }

  @Test
  void correctRepositoryMethodsForReadAndSharedLock() {
    var c = course();
    when(repo.existsById(40L)).thenReturn(true);
    when(repo.findById(40L)).thenReturn(Optional.of(c));
    when(repo.findByIdForShare(40L)).thenReturn(Optional.of(c));
    guard.requireExists(40L);

    assertSame(c, guard.find(40L));
    assertSame(c, guard.lockShared(40L));
    verify(repo, never()).findByIdForUpdate(anyLong());
  }
}

package org.courseflow.learning.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class EnrollmentAccessTest {
  private final EnrollmentRepository repo = mock(EnrollmentRepository.class);
  private final EnrollmentAccess access = new EnrollmentAccess(repo);

  @Test
  void activeEnrollmentRequired() {
    assertThrows(AccessDeniedException.class, () -> access.requireActive(1L, 2L, "not enrolled"));
    when(repo.hasActiveEnrollment(1L, 2L)).thenReturn(true);
    assertDoesNotThrow(() -> access.requireActive(1L, 2L, "not enrolled"));
  }

  @Test
  void historyAndActiveChecksAreDifferent() {
    when(repo.existsByCourse_Id(2L)).thenReturn(true);
    when(repo.existsByStudent_IdAndStatus(1L, EnrollmentStatus.ACTIVE)).thenReturn(true);

    assertTrue(access.hasEnrollmentHistory(2L));
    assertTrue(access.hasActiveEnrollments(1L));
    assertFalse(access.hasActiveEnrollment(1L, 2L));
  }
}

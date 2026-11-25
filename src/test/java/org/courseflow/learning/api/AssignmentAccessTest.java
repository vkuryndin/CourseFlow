package org.courseflow.learning.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.learning.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;

class AssignmentAccessTest {
  @Test
  void checksExistenceThroughOwningRepository() {
    var repository = mock(AssignmentRepository.class);
    when(repository.existsByLesson_Id(10L)).thenReturn(true);
    var access = new AssignmentAccess(repository);
    assertTrue(access.hasAssignments(10L));
    assertFalse(access.hasAssignments(11L));
    verify(repository).existsByLesson_Id(10L);
    verify(repository).existsByLesson_Id(11L);
  }
}

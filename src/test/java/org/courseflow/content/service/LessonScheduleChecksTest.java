package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.courseflow.content.api.LessonScheduleChecks;
import org.courseflow.content.repository.LessonSessionRepository;
import org.junit.jupiter.api.Test;

class LessonScheduleChecksTest {
  private final LessonSessionRepository repository = mock(LessonSessionRepository.class);
  private final LessonScheduleChecks checks = new LessonScheduleChecks(repository);

  @Test
  void lessonSessionsExist() {
    when(repository.existsByLesson_Id(60L)).thenReturn(true);
    assertTrue(checks.hasSessionsForLesson(60L));
    assertFalse(checks.hasSessionsForLesson(61L));
  }

  @Test
  void unfinishedIncludesOngoing() {
    when(repository.existsByLesson_Module_Course_IdAndEndsAtGreaterThan(40L, NOW)).thenReturn(true);
    assertTrue(checks.hasUnfinishedSessions(40L, NOW));
    assertFalse(checks.hasUnfinishedSessions(41L, NOW));
  }

  @Test
  void overlapCountConvertedToBoolean() {
    when(repository.countReassignmentOverlaps(40L, 20L, NOW)).thenReturn(2L, 0L);
    assertTrue(checks.hasReassignmentOverlap(40L, 20L, NOW));
    assertFalse(checks.hasReassignmentOverlap(40L, 20L, NOW));
  }
}

package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.courseflow.learning.entity.SubmissionStatus;
import org.junit.jupiter.api.Test;

class AssessmentStatusesTest {

  @Test
  void assignmentStatusPrefersActualSubmission() {
    assertEquals("GRADED", AssessmentStatuses.assignment(SubmissionStatus.GRADED, NOW, NOW));
    assertEquals("SUBMITTED", AssessmentStatuses.assignment(SubmissionStatus.SUBMITTED, NOW, NOW));
  }

  @Test
  void deadlineBoundary() {
    assertEquals("NOT_SUBMITTED", AssessmentStatuses.assignment(null, NOW.plusNanos(1), NOW));
    assertEquals("OVERDUE", AssessmentStatuses.assignment(null, NOW, NOW));
    assertEquals("OVERDUE", AssessmentStatuses.assignment(null, NOW.minusNanos(1), NOW));
  }

  @Test
  void quizStates() {
    assertEquals("COMPLETED", AssessmentStatuses.quiz(0, 1, 0));
    assertEquals("NOT_STARTED", AssessmentStatuses.quiz(null, 0, 0));
    assertEquals("IN_PROGRESS", AssessmentStatuses.quiz(null, 2, 1));
    assertEquals("EXPIRED", AssessmentStatuses.quiz(null, 2, 0));
  }
}

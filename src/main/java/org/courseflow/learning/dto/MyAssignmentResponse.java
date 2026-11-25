package org.courseflow.learning.dto;

import java.time.Instant;

public record MyAssignmentResponse(
    Long assignmentId,
    Long lessonId,
    String lessonTitle,
    Long courseId,
    String courseTitle,
    String title,
    String description,
    Instant dueAt,
    Integer maxScore,
    Long submissionId,
    String status,
    Integer score) {
  public enum Status {
    NOT_SUBMITTED,
    OVERDUE,
    SUBMITTED,
    GRADED
  }
}

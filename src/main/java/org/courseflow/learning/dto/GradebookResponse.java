package org.courseflow.learning.dto;

import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.entity.EnrollmentStatus;

public record GradebookResponse(
    Long courseId,
    List<Column> columns,
    PageResponse<StudentRow> students,
    int columnPage,
    int columnSize,
    long totalColumns,
    long totalColumnPages) {
  public enum AssessmentType {
    ASSIGNMENT,
    QUIZ
  }

  public enum ReviewStatus {
    /** No submission yet, including overdue assignments (backwards-compatible filter). */
    NOT_SUBMITTED,
    OVERDUE,
    SUBMITTED,
    GRADED
  }

  public record Column(
      Long id,
      AssessmentType type,
      String title,
      Long moduleId,
      String moduleTitle,
      Integer maxScore) {}

  public record StudentRow(
      Long studentId,
      String studentName,
      String studentStatus,
      EnrollmentStatus enrollmentStatus,
      List<Cell> results) {}

  public record Cell(
      Long assessmentId, AssessmentType type, String status, Integer score, Long attemptsUsed) {}
}

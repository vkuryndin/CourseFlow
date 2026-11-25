package org.courseflow.learning.dto;

import java.util.ArrayList;
import java.util.Collections;
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
  public GradebookResponse {
    columns = columns == null ? null : Collections.unmodifiableList(new ArrayList<>(columns));
  }

  @Override
  public List<Column> columns() {
    return columns == null ? null : Collections.unmodifiableList(columns);
  }

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
      List<Cell> results) {
    public StudentRow {
      results = results == null ? null : Collections.unmodifiableList(new ArrayList<>(results));
    }

    @Override
    public List<Cell> results() {
      return results == null ? null : Collections.unmodifiableList(results);
    }
  }

  public record Cell(
      Long assessmentId, AssessmentType type, String status, Integer score, Long attemptsUsed) {}
}

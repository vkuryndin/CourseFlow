package org.courseflow.learning.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.learning.entity.EnrollmentStatus;

public record CourseProgressResponse(
    Long courseId,
    String courseTitle,
    CourseStatus courseStatus,
    EnrollmentStatus enrollmentStatus,
    int totalAssessments,
    int completedAssessments,
    int progressPercent,
    Integer performancePercent,
    List<AssignmentProgress> assignments,
    List<QuizProgress> quizzes,
    List<AssignmentProgress> upcomingDeadlines,
    boolean detailsIncluded,
    int detailPage,
    int detailSize,
    long totalAssignments,
    long totalQuizzes,
    long totalUpcomingDeadlines) {
  public CourseProgressResponse {
    assignments =
        assignments == null ? null : Collections.unmodifiableList(new ArrayList<>(assignments));
    quizzes = quizzes == null ? null : Collections.unmodifiableList(new ArrayList<>(quizzes));
    upcomingDeadlines =
        upcomingDeadlines == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(upcomingDeadlines));
  }

  @Override
  public List<AssignmentProgress> assignments() {
    return assignments == null ? null : Collections.unmodifiableList(assignments);
  }

  @Override
  public List<QuizProgress> quizzes() {
    return quizzes == null ? null : Collections.unmodifiableList(quizzes);
  }

  @Override
  public List<AssignmentProgress> upcomingDeadlines() {
    return upcomingDeadlines == null ? null : Collections.unmodifiableList(upcomingDeadlines);
  }

  public record AssignmentProgress(
      Long assignmentId,
      String title,
      Integer maxScore,
      Integer score,
      String status,
      Instant dueAt) {}

  public record QuizProgress(
      Long quizId,
      String title,
      Integer bestScore,
      Long attemptsUsed,
      Integer maxAttempts,
      String status) {}
}

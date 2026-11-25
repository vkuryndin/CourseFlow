package org.courseflow.dashboard.dto;

import java.time.Instant;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;

public record TeacherDashboardResponse(
    long totalCourseCount,
    long activeCourseCount,
    long archivedCourseCount,
    long activeStudentCount,
    long pendingSubmissionCount,
    List<CourseSummary> courses,
    List<PendingSubmission> pendingSubmissions,
    List<UpcomingSession> upcomingSessions,
    List<RecentQuizResult> recentQuizResults) {
  public record CourseSummary(
      Long courseId, String title, CourseStatus status, long activeEnrollmentCount) {}

  public record PendingSubmission(
      Long submissionId,
      Long assignmentId,
      Long lessonId,
      String assignmentTitle,
      Long courseId,
      String courseTitle,
      Long studentId,
      String studentName,
      Instant submittedAt,
      Integer maxScore) {}

  public record RecentQuizResult(
      Long attemptId,
      Long quizId,
      String quizTitle,
      Long courseId,
      String courseTitle,
      Long studentId,
      String studentName,
      Integer attemptNumber,
      Integer score,
      Instant finishedAt) {}
}

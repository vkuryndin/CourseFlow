package org.courseflow.dashboard.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
  public TeacherDashboardResponse {
    courses = courses == null ? null : Collections.unmodifiableList(new ArrayList<>(courses));
    pendingSubmissions =
        pendingSubmissions == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(pendingSubmissions));
    recentQuizResults =
        recentQuizResults == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(recentQuizResults));
    upcomingSessions =
        upcomingSessions == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(upcomingSessions));
  }

  @Override
  public List<CourseSummary> courses() {
    return courses == null ? null : Collections.unmodifiableList(courses);
  }

  @Override
  public List<PendingSubmission> pendingSubmissions() {
    return pendingSubmissions == null ? null : Collections.unmodifiableList(pendingSubmissions);
  }

  @Override
  public List<RecentQuizResult> recentQuizResults() {
    return recentQuizResults == null ? null : Collections.unmodifiableList(recentQuizResults);
  }

  @Override
  public List<UpcomingSession> upcomingSessions() {
    return upcomingSessions == null ? null : Collections.unmodifiableList(upcomingSessions);
  }

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

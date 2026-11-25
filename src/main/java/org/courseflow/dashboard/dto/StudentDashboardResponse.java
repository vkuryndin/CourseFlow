package org.courseflow.dashboard.dto;

import java.time.Instant;
import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;

public record StudentDashboardResponse(
    long activeCourseCount,
    PageResponse<CourseProgressSummaryResponse> courses,
    List<UpcomingSession> upcomingSessions,
    List<UpcomingDeadline> upcomingDeadlines) {
  public record UpcomingDeadline(
      Long assignmentId,
      Long courseId,
      String courseTitle,
      Long lessonId,
      String assignmentTitle,
      Integer maxScore,
      Instant dueAt) {}
}

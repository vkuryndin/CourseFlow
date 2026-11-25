package org.courseflow.dashboard.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;

public record StudentDashboardResponse(
    long activeCourseCount,
    PageResponse<CourseProgressSummaryResponse> courses,
    List<UpcomingSession> upcomingSessions,
    List<UpcomingDeadline> upcomingDeadlines) {
  public StudentDashboardResponse {
    upcomingDeadlines =
        upcomingDeadlines == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(upcomingDeadlines));
    upcomingSessions =
        upcomingSessions == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(upcomingSessions));
  }

  @Override
  public List<UpcomingDeadline> upcomingDeadlines() {
    return upcomingDeadlines == null ? null : Collections.unmodifiableList(upcomingDeadlines);
  }

  @Override
  public List<UpcomingSession> upcomingSessions() {
    return upcomingSessions == null ? null : Collections.unmodifiableList(upcomingSessions);
  }

  public record UpcomingDeadline(
      Long assignmentId,
      Long courseId,
      String courseTitle,
      Long lessonId,
      String assignmentTitle,
      Integer maxScore,
      Instant dueAt) {}
}

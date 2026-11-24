
package org.courseflow.dashboard.dto;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;

import java.time.Instant;
import java.util.List;

public record StudentDashboardResponse(
        long activeCourseCount,
        PageResponse<CourseProgressSummaryResponse> courses,
        List<UpcomingSession> upcomingSessions,
        List<UpcomingDeadline> upcomingDeadlines
) {
    public record UpcomingDeadline(
            Long assignmentId,
            Long courseId,
            String courseTitle,
            Long lessonId,
            String assignmentTitle,
            Integer maxScore,
            Instant dueAt
    ) {
    }
}

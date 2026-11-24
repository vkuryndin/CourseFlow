
package org.courseflow.dashboard.dto;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CourseProgressResponse;

import java.time.Instant;
import java.util.List;

public record StudentDashboardResponse(
        long activeCourseCount,
        PageResponse<CourseProgressResponse> courses,
        List<UpcomingSession> upcomingSessions,
        List<UpcomingDeadline> upcomingDeadlines
) {
    public record UpcomingSession(
            Long sessionId,
            Long courseId,
            String courseTitle,
            Long lessonId,
            String lessonTitle,
            Instant startsAt,
            Instant endsAt,
            String format,
            String meetingUrl
    ) {
    }

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

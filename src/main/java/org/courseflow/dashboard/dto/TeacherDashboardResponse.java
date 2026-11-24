
package org.courseflow.dashboard.dto;

import org.courseflow.catalog.entity.CourseStatus;

import java.time.Instant;
import java.util.List;

public record TeacherDashboardResponse(
        long totalCourseCount,
        long activeCourseCount,
        long archivedCourseCount,
        long activeStudentCount,
        long pendingSubmissionCount,
        List<CourseSummary> courses,
        List<PendingSubmission> pendingSubmissions,
        List<UpcomingSession> upcomingSessions,
        List<RecentQuizResult> recentQuizResults
) {
    public record CourseSummary(
            Long courseId,
            String title,
            CourseStatus status,
            long activeEnrollmentCount
    ) {
    }

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
            Integer maxScore
    ) {
    }

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
            Instant finishedAt
    ) {
    }
}

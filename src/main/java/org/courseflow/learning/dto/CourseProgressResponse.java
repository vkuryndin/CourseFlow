
package org.courseflow.learning.dto;

import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.learning.entity.EnrollmentStatus;

import java.time.Instant;
import java.util.List;

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
        long totalUpcomingDeadlines
) {
    public record AssignmentProgress(
            Long assignmentId,
            String title,
            Integer maxScore,
            Integer score,
            String status,
            Instant dueAt
    ) {
    }

    public record QuizProgress(
            Long quizId,
            String title,
            Integer bestScore,
            Long attemptsUsed,
            Integer maxAttempts,
            String status
    ) {
    }
}

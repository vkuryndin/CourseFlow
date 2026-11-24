package org.courseflow.learning.dto;

import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.learning.entity.EnrollmentStatus;

/** Course-level totals, without assessment rows. */
public record CourseProgressSummaryResponse(
        Long courseId, String courseTitle, CourseStatus courseStatus,
        EnrollmentStatus enrollmentStatus, int totalAssessments,
        int completedAssessments, int progressPercent, Integer performancePercent,
        long totalAssignments, long totalQuizzes, long totalUpcomingDeadlines
) {}

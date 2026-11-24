
package org.courseflow.dashboard.dto;

public record AdminDashboardResponse(
        UserStatistics users,
        RoleStatistics roles,
        CourseStatistics courses,
        EnrollmentStatistics enrollments
) {
    public record UserStatistics(
            long total,
            long active,
            long inactive,
            long deleted
    ) {
    }

    public record RoleStatistics(
            long students,
            long teachers,
            long admins
    ) {
    }

    public record CourseStatistics(
            long total,
            long active,
            long archived
    ) {
    }

    public record EnrollmentStatistics(
            long total,
            long active,
            long withdrawn,
            long activeOnActiveCourses
    ) {
    }
}

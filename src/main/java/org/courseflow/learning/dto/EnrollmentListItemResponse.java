
package org.courseflow.learning.dto;

import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;

import java.time.Instant;

public record EnrollmentListItemResponse(
        Long id,
        Long studentId,
        String studentName,
        Long courseId,
        String courseTitle,
        CourseStatus courseStatus,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant endedAt
) {
    public static EnrollmentListItemResponse from(
            Enrollment enrollment
    ) {
        return new EnrollmentListItemResponse(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getDisplayName(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getTitle(),
                enrollment.getCourse().getStatus(),
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getEndedAt()
        );
    }
}

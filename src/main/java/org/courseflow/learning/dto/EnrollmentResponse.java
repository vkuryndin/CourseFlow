package org.courseflow.learning.dto;

import java.time.Instant;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;

public record EnrollmentResponse(
    Long id,
    Long studentId,
    Long courseId,
    Instant enrolledAt,
    Instant endedAt,
    EnrollmentStatus status) {
  public static EnrollmentResponse from(Enrollment enrollment) {
    return new EnrollmentResponse(
        enrollment.getId(),
        enrollment.getStudent().getId(),
        enrollment.getCourse().getId(),
        enrollment.getEnrolledAt(),
        enrollment.getEndedAt(),
        enrollment.getStatus());
  }
}

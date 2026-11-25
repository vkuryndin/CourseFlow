package org.courseflow.review.dto;

import java.time.Instant;
import org.courseflow.review.entity.CourseReview;

public record CourseReviewResponse(
    Long id,
    Long courseId,
    Long studentId,
    String studentName,
    Integer rating,
    String comment,
    Instant createdAt) {
  public static CourseReviewResponse from(CourseReview review) {
    return new CourseReviewResponse(
        review.getId(),
        review.getCourse().getId(),
        review.getStudent().getId(),
        review.getStudent().getDisplayName(),
        review.getRating(),
        review.getComment(),
        review.getCreatedAt());
  }
}

package org.courseflow.review.dto;

import org.courseflow.review.entity.CourseReview;
import java.time.Instant;

public record PublicCourseReviewResponse(Long id, Long courseId, String studentName,
                                         Integer rating, String comment, Instant createdAt) {
    public static PublicCourseReviewResponse from(CourseReview review) {
        return new PublicCourseReviewResponse(review.getId(), review.getCourse().getId(),
                review.getStudent().getDisplayName(), review.getRating(), review.getComment(),
                review.getCreatedAt());
    }
}

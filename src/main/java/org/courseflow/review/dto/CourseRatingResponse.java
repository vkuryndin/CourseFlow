package org.courseflow.review.dto;

public record CourseRatingResponse(Long courseId, long reviewCount, Double averageRating) {}

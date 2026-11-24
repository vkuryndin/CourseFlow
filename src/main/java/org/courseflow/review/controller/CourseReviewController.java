
package org.courseflow.review.controller;

import org.courseflow.review.dto.PublicCourseReviewResponse;

import jakarta.validation.Valid;

import org.courseflow.common.api.PageResponse;

import org.courseflow.review.dto.CourseRatingResponse;
import org.courseflow.review.dto.CourseReviewRequest;
import org.courseflow.review.dto.CourseReviewResponse;
import org.courseflow.review.service.CourseReviewService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/reviews")
public class CourseReviewController {

    private final CourseReviewService courseReviewService;

    public CourseReviewController(
            CourseReviewService courseReviewService
    ) {
        this.courseReviewService = courseReviewService;
    }

    @PostMapping
    public ResponseEntity<CourseReviewResponse> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CourseReviewResponse response =
                courseReviewService.create(
                        courseId,
                        Long.valueOf(jwt.getSubject()),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/me")
    public CourseReviewResponse update(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return courseReviewService.update(
                courseId,
                Long.valueOf(jwt.getSubject()),
                request
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        courseReviewService.delete(
                courseId,
                Long.valueOf(jwt.getSubject())
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CourseReviewResponse getMyReview(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return courseReviewService.getMyReview(
                courseId,
                Long.valueOf(jwt.getSubject())
        );
    }

    @GetMapping
    public PageResponse<PublicCourseReviewResponse> findByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt")
            String sortBy,
            @RequestParam(defaultValue = "desc")
            String direction
    ) {
        return courseReviewService.findByCourse(
                courseId,
                page,
                size,
                sortBy,
                direction
        );
    }

    @GetMapping("/rating")
    public CourseRatingResponse getRating(
            @PathVariable Long courseId
    ) {
        return courseReviewService.getRating(courseId);
    }
}

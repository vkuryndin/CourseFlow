
package org.courseflow.learning.controller;

import org.courseflow.learning.dto.EnrollmentResponse;
import org.courseflow.learning.service.EnrollmentService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/courses/{courseId}/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(
            EnrollmentService enrollmentService
    ) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long studentId = Long.valueOf(jwt.getSubject());

        EnrollmentService.EnrollResult result =
                enrollmentService.enroll(courseId, studentId);

        if (result.created()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(result.enrollment());
        }

        return ResponseEntity.ok(result.enrollment());
    }

    @GetMapping("/me")
    public EnrollmentResponse getMyEnrollment(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long studentId = Long.valueOf(jwt.getSubject());

        return enrollmentService.getMyEnrollment(
                courseId,
                studentId
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long studentId = Long.valueOf(jwt.getSubject());

        enrollmentService.withdraw(courseId, studentId);

        return ResponseEntity.noContent().build();
    }

    // ADMIN may withdraw even an inactive student. Keep the enrollment history.
    @DeleteMapping("/students/{studentId}")
    public ResponseEntity<Void> withdrawStudent(
            @PathVariable Long courseId,
            @PathVariable Long studentId
    ) {
        enrollmentService.withdraw(courseId, studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<EnrollmentListItemResponse> getCourseEnrollments(
            @PathVariable Long courseId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "enrolledAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(jwt.getSubject());

        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );

        return enrollmentService.getCourseEnrollments(
                courseId,
                currentUserId,
                isAdmin,
                status,
                page,
                size,
                sortBy,
                direction
        );
    }

}

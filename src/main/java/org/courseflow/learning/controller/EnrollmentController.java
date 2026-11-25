package org.courseflow.learning.controller;

import java.net.URI;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.dto.EnrollmentResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.EnrollmentService;
import org.courseflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/enrollments")
public class EnrollmentController {

  private static final Logger LOG = LoggerFactory.getLogger(EnrollmentController.class);
  private final EnrollmentService enrollmentService;

  public EnrollmentController(EnrollmentService enrollmentService) {
    this.enrollmentService = enrollmentService;
  }

  @PostMapping
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Existing resource",
      useReturnTypeSchema = true)
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Resource created",
      useReturnTypeSchema = true)
  public ResponseEntity<EnrollmentResponse> enroll(
      @PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
    Long studentId = CurrentUser.id(jwt);

    EnrollmentService.EnrollResult result = enrollmentService.enroll(courseId, studentId);

    if (result.created()) {
      return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/enrollments/me"))
          .body(result.enrollment());
    }

    return ResponseEntity.ok(result.enrollment());
  }

  @GetMapping("/me")
  public EnrollmentResponse getMyEnrollment(
      @PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
    Long studentId = CurrentUser.id(jwt);

    return enrollmentService.getMyEnrollment(courseId, studentId);
  }

  @DeleteMapping("/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> withdraw(
      @PathVariable Long courseId, @AuthenticationPrincipal Jwt jwt) {
    Long studentId = CurrentUser.id(jwt);

    enrollmentService.withdraw(courseId, studentId);

    return ResponseEntity.noContent().build();
  }

  // ADMIN may withdraw even an inactive student. Keep the enrollment history.
  @DeleteMapping("/students/{studentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> withdrawStudent(
      @PathVariable Long courseId, @PathVariable Long studentId, @AuthenticationPrincipal Jwt jwt) {
    enrollmentService.withdraw(courseId, studentId);
    LOG.info(
        "admin_action=withdraw_student actor={} course={} target={}",
        jwt.getSubject(),
        courseId,
        studentId);
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
      Authentication authentication) {
    Long currentUserId = CurrentUser.id(jwt);

    boolean isAdmin = CurrentUser.isAdmin(authentication);

    return enrollmentService.getCourseEnrollments(
        courseId, currentUserId, isAdmin, status, page, size, sortBy, direction);
  }
}

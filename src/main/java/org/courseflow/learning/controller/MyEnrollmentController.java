package org.courseflow.learning.controller;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.EnrollmentService;
import org.courseflow.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/enrollments")
public class MyEnrollmentController {

  private final EnrollmentService enrollmentService;

  public MyEnrollmentController(EnrollmentService enrollmentService) {
    this.enrollmentService = enrollmentService;
  }

  @GetMapping
  public PageResponse<EnrollmentListItemResponse> getMyEnrollments(
      @RequestParam(required = false) EnrollmentStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "enrolledAt") String sortBy,
      @RequestParam(defaultValue = "desc") String direction,
      @AuthenticationPrincipal Jwt jwt) {
    Long studentId = CurrentUser.id(jwt);

    return enrollmentService.getMyEnrollments(studentId, status, page, size, sortBy, direction);
  }
}

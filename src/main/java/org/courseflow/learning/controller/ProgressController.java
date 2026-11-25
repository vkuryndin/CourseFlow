package org.courseflow.learning.controller;

import org.courseflow.common.api.PageResponse;
import org.courseflow.common.security.CurrentUser;
import org.courseflow.learning.dto.CourseProgressResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.ProgressService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/progress")
public class ProgressController {

  private final ProgressService progressService;

  public ProgressController(ProgressService progressService) {
    this.progressService = progressService;
  }

  @GetMapping
  public PageResponse<CourseProgressResponse> getMyProgress(
      @RequestParam(required = false) Long courseId,
      @RequestParam(required = false) EnrollmentStatus enrollmentStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "courseTitle") String sortBy,
      @RequestParam(defaultValue = "asc") String direction,
      @RequestParam(required = false) Boolean includeDetails,
      @RequestParam(defaultValue = "0") int detailPage,
      @RequestParam(defaultValue = "50") int detailSize,
      @AuthenticationPrincipal Jwt jwt) {
    return progressService.getMyProgress(
        CurrentUser.id(jwt),
        courseId,
        enrollmentStatus,
        page,
        size,
        sortBy,
        direction,
        includeDetails == null ? courseId != null : includeDetails,
        detailPage,
        detailSize);
  }
}

package org.courseflow.learning.controller;

import org.courseflow.learning.dto.GradebookResponse;
import org.courseflow.learning.dto.GradebookResponse.AssessmentType;
import org.courseflow.learning.dto.GradebookResponse.ReviewStatus;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.GradebookService;
import org.courseflow.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/gradebook")
public class GradebookController {

  private final GradebookService gradebookService;

  public GradebookController(GradebookService gradebookService) {
    this.gradebookService = gradebookService;
  }

  @GetMapping
  public GradebookResponse getGradebook(
      @PathVariable Long courseId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) EnrollmentStatus enrollmentStatus,
      @io.swagger.v3.oas.annotations.Parameter(
              description =
                  "Assignment filter: NOT_SUBMITTED includes all missing submissions; OVERDUE includes only missing submissions at or after the deadline.")
          @RequestParam(required = false)
          ReviewStatus reviewStatus,
      @RequestParam(required = false) Long moduleId,
      @RequestParam(required = false) AssessmentType type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "0") int columnPage,
      @RequestParam(defaultValue = "50") int columnSize,
      @RequestParam(defaultValue = "studentName") String sortBy,
      @RequestParam(defaultValue = "asc") String direction,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    return gradebookService.getGradebook(
        courseId,
        CurrentUser.id(jwt),
        isAdmin,
        q,
        enrollmentStatus,
        reviewStatus,
        moduleId,
        type,
        page,
        size,
        sortBy,
        direction,
        columnPage,
        columnSize);
  }
}

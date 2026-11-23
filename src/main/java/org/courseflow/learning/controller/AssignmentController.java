
package org.courseflow.learning.controller;

import jakarta.validation.Valid;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.AssignmentResponse;
import org.courseflow.learning.dto.CreateAssignmentRequest;
import org.courseflow.learning.service.AssignmentService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lessons/{lessonId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(
            AssignmentService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public ResponseEntity<AssignmentResponse> create(
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(jwt.getSubject());

        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );

        AssignmentResponse response = assignmentService.create(
                lessonId,
                request,
                currentUserId,
                isAdmin
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public PageResponse<AssignmentResponse> findByLesson(
            @PathVariable Long lessonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return assignmentService.findByLesson(
                lessonId,
                Long.valueOf(jwt.getSubject()),
                authentication,
                page,
                size,
                sortBy,
                direction
        );
    }

    @GetMapping("/{assignmentId}")
    public AssignmentResponse getById(
            @PathVariable Long lessonId,
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return assignmentService.getById(
                lessonId,
                assignmentId,
                Long.valueOf(jwt.getSubject()),
                authentication
        );
    }

    @PutMapping("/{assignmentId}")
    public AssignmentResponse update(
            @PathVariable Long lessonId,
            @PathVariable Long assignmentId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );

        return assignmentService.update(
                lessonId,
                assignmentId,
                request,
                Long.valueOf(jwt.getSubject()),
                isAdmin
        );
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long lessonId,
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );

        assignmentService.delete(
                lessonId,
                assignmentId,
                Long.valueOf(jwt.getSubject()),
                isAdmin
        );

        return ResponseEntity.noContent().build();
    }

}

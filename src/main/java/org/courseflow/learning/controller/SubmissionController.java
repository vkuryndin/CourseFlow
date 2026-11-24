
package org.courseflow.learning.controller;

import jakarta.validation.Valid;

import org.courseflow.learning.dto.CreateSubmissionRequest;
import org.courseflow.learning.dto.SubmissionResponse;
import org.courseflow.learning.service.SubmissionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.GradeSubmissionRequest;
import org.courseflow.learning.entity.SubmissionStatus;

import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/assignments/{assignmentId}/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(
            SubmissionService submissionService
    ) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(
            @PathVariable Long assignmentId,
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SubmissionResponse response = submissionService.submit(
                assignmentId,
                Long.valueOf(jwt.getSubject()),
                request.content()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me")
    public SubmissionResponse getMySubmission(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return submissionService.getMySubmission(
                assignmentId,
                Long.valueOf(jwt.getSubject())
        );
    }

    @GetMapping
    public PageResponse<SubmissionResponse> findByAssignment(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );

        return submissionService.findByAssignment(
                assignmentId,
                Long.valueOf(jwt.getSubject()),
                isAdmin,
                status,
                page,
                size,
                sortBy,
                direction
        );
    }

    @PutMapping("/{submissionId}/grade")
    public SubmissionResponse grade(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @Valid @RequestBody GradeSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );

        return submissionService.grade(
                assignmentId,
                submissionId,
                request,
                Long.valueOf(jwt.getSubject()),
                isAdmin
        );
    }

}

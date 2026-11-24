package org.courseflow.learning.controller;

import org.springframework.http.HttpStatus;

import java.net.URI;

import org.courseflow.security.CurrentUser;
import jakarta.validation.Valid;
import org.courseflow.learning.dto.CreateSubmissionRequest;
import org.courseflow.learning.dto.SubmissionResponse;
import org.courseflow.learning.service.SubmissionService;
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
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<SubmissionResponse> submit(
            @PathVariable Long assignmentId,
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SubmissionResponse response = submissionService.submit(
                assignmentId,
                CurrentUser.id(jwt),
                request.content()
        );

        return ResponseEntity
                .created(URI.create("/api/assignments/" + assignmentId + "/submissions/me"))
                .body(response);
    }

    @GetMapping("/me")
    public SubmissionResponse getMySubmission(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return submissionService.getMySubmission(
                assignmentId,
                CurrentUser.id(jwt)
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
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        return submissionService.findByAssignment(
                assignmentId,
                CurrentUser.id(jwt),
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
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        return submissionService.grade(
                assignmentId,
                submissionId,
                request,
                CurrentUser.id(jwt),
                isAdmin
        );
    }

}

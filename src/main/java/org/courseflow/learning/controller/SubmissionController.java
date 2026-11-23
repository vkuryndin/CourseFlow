
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
}

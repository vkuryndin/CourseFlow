package org.courseflow.learning.controller;

import org.springframework.http.HttpStatus;

import java.net.URI;

import org.courseflow.security.CurrentUser;
import jakarta.validation.Valid;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.AssignmentResponse;
import org.courseflow.learning.dto.CreateAssignmentRequest;
import org.courseflow.learning.service.AssignmentService;
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
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AssignmentResponse> create(
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Long currentUserId = CurrentUser.id(jwt);

        boolean isAdmin = CurrentUser.isAdmin(authentication);

        AssignmentResponse response = assignmentService.create(
                lessonId,
                request,
                currentUserId,
                isAdmin
        );

        return ResponseEntity
                .created(URI.create("/api/lessons/" + lessonId + "/assignments/" + response.id()))
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
                CurrentUser.id(jwt),
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
                CurrentUser.id(jwt),
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
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        return assignmentService.update(
                lessonId,
                assignmentId,
                request,
                CurrentUser.id(jwt),
                isAdmin
        );
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @PathVariable Long lessonId,
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        assignmentService.delete(
                lessonId,
                assignmentId,
                CurrentUser.id(jwt),
                isAdmin
        );

        return ResponseEntity.noContent().build();
    }

}

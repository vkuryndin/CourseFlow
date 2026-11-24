package org.courseflow.content.controller;

import org.courseflow.security.CurrentUser;
import jakarta.validation.Valid;
import org.courseflow.common.api.PageResponse;
import org.courseflow.content.dto.CreateLessonSessionRequest;
import org.courseflow.content.dto.LessonSessionResponse;
import org.courseflow.content.service.LessonSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.courseflow.content.dto.RescheduleLessonSessionRequest;
import java.net.URI;

@RestController
@RequestMapping("/api/lessons/{lessonId}/sessions")
public class LessonSessionController {

    private final LessonSessionService sessionService;

    public LessonSessionController(
            LessonSessionService sessionService
    ) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<LessonSessionResponse> create(
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateLessonSessionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        LessonSessionResponse response = sessionService.create(
                lessonId,
                request,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );

        URI location = URI.create(
                "/api/lessons/" + lessonId
                        + "/sessions/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping
    public PageResponse<LessonSessionResponse> findByLesson(
            @PathVariable Long lessonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return sessionService.findByLesson(
                lessonId, page, size, CurrentUser.id(jwt), authentication
        );
    }

    @GetMapping("/{sessionId}")
    public LessonSessionResponse getById(
            @PathVariable Long lessonId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return sessionService.getById(
                lessonId, sessionId, CurrentUser.id(jwt), authentication
        );
    }

    @PutMapping("/{sessionId}")
    public LessonSessionResponse update(
            @PathVariable Long lessonId,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateLessonSessionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return sessionService.update(
                lessonId,
                sessionId,
                request,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );
    }

    @PatchMapping("/{sessionId}/time")
    public LessonSessionResponse reschedule(
            @PathVariable Long lessonId,
            @PathVariable Long sessionId,
            @Valid @RequestBody RescheduleLessonSessionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return sessionService.reschedule(
                lessonId,
                sessionId,
                request,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long lessonId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        sessionService.delete(
                lessonId,
                sessionId,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

}

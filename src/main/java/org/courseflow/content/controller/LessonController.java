
package org.courseflow.content.controller;

import jakarta.validation.Valid;
import org.courseflow.common.api.PageResponse;
import org.courseflow.content.dto.CreateLessonRequest;
import org.courseflow.content.dto.LessonResponse;
import org.courseflow.content.service.LessonService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.courseflow.content.dto.UpdateLessonRequest;
import org.courseflow.content.dto.ChangeLessonPositionRequest;
import org.courseflow.content.service.ContentAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;

@RestController
@RequestMapping(
        "/api/courses/{courseId}/modules/{moduleId}/lessons"
)
public class LessonController {

    private final LessonService lessonService;
    private final ContentAccessService contentAccessService;

    public LessonController(
            LessonService lessonService,
            ContentAccessService contentAccessService
    ) {
        this.lessonService = lessonService;
        this.contentAccessService = contentAccessService;
    }

    @PostMapping
    public ResponseEntity<LessonResponse> create(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateLessonRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        LessonResponse response = lessonService.create(
                courseId,
                moduleId,
                request,
                Long.valueOf(jwt.getSubject()),
                isAdmin(authentication)
        );

        URI location = URI.create(
                "/api/courses/" + courseId
                        + "/modules/" + moduleId
                        + "/lessons/" + response.id()
        );

        return ResponseEntity.created(location).body(response);
    }


    @GetMapping
    public PageResponse<LessonResponse> findByModule(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        contentAccessService.checkCourseAccess(
                courseId,
                Long.valueOf(jwt.getSubject()),
                authentication
        );

        return lessonService.findByModule(
                courseId, moduleId, page, size
        );
    }


    @GetMapping("/{lessonId}")
    public LessonResponse getById(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        contentAccessService.checkCourseAccess(
                courseId,
                Long.valueOf(jwt.getSubject()),
                authentication
        );

        return lessonService.getById(
                courseId, moduleId, lessonId
        );
    }


    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                );
    }

    @PutMapping("/{lessonId}")
    public LessonResponse update(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @Valid @RequestBody UpdateLessonRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return lessonService.update(
                courseId,
                moduleId,
                lessonId,
                request,
                Long.valueOf(jwt.getSubject()),
                isAdmin(authentication)
        );
    }

    @PatchMapping("/{lessonId}/position")
    public LessonResponse changePosition(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @Valid @RequestBody ChangeLessonPositionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return lessonService.changePosition(
                courseId,
                moduleId,
                lessonId,
                request,
                Long.valueOf(jwt.getSubject()),
                isAdmin(authentication)
        );
    }

    @DeleteMapping("/{lessonId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        lessonService.delete(
                courseId,
                moduleId,
                lessonId,
                Long.valueOf(jwt.getSubject()),
                isAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

}

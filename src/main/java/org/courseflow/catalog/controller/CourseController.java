package org.courseflow.catalog.controller;

import org.springframework.http.HttpStatus;

import org.courseflow.security.CurrentUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.courseflow.catalog.dto.CourseResponse;
import org.courseflow.catalog.dto.CreateCourseRequest;
import org.courseflow.catalog.dto.UpdateCourseRequest;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.service.CourseService;
import org.courseflow.common.api.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.dto.ReassignCourseTeacherRequest;
import java.net.URI;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private static final Logger LOG = LoggerFactory.getLogger(CourseController.class);
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CourseResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody CreateCourseRequest request
    ) {
        CourseResponse response = courseService.create(
                request,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );

        return ResponseEntity
                .created(
                        URI.create("/api/courses/" + response.id())
                )
                .body(response);
    }

    @PutMapping("/{id}")
    public CourseResponse update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody UpdateCourseRequest request
    ) {
        return courseService.update(
                id,
                request,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );
    }

    @GetMapping("/{id}")
    public CourseResponse getById(
            @PathVariable Long id
    ) {
        return courseService.getById(id);
    }

    @GetMapping
    public PageResponse<CourseResponse> findAll(
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size,

            @RequestParam(defaultValue = "")
            String q,

            @RequestParam(required = false)
            Long categoryId,

            @RequestParam(required = false)
            Long teacherId,

            @RequestParam(required = false)
            CourseStatus status,

            @RequestParam(defaultValue = "id")
            String sortBy,

            @RequestParam(defaultValue = "asc")
            String direction
    ) {
        return courseService.findAll(
                page,
                size,
                q,
                categoryId,
                teacherId,
                status,
                sortBy,
                direction
        );
    }

    @PostMapping("/{id}/archive")
    public CourseResponse archive(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return courseService.archive(
                id,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        courseService.delete(
                id,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> addTag(
            @PathVariable Long courseId,
            @PathVariable Long tagId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        courseService.addTagToCourse(
                courseId,
                tagId,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{courseId}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> removeTag(
            @PathVariable Long courseId,
            @PathVariable Long tagId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        courseService.removeTagFromCourse(
                courseId,
                tagId,
                CurrentUser.id(jwt),
                CurrentUser.isAdmin(authentication)
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}/tags")
    public PageResponse<TagResponse> getCourseTags(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return courseService.findCourseTags(
                courseId, page, size
        );
    }

    @PutMapping("/{courseId}/teacher")
    public CourseResponse reassignTeacher(
            @PathVariable Long courseId,
            @Valid @RequestBody ReassignCourseTeacherRequest request,
            Authentication authentication
    ) {
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        CourseResponse response = courseService.reassignTeacher(
                courseId,
                request.teacherId(),
                request.version(),
                isAdmin
        );
        LOG.info("admin_action=reassign_teacher actor={} course={} teacher={}",
                authentication.getName(), courseId, request.teacherId());
        return response;
    }

}

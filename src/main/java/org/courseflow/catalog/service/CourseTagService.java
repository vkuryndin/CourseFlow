package org.courseflow.catalog.service;

import org.courseflow.common.api.Pagination;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.catalog.repository.TagRepository;

@Service
public class CourseTagService {
    private final CourseRepository courseRepository;
    private final TagRepository tagRepository;
    private final CourseGuard courseGuard;

    public CourseTagService(CourseRepository courseRepository, TagRepository tagRepository, CourseGuard courseGuard) {
        this.courseRepository = courseRepository;
        this.tagRepository = tagRepository;
        this.courseGuard = courseGuard;
    }

    @Transactional
    public void addTagToCourse(
            Long courseId,
            Long tagId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = findCourseForTagModification(
                courseId, currentUserId, isAdmin
        );

        Tag tag = tagRepository.findByIdForShare(tagId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Tag not found: " + tagId
                        )
                );

        // Set prevents duplicate links.
        course.addTag(tag);
    }

    @Transactional
    public void removeTagFromCourse(
            Long courseId,
            Long tagId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = findCourseForTagModification(
                courseId, currentUserId, isAdmin
        );

        Tag tag = tagRepository.findByIdForShare(tagId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Tag not found: " + tagId
                        )
                );

        // Removing an absent link is idempotent.
        course.removeTag(tag);
    }

    @Transactional(readOnly = true)
    public PageResponse<TagResponse> findCourseTags(
            Long courseId,
            int page,
            int size
    ) {
        Pagination.validate(page, size);

        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException(
                    "Course not found: " + courseId
            );
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Pagination.stableSort("name", "asc")
        );

        Page<TagResponse> result = tagRepository
                .findByCourseId(courseId, pageable)
                .map(TagResponse::from);

        return PageResponse.from(result);
    }

    private Course findCourseForTagModification(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        return courseGuard.lockEditable(courseId, currentUserId, isAdmin,
                "Cannot modify tags of an archived course");
    }

}

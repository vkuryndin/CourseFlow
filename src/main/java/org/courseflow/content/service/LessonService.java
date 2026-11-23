
package org.courseflow.content.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.dto.CreateLessonRequest;
import org.courseflow.content.dto.LessonResponse;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.ModuleRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.courseflow.content.dto.UpdateLessonRequest;
import org.courseflow.content.dto.ChangeLessonPositionRequest;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final EntityManager entityManager;

    public LessonService(
            LessonRepository lessonRepository,
            ModuleRepository moduleRepository,
            CourseRepository courseRepository,
            EntityManager entityManager
    ) {
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public LessonResponse create(
            Long courseId,
            Long moduleId,
            CreateLessonRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        // Lock the parent course to serialize content changes.
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (!isAdmin
                && !currentUserId.equals(
                course.getTeacher().getId()
        )) {
            throw new AccessDeniedException(
                    "Teacher can manage only own courses"
            );
        }

        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessConflictException(
                    "Cannot add lessons to an archived course"
            );
        }

        Module module = findModule(courseId, moduleId);

        if (lessonRepository.existsByModule_IdAndPosition(
                moduleId, request.position()
        )) {
            throw new BusinessConflictException(
                    "Lesson position is already occupied"
            );
        }

        String videoUrl = request.videoUrl() == null
                || request.videoUrl().isBlank()
                ? null
                : request.videoUrl().trim();

        Lesson lesson = module.addLesson(
                request.title().trim(),
                request.content().trim(),
                videoUrl,
                request.position()
        );

        Lesson saved = lessonRepository.saveAndFlush(lesson);

        return LessonResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> findByModule(
            Long courseId,
            Long moduleId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException(
                    "Invalid pagination parameters"
            );
        }

        findModule(courseId, moduleId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("position").ascending()
                        .and(Sort.by("id").ascending())
        );

        Page<LessonResponse> result = lessonRepository
                .findByModule_Id(moduleId, pageable)
                .map(LessonResponse::from);

        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public LessonResponse getById(
            Long courseId,
            Long moduleId,
            Long lessonId
    ) {
        findModule(courseId, moduleId);

        Lesson lesson = lessonRepository
                .findByIdAndModule_Id(lessonId, moduleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found: " + lessonId
                        )
                );

        return LessonResponse.from(lesson);
    }

    private Module findModule(
            Long courseId,
            Long moduleId
    ) {
        return moduleRepository
                .findByIdAndCourse_Id(moduleId, courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Module not found: " + moduleId
                        )
                );
    }

    @Transactional
    public LessonResponse update(
            Long courseId,
            Long moduleId,
            Long lessonId,
            UpdateLessonRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        findEditableCourse(courseId, currentUserId, isAdmin);
        findModule(courseId, moduleId);

        Lesson lesson = findLesson(moduleId, lessonId);

        String videoUrl = request.videoUrl() == null
                || request.videoUrl().isBlank()
                ? null
                : request.videoUrl().trim();

        lesson.updateDetails(
                request.title().trim(),
                request.content().trim(),
                videoUrl
        );

        return LessonResponse.from(lesson);
    }

    @Transactional
    public LessonResponse changePosition(
            Long courseId,
            Long moduleId,
            Long lessonId,
            ChangeLessonPositionRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        findEditableCourse(courseId, currentUserId, isAdmin);
        findModule(courseId, moduleId);

        Lesson lesson = findLesson(moduleId, lessonId);

        int oldPosition = lesson.getPosition();
        int newPosition = request.position();

        if (oldPosition == newPosition) {
            return LessonResponse.from(lesson);
        }

        var occupying = lessonRepository
                .findByModule_IdAndPosition(moduleId, newPosition);

        if (occupying.isPresent()) {
            Lesson other = occupying.get();

            int maxPosition = lessonRepository
                    .findFirstByModule_IdOrderByPositionDescIdDesc(moduleId)
                    .map(Lesson::getPosition)
                    .orElse(0);

            if (maxPosition == Integer.MAX_VALUE) {
                throw new BusinessConflictException(
                        "Cannot reorder lessons: position limit reached"
                );
            }

            // Temporarily move the other lesson to a free position.
            other.changePosition(maxPosition + 1);
            lessonRepository.flush();

            // Move the requested lesson to its new position.
            lesson.changePosition(newPosition);
            lessonRepository.flush();

            // Move the other lesson to the vacated position.
            other.changePosition(oldPosition);
            lessonRepository.flush();

        } else {
            lesson.changePosition(newPosition);
            lessonRepository.flush();
        }

        return LessonResponse.from(lesson);
    }


    @Transactional
    public void delete(
            Long courseId,
            Long moduleId,
            Long lessonId,
            Long currentUserId,
            boolean isAdmin
    ) {
        findEditableCourse(courseId, currentUserId, isAdmin);

        Module module = findModule(courseId, moduleId);
        Lesson lesson = findLesson(moduleId, lessonId);

        Long sessionCount = entityManager.createQuery(
                        """
                        SELECT COUNT(s)
                        FROM LessonSession s
                        WHERE s.lesson.id = :lessonId
                        """,
                        Long.class
                )
                .setParameter("lessonId", lessonId)
                .getSingleResult();

        Long assignmentCount = entityManager.createQuery(
                        """
                        SELECT COUNT(a)
                        FROM Assignment a
                        WHERE a.lesson.id = :lessonId
                        """,
                        Long.class
                )
                .setParameter("lessonId", lessonId)
                .getSingleResult();

        if (sessionCount > 0 || assignmentCount > 0) {
            throw new BusinessConflictException(
                    "Lesson has sessions or assignments "
                            + "and cannot be deleted"
            );
        }

        try {
            module.removeLesson(lesson);
            lessonRepository.delete(lesson);
            lessonRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Lesson has related records and cannot be deleted"
            );
        }
    }


    private Lesson findLesson(
            Long moduleId,
            Long lessonId
    ) {
        return lessonRepository
                .findByIdAndModule_Id(lessonId, moduleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found: " + lessonId
                        )
                );
    }

    private Course findEditableCourse(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (!isAdmin
                && !currentUserId.equals(
                course.getTeacher().getId()
        )) {
            throw new AccessDeniedException(
                    "Teacher can manage only own courses"
            );
        }

        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessConflictException(
                    "Cannot modify lessons of archived course"
            );
        }

        return course;
    }



}

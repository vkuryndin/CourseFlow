package org.courseflow.content.service;

import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.dto.CreateModuleRequest;
import org.courseflow.content.dto.ModuleResponse;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.ModuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.courseflow.content.dto.UpdateModuleRequest;
import org.courseflow.content.dto.ChangeModulePositionRequest;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final EntityManager entityManager;

    public ModuleService(
            ModuleRepository moduleRepository,
            CourseRepository courseRepository,
            EntityManager entityManager
    ) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ModuleResponse create(
            Long courseId,
            CreateModuleRequest request,
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

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Teacher can manage only own courses");

        CourseAccessPolicy.requireActive(course,
                "Cannot add modules to archived course");

        if (moduleRepository.existsByCourse_IdAndPosition(
                courseId, request.position()
        )) {
            throw new BusinessConflictException(
                    "Module position is already occupied"
            );
        }

        String title = request.title().trim();

        String description = request.description() == null
                ? null
                : request.description().trim();

        Module module = course.addModule(
                title,
                description,
                request.position()
        );

        Module saved = moduleRepository.saveAndFlush(module);

        return ModuleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ModuleResponse> findByCourse(
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
                Sort.by("position").ascending()
                        .and(Sort.by("id").ascending())
        );

        Page<ModuleResponse> result = moduleRepository
                .findByCourse_Id(courseId, pageable)
                .map(ModuleResponse::from);

        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ModuleResponse getById(
            Long courseId,
            Long moduleId
    ) {
        return ModuleResponse.from(
                findModule(courseId, moduleId)
        );
    }

    @Transactional
    public ModuleResponse update(
            Long courseId,
            Long moduleId,
            UpdateModuleRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        findEditableCourse(courseId, currentUserId, isAdmin);

        Module module = findModule(courseId, moduleId);

        String description = request.description() == null
                ? null
                : request.description().trim();

        module.updateDetails(
                request.title().trim(),
                description
        );

        return ModuleResponse.from(module);
    }

    @Transactional
    public ModuleResponse changePosition(
            Long courseId,
            Long moduleId,
            ChangeModulePositionRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        findEditableCourse(courseId, currentUserId, isAdmin);

        Module module = findModule(courseId, moduleId);

        int oldPosition = module.getPosition();
        int newPosition = request.position();

        if (oldPosition == newPosition) {
            return ModuleResponse.from(module);
        }

        var occupying = moduleRepository
                .findByCourse_IdAndPosition(courseId, newPosition);

        if (occupying.isPresent()) {
            Module other = occupying.get();

            int maxPosition = moduleRepository
                    .findMaxPositionByCourseId(courseId);

            if (maxPosition == Integer.MAX_VALUE) {
                throw new BusinessConflictException(
                        "Cannot reorder modules: position limit reached"
                );
            }

            // Move the other module to a temporary free position.
            other.changePosition(maxPosition + 1);
            moduleRepository.flush();

            // Move the requested module to its target position.
            module.changePosition(newPosition);
            moduleRepository.flush();

            // Return the other module to the vacated position.
            other.changePosition(oldPosition);
            moduleRepository.flush();
        } else {
            module.changePosition(newPosition);
            moduleRepository.flush();
        }

        return ModuleResponse.from(module);
    }

    @Transactional
    public void delete(
            Long courseId,
            Long moduleId,
            Long currentUserId,
            boolean isAdmin
    ) {
        findEditableCourse(courseId, currentUserId, isAdmin);

        Module module = findModule(courseId, moduleId);

        Long lessonCount = entityManager.createQuery(
                        """
                        SELECT COUNT(l)
                        FROM Lesson l
                        WHERE l.module.id = :moduleId
                        """,
                        Long.class
                )
                .setParameter("moduleId", moduleId)
                .getSingleResult();

        Long quizCount = entityManager.createQuery(
                        """
                        SELECT COUNT(q)
                        FROM Quiz q
                        WHERE q.module.id = :moduleId
                        """,
                        Long.class
                )
                .setParameter("moduleId", moduleId)
                .getSingleResult();

        if (lessonCount > 0 || quizCount > 0) {
            throw new BusinessConflictException(
                    "Module has lessons or quiz and cannot be deleted"
            );
        }

        try {
            moduleRepository.delete(module);
            moduleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Module has related records and cannot be deleted"
            );
        }
    }

    private Module findModule(Long courseId, Long moduleId) {
        return moduleRepository
                .findByIdAndCourse_Id(moduleId, courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Module not found: " + moduleId
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

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Teacher can manage only own courses");

        CourseAccessPolicy.requireActive(course,
                "Cannot modify an archived course");

        return course;
    }

}

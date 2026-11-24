package org.courseflow.content.service;

import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.EditVersion;
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
    private final CourseGuard courseGuard;
    private final EntityManager entityManager;

    public ModuleService(
            ModuleRepository moduleRepository,
            CourseRepository courseRepository,
            CourseGuard courseGuard,
            EntityManager entityManager
    ) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.courseGuard = courseGuard;
        this.entityManager = entityManager;
    }

    @Transactional
    public ModuleResponse create(
            Long courseId,
            CreateModuleRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseGuard.lockEditable(courseId, currentUserId, isAdmin,
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
                Pagination.stableSort("position", "asc")
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

        EditVersion.require(request.version(), module.getVersion());

        module.updateDetails(
                request.title().trim(),
                description
        );

        moduleRepository.flush();
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

        EditVersion.require(request.version(), module.getVersion());

        int oldPosition = module.getPosition();
        int newPosition = request.position();

        if (oldPosition == newPosition) {
            return ModuleResponse.from(module);
        }

        var affected = moduleRepository.findByCourse_IdAndPositionBetweenOrderByPositionAsc(
                courseId, Math.min(oldPosition, newPosition), Math.max(oldPosition, newPosition));
        PositionMover.move(module, oldPosition, newPosition, affected,
                Module::getPosition, Module::changePosition,
                () -> moduleRepository.findMaxPositionByCourseId(courseId),
                moduleRepository::flush);

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
        Course course = courseGuard.lockEditable(courseId, currentUserId, isAdmin,
                "Cannot modify an archived course");

        return course;
    }

}

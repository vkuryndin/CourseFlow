package org.courseflow.learning.service;

import org.courseflow.common.exception.EditVersion;
import java.time.Clock;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import org.springframework.transaction.annotation.Isolation;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.service.ContentAccessService;
import org.courseflow.learning.dto.AssignmentResponse;
import org.courseflow.learning.dto.CreateAssignmentRequest;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.repository.AssignmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Set;

@Service
public class AssignmentService {

    private final Clock clock;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "dueAt", "maxScore");

    private final AssignmentRepository assignmentRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final ContentAccessService contentAccessService;
    private final EntityManager entityManager;

    public AssignmentService(
            AssignmentRepository assignmentRepository,
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            ContentAccessService contentAccessService,
            EntityManager entityManager,
            Clock clock
    ) {
        this.clock = clock;
        this.assignmentRepository = assignmentRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.contentAccessService = contentAccessService;
        this.entityManager = entityManager;
    }

    @Transactional
    public AssignmentResponse create(
            Long lessonId,
            CreateAssignmentRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found: " + lessonId
                        )
                );

        Long courseId = lesson.getModule()
                .getCourse()
                .getId();

        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Only course teacher or ADMIN can create assignments");

        CourseAccessPolicy.requireActive(course,
                "Cannot create assignment in archived course");

        requireFutureDeadline(request);

        Assignment assignment = new Assignment(
                lesson,
                request.title().trim(),
                request.description().trim(),
                request.dueAt(),
                request.maxScore()
        );

        Assignment saved =
                assignmentRepository.saveAndFlush(assignment);

        return AssignmentResponse.from(saved);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AssignmentResponse getById(
            Long lessonId,
            Long assignmentId,
            Long currentUserId,
            Authentication authentication
    ) {
        contentAccessService.checkLessonAccess(
                lessonId,
                currentUserId,
                authentication
        );

        Assignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assignment not found: " + assignmentId
                        )
                );

        if (!assignment.getLesson().getId().equals(lessonId)) {
            throw new ResourceNotFoundException(
                    "Assignment not found in lesson: " + lessonId
            );
        }

        return AssignmentResponse.from(assignment);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AssignmentResponse> findByLesson(
            Long lessonId,
            Long currentUserId,
            Authentication authentication,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        contentAccessService.checkLessonAccess(
                lessonId,
                currentUserId,
                authentication
        );

        Pagination.validate(page, size);

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        }

        String normalizedDirection = Pagination.direction(direction);

        Sort.Direction sortDirection =
                normalizedDirection.equals("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort sort = Sort.by(sortDirection, sortBy);

        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        PageRequest pageable =
                PageRequest.of(page, size, sort);

        Page<AssignmentResponse> result =
                assignmentRepository
                        .findAllByLesson_Id(lessonId, pageable)
                        .map(AssignmentResponse::from);

        return PageResponse.from(result);
    }

    @Transactional
    public AssignmentResponse update(
            Long lessonId,
            Long assignmentId,
            CreateAssignmentRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        Assignment assignment = findEditableAssignment(
                lessonId,
                assignmentId,
                currentUserId,
                isAdmin
        );

        requireFutureDeadline(request);

        EditVersion.require(request.version(), assignment.getVersion());

        assignment.updateDetails(
                request.title().trim(),
                request.description().trim(),
                request.dueAt(),
                request.maxScore()
        );

        assignmentRepository.flush();
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public void delete(
            Long lessonId,
            Long assignmentId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Assignment assignment = findEditableAssignment(
                lessonId,
                assignmentId,
                currentUserId,
                isAdmin
        );

        try {
            assignmentRepository.delete(assignment);
            assignmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Assignment has related records and cannot be deleted"
            );
        }
    }

    private Assignment findEditableAssignment(
            Long lessonId,
            Long assignmentId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found: " + lessonId
                        )
                );

        Long courseId = lesson.getModule()
                .getCourse()
                .getId();

        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Only course teacher or ADMIN can manage assignments");

        CourseAccessPolicy.requireActive(course,
                "Cannot modify assignment in archived course");

        Assignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assignment not found: " + assignmentId
                        )
                );

        if (!assignment.getLesson().getId().equals(lessonId)) {
            throw new ResourceNotFoundException(
                    "Assignment not found in lesson: " + lessonId
            );
        }

        if (hasSubmissions(assignmentId)) {
            throw new BusinessConflictException(
                    "Assignment already has student submissions"
            );
        }

        return assignment;
    }

    private void requireFutureDeadline(CreateAssignmentRequest request) {
        if (!request.dueAt().isAfter(clock.instant())) {
            throw new BadRequestException("Assignment deadline must be in the future");
        }
    }

    private boolean hasSubmissions(Long assignmentId) {
        Long count = entityManager.createQuery(
                        """
                        SELECT COUNT(s)
                        FROM Submission s
                        WHERE s.assignment.id = :assignmentId
                        """,
                        Long.class
                )
                .setParameter("assignmentId", assignmentId)
                .getSingleResult();

        return count > 0;
    }

}

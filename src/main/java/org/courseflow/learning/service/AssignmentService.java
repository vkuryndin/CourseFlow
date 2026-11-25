package org.courseflow.learning.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Set;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.service.ContentAccessService;
import org.courseflow.learning.dto.AssignmentResponse;
import org.courseflow.learning.dto.CreateAssignmentRequest;
import org.courseflow.learning.dto.ExtendAssignmentDeadlineRequest;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.repository.AssignmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

  private final Clock clock;

  private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "title", "dueAt", "maxScore");

  private final AssignmentRepository assignmentRepository;
  private final ContentStructure contentStructure;
  private final CourseGuard courseGuard;
  private final ContentAccessService contentAccessService;
  private final EntityManager entityManager;

  public AssignmentService(
      AssignmentRepository assignmentRepository,
      ContentStructure contentStructure,
      CourseGuard courseGuard,
      ContentAccessService contentAccessService,
      EntityManager entityManager,
      Clock clock) {
    this.clock = clock;
    this.assignmentRepository = assignmentRepository;
    this.contentStructure = contentStructure;
    this.courseGuard = courseGuard;
    this.contentAccessService = contentAccessService;
    this.entityManager = entityManager;
  }

  @Transactional
  public AssignmentResponse create(
      Long lessonId, CreateAssignmentRequest request, Long currentUserId, boolean isAdmin) {
    Lesson lesson = contentStructure.requireLesson(lessonId);

    Long courseId = lesson.getModule().getCourse().getId();

    Course course = courseGuard.lock(courseId);

    CourseAccessPolicy.requireOwnerOrAdmin(
        course, currentUserId, isAdmin, "Only course teacher or ADMIN can create assignments");

    CourseAccessPolicy.requireActive(course, "Cannot create assignment in archived course");

    requireFutureDeadline(request);

    Assignment assignment =
        new Assignment(
            lesson,
            request.title().trim(),
            request.description().trim(),
            request.dueAt(),
            request.maxScore());

    Assignment saved = assignmentRepository.saveAndFlush(assignment);

    return AssignmentResponse.from(saved);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AssignmentResponse getById(
      Long lessonId, Long assignmentId, Long currentUserId, Authentication authentication) {
    contentAccessService.checkLessonAccess(lessonId, currentUserId, authentication);

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

    if (!assignment.getLesson().getId().equals(lessonId)) {
      throw new ResourceNotFoundException("Assignment not found in lesson: " + lessonId);
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
      String direction) {
    contentAccessService.checkLessonAccess(lessonId, currentUserId, authentication);

    Pagination.validate(page, size);

    Pagination.requireSortField(sortBy, ALLOWED_SORT_FIELDS, "Unsupported sort field: " + sortBy);

    Sort sort = Pagination.stableSort(sortBy, direction);

    PageRequest pageable = PageRequest.of(page, size, sort);

    Page<AssignmentResponse> result =
        assignmentRepository.findAllByLesson_Id(lessonId, pageable).map(AssignmentResponse::from);

    return PageResponse.from(result);
  }

  @Transactional
  public AssignmentResponse update(
      Long lessonId,
      Long assignmentId,
      CreateAssignmentRequest request,
      Long currentUserId,
      boolean isAdmin) {
    Assignment assignment = findManagedAssignment(lessonId, assignmentId, currentUserId, isAdmin);
    requireNoSubmissions(assignmentId);

    requireFutureDeadline(request);

    EditVersion.require(request.version(), assignment.getVersion());

    assignment.updateDetails(
        request.title().trim(), request.description().trim(), request.dueAt(), request.maxScore());

    assignmentRepository.flush();
    return AssignmentResponse.from(assignment);
  }

  /** Extend the common deadline without changing existing submissions or grades. */
  @Transactional
  public AssignmentResponse extendDeadline(
      Long lessonId,
      Long assignmentId,
      ExtendAssignmentDeadlineRequest request,
      Long currentUserId,
      boolean isAdmin) {
    Assignment assignment = findManagedAssignment(lessonId, assignmentId, currentUserId, isAdmin);
    EditVersion.require(request.version(), assignment.getVersion());
    if (request.dueAt() == null || !request.dueAt().isAfter(clock.instant())) {
      throw new BadRequestException("Assignment deadline must be in the future");
    }
    if (!request.dueAt().isAfter(assignment.getDueAt())) {
      throw new BadRequestException("New deadline must be later than the current deadline");
    }
    assignment.extendDeadline(request.dueAt());
    assignmentRepository.flush();
    return AssignmentResponse.from(assignment);
  }

  @Transactional
  public void delete(Long lessonId, Long assignmentId, Long currentUserId, boolean isAdmin) {
    Assignment assignment = findManagedAssignment(lessonId, assignmentId, currentUserId, isAdmin);
    requireNoSubmissions(assignmentId);

    try {
      assignmentRepository.delete(assignment);
      assignmentRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessConflictException("Assignment has related records and cannot be deleted");
    }
  }

  private Assignment findManagedAssignment(
      Long lessonId, Long assignmentId, Long currentUserId, boolean isAdmin) {
    Lesson lesson = contentStructure.requireLesson(lessonId);

    Long courseId = lesson.getModule().getCourse().getId();

    Course course = courseGuard.lock(courseId);

    CourseAccessPolicy.requireOwnerOrAdmin(
        course, currentUserId, isAdmin, "Only course teacher or ADMIN can manage assignments");

    CourseAccessPolicy.requireActive(course, "Cannot modify assignment in archived course");

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

    if (!assignment.getLesson().getId().equals(lessonId)) {
      throw new ResourceNotFoundException("Assignment not found in lesson: " + lessonId);
    }

    return assignment;
  }

  private void requireNoSubmissions(Long assignmentId) {
    if (hasSubmissions(assignmentId)) {
      throw new BusinessConflictException("Assignment already has student submissions");
    }
  }

  private void requireFutureDeadline(CreateAssignmentRequest request) {
    if (!request.dueAt().isAfter(clock.instant())) {
      throw new BadRequestException("Assignment deadline must be in the future");
    }
  }

  private boolean hasSubmissions(Long assignmentId) {
    Long count =
        entityManager
            .createQuery(
                """
                        SELECT COUNT(s)
                        FROM Submission s
                        WHERE s.assignment.id = :assignmentId
                        """,
                Long.class)
            .setParameter("assignmentId", assignmentId)
            .getSingleResult();

    return count > 0;
  }
}

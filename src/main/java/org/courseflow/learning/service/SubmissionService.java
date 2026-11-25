package org.courseflow.learning.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.dto.GradeSubmissionRequest;
import org.courseflow.learning.dto.SubmissionResponse;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.repository.AssignmentRepository;
import org.courseflow.learning.repository.SubmissionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

  private final Clock clock;
  private final GradeAudit gradeAudit;

  private final SubmissionRepository submissionRepository;
  private final AssignmentRepository assignmentRepository;
  private final EnrollmentAccess enrollmentAccess;
  private final CourseGuard courseGuard;
  private final StudentAccess studentAccess;
  private final EntityManager entityManager;

  public SubmissionService(
      SubmissionRepository submissionRepository,
      AssignmentRepository assignmentRepository,
      EnrollmentAccess enrollmentAccess,
      CourseGuard courseGuard,
      StudentAccess studentAccess,
      EntityManager entityManager,
      Clock clock,
      GradeAudit gradeAudit) {
    this.clock = clock;
    this.gradeAudit = gradeAudit;
    this.submissionRepository = submissionRepository;
    this.assignmentRepository = assignmentRepository;
    this.enrollmentAccess = enrollmentAccess;
    this.courseGuard = courseGuard;
    this.studentAccess = studentAccess;
    this.entityManager = entityManager;
  }

  @Transactional
  public SubmissionResponse submit(Long assignmentId, Long studentId, String content) {
    // Read only the ID before locking, not an Assignment entity.
    Long courseId = findCourseId(assignmentId);

    // Shared course lock conflicts with Assignment update/delete.
    Course course = courseGuard.lockShared(courseId);

    CourseAccessPolicy.requireActive(course, "Cannot submit work to archived course");

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

    if (course.getTeacher().getId().equals(studentId)) {
      throw new BusinessConflictException("Teacher cannot submit work for own course");
    }

    final User student = studentAccess.lockActive(studentId);

    enrollmentAccess.requireActive(
        studentId, courseId, "Active enrollment is required to submit work");

    if (!clock.instant().isBefore(assignment.getDueAt())) {
      throw new BusinessConflictException("Assignment submission deadline has passed");
    }

    if (findStudentSubmission(assignmentId, studentId).isPresent()) {
      throw new BusinessConflictException("Assignment has already been submitted");
    }

    Submission submission = new Submission(assignment, student, content.trim(), clock.instant());

    try {
      Submission saved = submissionRepository.saveAndFlush(submission);

      return SubmissionResponse.from(saved);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessConflictException("Submission conflicts with existing data");
    }
  }

  @Transactional(readOnly = true)
  public SubmissionResponse getMySubmission(Long assignmentId, Long studentId) {
    if (!assignmentRepository.existsById(assignmentId)) {
      throw new ResourceNotFoundException("Assignment not found: " + assignmentId);
    }

    Submission submission =
        findStudentSubmission(assignmentId, studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

    return SubmissionResponse.from(submission);
  }

  private Long findCourseId(Long assignmentId) {
    return assignmentRepository
        .findCourseIdByAssignmentId(assignmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
  }

  private Optional<Submission> findStudentSubmission(Long assignmentId, Long studentId) {
    return submissionRepository.findByAssignment_IdAndStudent_Id(assignmentId, studentId);
  }

  @Transactional(readOnly = true)
  public PageResponse<SubmissionResponse> findByAssignment(
      Long assignmentId,
      Long currentUserId,
      boolean isAdmin,
      SubmissionStatus status,
      int page,
      int size,
      String sortBy,
      String direction) {
    Pagination.validate(page, size);

    String sortField =
        switch (sortBy) {
          case "id" -> "s.id";
          case "submittedAt" -> "s.submittedAt";
          case "status" -> "s.status";
          case "score" -> "s.score";
          default -> throw new BadRequestException("Unsupported sort field: " + sortBy);
        };

    String normalizedDirection = Pagination.direction(direction);

    requireAssignmentAccess(assignmentId, currentUserId, isAdmin, false);

    String where =
        """
            FROM Submission s
            WHERE s.assignment.id = :assignmentId
            """;

    if (status != null) {
      where += " AND s.status = :status";
    }

    String orderBy = Pagination.orderBy(sortField, normalizedDirection, "s.id");

    Map<String, Object> parameters = new java.util.HashMap<>();
    parameters.put("assignmentId", assignmentId);
    if (status != null) {
      parameters.put("status", status);
    }
    var result =
        JpqlPages.findPage(
            entityManager,
            Submission.class,
            "SELECT s",
            "COUNT(s)",
            where,
            orderBy,
            parameters,
            page,
            size);
    List<SubmissionResponse> items = result.rows().stream().map(SubmissionResponse::from).toList();
    return new JpqlPages.Result<>(items, result.total(), page, size).response();
  }

  @Transactional
  public SubmissionResponse grade(
      Long assignmentId,
      Long submissionId,
      GradeSubmissionRequest request,
      Long currentUserId,
      boolean isAdmin) {
    Assignment assignment = requireAssignmentAccess(assignmentId, currentUserId, isAdmin, true);

    Submission submission =
        submissionRepository
            .findForGrading(submissionId, assignmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Submission not found: " + submissionId));

    if (!submission.getAssignment().getId().equals(assignmentId)) {
      throw new ResourceNotFoundException("Submission not found for assignment: " + assignmentId);
    }

    int score = request.score();

    if (score > assignment.getMaxScore()) {
      throw new BadRequestException(
          "Score cannot exceed assignment maxScore: " + assignment.getMaxScore());
    }

    String feedback = request.feedback() == null ? null : request.feedback().trim();

    EditVersion.require(request.version(), submission.getVersion());

    Integer previousScore = submission.getScore();
    boolean regrade = submission.getStatus() == SubmissionStatus.GRADED;
    boolean feedbackChanged = !Objects.equals(submission.getFeedback(), feedback);
    submission.grade(score, feedback);

    // Hibernate Dirty Checking saves the changes; audit only committed operations.
    submissionRepository.flush();
    gradeAudit.afterSuccessfulCommit(
        assignmentId,
        submissionId,
        currentUserId,
        previousScore,
        score,
        feedbackChanged,
        regrade,
        submission.getVersion(),
        clock.instant());
    return SubmissionResponse.from(submission);
  }

  private Assignment requireAssignmentAccess(
      Long assignmentId, Long currentUserId, boolean isAdmin, boolean forGrading) {
    Long courseId = findCourseId(assignmentId);

    Course course = (forGrading ? courseGuard.lockShared(courseId) : courseGuard.find(courseId));

    CourseAccessPolicy.requireOwnerOrAdmin(
        course, currentUserId, isAdmin, "Only course teacher or ADMIN can review submissions");

    if (forGrading && course.getStatus() != CourseStatus.ACTIVE) {
      throw new BusinessConflictException("Cannot grade submissions in archived course");
    }

    return assignmentRepository
        .findById(assignmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
  }
}

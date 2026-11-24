
package org.courseflow.learning.service;

import jakarta.persistence.EntityManager;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.dto.SubmissionResponse;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.repository.AssignmentRepository;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.courseflow.learning.repository.SubmissionRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.GradeSubmissionRequest;
import org.courseflow.learning.entity.SubmissionStatus;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Locale;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            AssignmentRepository assignmentRepository,
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public SubmissionResponse submit(
            Long assignmentId,
            Long studentId,
            String content
    ) {
        Assignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assignment not found: " + assignmentId
                        )
                );

        Long courseId = assignment.getLesson()
                .getModule()
                .getCourse()
                .getId();

        // Same course lock as Assignment update/delete.
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Cannot submit work to archived course"
            );
        }

        boolean enrolled = enrollmentRepository
                .findByStudent_IdAndCourse_Id(studentId, courseId)
                .map(enrollment ->
                        enrollment.getStatus() == EnrollmentStatus.ACTIVE
                )
                .orElse(false);

        if (!enrolled) {
            throw new AccessDeniedException(
                    "Active enrollment is required to submit work"
            );
        }

        if (!Instant.now().isBefore(assignment.getDueAt())) {
            throw new BusinessConflictException(
                    "Assignment submission deadline has passed"
            );
        }

        if (findStudentSubmission(assignmentId, studentId).isPresent()) {
            throw new BusinessConflictException(
                    "Assignment has already been submitted"
            );
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Student not found: " + studentId
                        )
                );

        Submission submission = new Submission(
                assignment,
                student,
                content.trim()
        );

        try {
            Submission saved =
                    submissionRepository.saveAndFlush(submission);

            return SubmissionResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Submission conflicts with existing data"
            );
        }
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getMySubmission(
            Long assignmentId,
            Long studentId
    ) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException(
                    "Assignment not found: " + assignmentId
            );
        }

        Submission submission =
                findStudentSubmission(assignmentId, studentId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Submission not found"
                                )
                        );

        return SubmissionResponse.from(submission);
    }

    private Optional<Submission> findStudentSubmission(
            Long assignmentId,
            Long studentId
    ) {
        return entityManager.createQuery(
                        """
                        SELECT s
                        FROM Submission s
                        WHERE s.assignment.id = :assignmentId
                          AND s.student.id = :studentId
                        """,
                        Submission.class
                )
                .setParameter("assignmentId", assignmentId)
                .setParameter("studentId", studentId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
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
            String direction
    ) {
        if (page < 0 || size < 1 || size > 100
                || page > Integer.MAX_VALUE / size) {
            throw new BadRequestException(
                    "Invalid pagination parameters"
            );
        }

        String sortField = switch (sortBy) {
            case "id" -> "s.id";
            case "submittedAt" -> "s.submittedAt";
            case "status" -> "s.status";
            case "score" -> "s.score";
            default -> throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        };

        String normalizedDirection =
                direction.toLowerCase(Locale.ROOT);

        if (!normalizedDirection.equals("asc")
                && !normalizedDirection.equals("desc")) {
            throw new BadRequestException(
                    "Direction must be asc or desc"
            );
        }

        requireAssignmentAccess(
                assignmentId,
                currentUserId,
                isAdmin,
                false
        );

        String where = """
            FROM Submission s
            WHERE s.assignment.id = :assignmentId
            """;

        if (status != null) {
            where += " AND s.status = :status";
        }

        String orderBy = " ORDER BY " + sortField
                + " " + normalizedDirection;

        if (!sortBy.equals("id")) {
            orderBy += ", s.id ASC";
        }

        TypedQuery<Submission> dataQuery =
                entityManager.createQuery(
                        "SELECT s " + where + orderBy,
                        Submission.class
                ).setParameter("assignmentId", assignmentId);

        TypedQuery<Long> countQuery =
                entityManager.createQuery(
                        "SELECT COUNT(s) " + where,
                        Long.class
                ).setParameter("assignmentId", assignmentId);

        if (status != null) {
            dataQuery.setParameter("status", status);
            countQuery.setParameter("status", status);
        }

        List<SubmissionResponse> items = dataQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(SubmissionResponse::from)
                .toList();

        long total = countQuery.getSingleResult();

        return PageResponse.from(
                new PageImpl<>(
                        items,
                        PageRequest.of(page, size),
                        total
                )
        );
    }

    @Transactional
    public SubmissionResponse grade(
            Long assignmentId,
            Long submissionId,
            GradeSubmissionRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        Assignment assignment = requireAssignmentAccess(
                assignmentId,
                currentUserId,
                isAdmin,
                true
        );

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Submission not found: " + submissionId
                        )
                );

        if (!submission.getAssignment().getId()
                .equals(assignmentId)) {
            throw new ResourceNotFoundException(
                    "Submission not found for assignment: "
                            + assignmentId
            );
        }

        int score = request.score();

        if (score > assignment.getMaxScore()) {
            throw new BadRequestException(
                    "Score cannot exceed assignment maxScore: "
                            + assignment.getMaxScore()
            );
        }

        String feedback = request.feedback() == null
                ? null
                : request.feedback().trim();

        submission.grade(score, feedback);

        // Hibernate Dirty Checking saves the changes.
        return SubmissionResponse.from(submission);
    }

    private Assignment requireAssignmentAccess(
            Long assignmentId,
            Long currentUserId,
            boolean isAdmin,
            boolean forGrading
    ) {
        Long courseId = entityManager.createQuery(
                        """
                        SELECT a.lesson.module.course.id
                        FROM Assignment a
                        WHERE a.id = :assignmentId
                        """,
                        Long.class
                )
                .setParameter("assignmentId", assignmentId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assignment not found: " + assignmentId
                        )
                );

        Course course = (
                forGrading
                        ? courseRepository.findByIdForUpdate(courseId)
                        : courseRepository.findById(courseId)
        ).orElseThrow(() ->
                new ResourceNotFoundException(
                        "Course not found: " + courseId
                )
        );

        if (!isAdmin &&
                !course.getTeacher().getId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "Only course teacher or ADMIN can review submissions"
            );
        }

        if (forGrading
                && course.getStatus() != CourseStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Cannot grade submissions in archived course"
            );
        }

        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Assignment not found: " + assignmentId
                        )
                );
    }

}

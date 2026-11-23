
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
}

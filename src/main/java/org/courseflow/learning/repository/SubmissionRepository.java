
package org.courseflow.learning.repository;

import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionRepository
        extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAssignment_IdAndStudent_Id(
            Long assignmentId,
            Long studentId
    );

    Page<Submission> findAllByAssignment_Id(
            Long assignmentId,
            Pageable pageable
    );

    Page<Submission> findAllByStudent_Id(
            Long studentId,
            Pageable pageable
    );

    Page<Submission> findAllByAssignment_IdAndStatus(
            Long assignmentId,
            SubmissionStatus status,
            Pageable pageable
    );

    Page<Submission> findAllByAssignment_Lesson_Module_Course_Id(
            Long courseId,
            Pageable pageable
    );

    boolean existsByAssignment_Id(Long assignmentId);

    long countByStudent_IdAndAssignment_Lesson_Module_Course_Id(
            Long studentId,
            Long courseId
    );
}

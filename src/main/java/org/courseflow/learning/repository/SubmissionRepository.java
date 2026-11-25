package org.courseflow.learning.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

  Optional<Submission> findByAssignment_IdAndStudent_Id(Long assignmentId, Long studentId);

  Page<Submission> findAllByAssignment_Id(Long assignmentId, Pageable pageable);

  Page<Submission> findAllByStudent_Id(Long studentId, Pageable pageable);

  Page<Submission> findAllByAssignment_IdAndStatus(
      Long assignmentId, SubmissionStatus status, Pageable pageable);

  Page<Submission> findAllByAssignment_Lesson_Module_Course_Id(Long courseId, Pageable pageable);

  boolean existsByAssignment_Id(Long assignmentId);

  long countByStudent_IdAndAssignment_Lesson_Module_Course_Id(Long studentId, Long courseId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Submission s where s.id = :id and s.assignment.id = :assignmentId")
  Optional<Submission> findForGrading(
      @Param("id") Long id, @Param("assignmentId") Long assignmentId);
}

package org.courseflow.learning.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.courseflow.learning.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

  Optional<Submission> findByAssignment_IdAndStudent_Id(Long assignmentId, Long studentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Submission s where s.id = :id and s.assignment.id = :assignmentId")
  Optional<Submission> findForGrading(
      @Param("id") Long id, @Param("assignmentId") Long assignmentId);
}

package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.QuizSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizSubmissionRepository
        extends JpaRepository<QuizSubmission, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuizSubmission>
    findFirstByQuiz_IdAndStudent_IdOrderByAttemptNumberDesc(
            Long quizId,
            Long studentId
    );

    Page<QuizSubmission> findAllByStudent_Id(
            Long studentId,
            Pageable pageable
    );

    boolean existsByQuiz_Id(Long quizId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSubmission s where s.id = :id and s.quiz.id = :quizId and s.student.id = :studentId")
    Optional<QuizSubmission> findOwnedForUpdate(@Param("id") Long id,
                                                @Param("quizId") Long quizId,
                                                @Param("studentId") Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSubmission s where s.id = :id and s.quiz.id = :quizId")
    Optional<QuizSubmission> findForUpdate(@Param("id") Long id,
                                           @Param("quizId") Long quizId);

}


package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizSubmissionRepository
        extends JpaRepository<QuizSubmission, Long> {

    Optional<QuizSubmission>
    findFirstByQuiz_IdAndStudent_IdOrderByAttemptNumberDesc(
            Long quizId,
            Long studentId
    );

    Optional<QuizSubmission>
    findFirstByQuiz_IdAndStudent_IdAndStatusOrderByAttemptNumberDesc(
            Long quizId,
            Long studentId,
            QuizSubmissionStatus status
    );

    long countByQuiz_IdAndStudent_Id(
            Long quizId,
            Long studentId
    );

    Page<QuizSubmission> findAllByQuiz_IdAndStudent_Id(
            Long quizId,
            Long studentId,
            Pageable pageable
    );

    Page<QuizSubmission> findAllByQuiz_Id(
            Long quizId,
            Pageable pageable
    );

    Page<QuizSubmission> findAllByStudent_Id(
            Long studentId,
            Pageable pageable
    );

    Page<QuizSubmission> findAllByQuiz_Module_Course_Id(
            Long courseId,
            Pageable pageable
    );

    boolean existsByQuiz_Id(Long quizId);
}

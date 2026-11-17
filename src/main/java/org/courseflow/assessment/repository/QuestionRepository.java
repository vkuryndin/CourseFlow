
package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository
        extends JpaRepository<Question, Long> {

    Page<Question> findAllByQuiz_Id(
            Long quizId,
            Pageable pageable
    );

    List<Question> findAllByQuiz_IdOrderByPositionAscIdAsc(
            Long quizId
    );

    long countByQuiz_Id(Long quizId);
}

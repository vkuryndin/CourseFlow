
package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.AnswerOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerOptionRepository
        extends JpaRepository<AnswerOption, Long> {

    Page<AnswerOption> findAllByQuestion_Id(
            Long questionId,
            Pageable pageable
    );

    List<AnswerOption> findAllByQuestion_IdOrderByIdAsc(
            Long questionId
    );

    List<AnswerOption> findAllByQuestion_Quiz_Id(
            Long quizId
    );
}

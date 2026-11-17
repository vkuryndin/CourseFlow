
package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.QuizAnswerSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizAnswerSelectionRepository
        extends JpaRepository<QuizAnswerSelection, Long> {

    List<QuizAnswerSelection>
    findAllByQuizSubmission_IdOrderByQuestion_PositionAscIdAsc(
            Long quizSubmissionId
    );

    List<QuizAnswerSelection>
    findAllByQuizSubmission_IdAndQuestion_Id(
            Long quizSubmissionId,
            Long questionId
    );
}

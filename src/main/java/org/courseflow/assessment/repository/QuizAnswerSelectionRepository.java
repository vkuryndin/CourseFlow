package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.QuizAnswerSelection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerSelectionRepository
        extends JpaRepository<QuizAnswerSelection, Long> {

}

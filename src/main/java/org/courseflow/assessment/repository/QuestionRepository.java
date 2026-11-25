package org.courseflow.assessment.repository;

import java.util.List;
import java.util.Optional;
import org.courseflow.assessment.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

  long countByQuiz_Id(Long quizId);

  @Query(
      """
        SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.answerOptions
        WHERE q.quiz.id = :quizId ORDER BY q.position ASC, q.id ASC
        """)
  List<Question> findWithOptionsByQuizId(@Param("quizId") Long quizId);

  Optional<Question> findByIdAndQuiz_Id(Long questionId, Long quizId);

  boolean existsByQuiz_IdAndPosition(Long quizId, Integer position);

  boolean existsByQuiz_IdAndPositionAndIdNot(Long quizId, Integer position, Long questionId);
}

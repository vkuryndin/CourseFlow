package org.courseflow.assessment.repository;

import java.util.Optional;
import org.courseflow.assessment.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

  Optional<Quiz> findByModule_Id(Long moduleId);

  boolean existsByModule_Id(Long moduleId);

  @Query("SELECT q.module.course.id FROM Quiz q WHERE q.id = :quizId")
  Optional<Long> findCourseIdByQuizId(@Param("quizId") Long quizId);
}

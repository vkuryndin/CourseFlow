
package org.courseflow.assessment.repository;

import org.courseflow.assessment.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizRepository
        extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByModule_Id(Long moduleId);

    boolean existsByModule_Id(Long moduleId);

    Page<Quiz> findAllByModule_Course_Id(
            Long courseId,
            Pageable pageable
    );
}


package org.courseflow.learning.repository;

import org.courseflow.learning.entity.Assignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository
        extends JpaRepository<Assignment, Long> {

    Page<Assignment> findAllByLesson_Id(
            Long lessonId,
            Pageable pageable
    );

    Page<Assignment> findAllByLesson_Module_Course_Id(
            Long courseId,
            Pageable pageable
    );

    boolean existsByLesson_Id(Long lessonId);
}

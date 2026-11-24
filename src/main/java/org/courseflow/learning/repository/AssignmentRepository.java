
package org.courseflow.learning.repository;

import org.courseflow.learning.entity.Assignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

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

    @Query("""
            SELECT a.lesson.module.course.id
            FROM Assignment a
            WHERE a.id = :assignmentId
            """)
    Optional<Long> findCourseIdByAssignmentId(@Param("assignmentId") Long assignmentId);
}


package org.courseflow.catalog.repository;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseRepository
        extends JpaRepository<Course, Long>,
        JpaSpecificationExecutor<Course> {

    Page<Course> findAllByTeacher_Id(
            Long teacherId,
            Pageable pageable
    );

    Page<Course> findAllByCategory_Id(
            Long categoryId,
            Pageable pageable
    );

    Page<Course> findAllByStatus(
            CourseStatus status,
            Pageable pageable
    );

    boolean existsByCategory_Id(Long categoryId);

    boolean existsByTeacher_Id(Long teacherId);
}

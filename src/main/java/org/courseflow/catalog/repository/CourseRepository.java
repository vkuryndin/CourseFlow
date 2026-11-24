
package org.courseflow.catalog.repository;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
    // Shared lock: independent learning transactions may proceed concurrently.
    // Structural changes and archiving retain PESSIMISTIC_WRITE.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForShare(@Param("id") Long id);
}

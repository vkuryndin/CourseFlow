
package org.courseflow.learning.repository;

import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnrollmentRepository
        extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudent_IdAndCourse_Id(
            Long studentId,
            Long courseId
    );

    Page<Enrollment> findAllByCourse_Id(
            Long courseId,
            Pageable pageable
    );

    Page<Enrollment> findAllByStudent_Id(
            Long studentId,
            Pageable pageable
    );

    Page<Enrollment> findAllByCourse_IdAndStatus(
            Long courseId,
            EnrollmentStatus status,
            Pageable pageable
    );

    Page<Enrollment> findAllByStudent_IdAndStatus(
            Long studentId,
            EnrollmentStatus status,
            Pageable pageable
    );

    boolean existsByCourse_Id(Long courseId);
}

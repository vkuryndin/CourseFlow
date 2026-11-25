package org.courseflow.learning.repository;

import java.util.Optional;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

  Optional<Enrollment> findByStudent_IdAndCourse_Id(Long studentId, Long courseId);

  @EntityGraph(attributePaths = {"student", "course"})
  Page<Enrollment> findAllByCourse_Id(Long courseId, Pageable pageable);

  @EntityGraph(attributePaths = {"student", "course"})
  Page<Enrollment> findAllByCourse_IdAndStatus(
      Long courseId, EnrollmentStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"student", "course"})
  Page<Enrollment> findAllByStudent_Id(Long studentId, Pageable pageable);

  @EntityGraph(attributePaths = {"student", "course"})
  Page<Enrollment> findAllByStudent_IdAndStatus(
      Long studentId, EnrollmentStatus status, Pageable pageable);

  boolean existsByCourse_Id(Long courseId);

  boolean existsByStudent_IdAndStatus(Long studentId, EnrollmentStatus status);

  boolean existsByStudent_IdAndCourse_IdAndStatus(
      Long studentId, Long courseId, EnrollmentStatus status);

  default boolean hasActiveEnrollment(Long studentId, Long courseId) {
    return existsByStudent_IdAndCourse_IdAndStatus(studentId, courseId, EnrollmentStatus.ACTIVE);
  }
}

package org.courseflow.content.repository;

import java.util.List;
import java.util.Optional;
import org.courseflow.content.entity.Module;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleRepository extends JpaRepository<Module, Long> {

  boolean existsByCourse_Id(Long courseId);

  boolean existsByIdAndCourse_Id(Long id, Long courseId);

  Page<Module> findByCourse_Id(Long courseId, Pageable pageable);

  boolean existsByCourse_IdAndPosition(Long courseId, Integer position);

  Optional<Module> findByIdAndCourse_Id(Long id, Long courseId);

  @Query(
      """
        SELECT COALESCE(MAX(m.position), 0)
        FROM Module m
        WHERE m.course.id = :courseId
        """)
  Integer findMaxPositionByCourseId(@Param("courseId") Long courseId);

  List<Module> findByCourse_IdAndPositionBetweenOrderByPositionAsc(
      Long courseId, Integer from, Integer to);
}

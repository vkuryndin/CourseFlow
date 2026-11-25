package org.courseflow.content.repository;

import java.util.List;
import java.util.Optional;
import org.courseflow.content.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

  Page<Lesson> findAllByModule_Id(Long moduleId, Pageable pageable);

  @Query(
      value =
          """
            select new org.courseflow.content.dto.LessonSummaryResponse(l.id, l.version, l.module.id, l.title, l.position)
            from Lesson l where l.module.id = :moduleId
            """,
      countQuery = "select count(l) from Lesson l where l.module.id = :moduleId")
  Page<org.courseflow.content.dto.LessonSummaryResponse> findSummariesByModuleId(
      @Param("moduleId") Long moduleId, Pageable pageable);

  boolean existsByModule_Id(Long moduleId);

  Page<Lesson> findByModule_Id(Long moduleId, Pageable pageable);

  Optional<Lesson> findByIdAndModule_Id(Long id, Long moduleId);

  boolean existsByModule_IdAndPosition(Long moduleId, Integer position);

  Optional<Lesson> findByModule_IdAndPosition(Long moduleId, Integer position);

  Optional<Lesson> findFirstByModule_IdOrderByPositionDescIdDesc(Long moduleId);

  @Query("SELECT l.module.course.id FROM Lesson l WHERE l.id = :lessonId")
  Optional<Long> findCourseId(@Param("lessonId") Long lessonId);

  List<Lesson> findByModule_IdAndPositionBetweenOrderByPositionAsc(
      Long moduleId, Integer from, Integer to);
}

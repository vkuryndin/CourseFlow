package org.courseflow.content.repository;

import java.time.Instant;
import java.util.Optional;
import org.courseflow.content.entity.LessonSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonSessionRepository extends JpaRepository<LessonSession, Long> {

  Page<LessonSession> findAllByLesson_Id(Long lessonId, Pageable pageable);

  Page<LessonSession> findAllByLesson_Module_Course_Id(Long courseId, Pageable pageable);

  Page<LessonSession> findAllByLesson_Module_Course_IdAndStartsAtGreaterThanEqual(
      Long courseId, Instant startsAt, Pageable pageable);

  boolean existsByLesson_Id(Long lessonId);

  boolean existsByLesson_Module_Course_IdAndEndsAtGreaterThan(Long courseId, Instant now);

  Optional<LessonSession> findByIdAndLesson_Id(Long id, Long lessonId);

  @Query(
      """
        SELECT COUNT(s) FROM LessonSession s
        WHERE s.lesson.module.course.teacher.id = :teacherId
          AND s.lesson.module.course.status = org.courseflow.catalog.entity.CourseStatus.ACTIVE
          AND s.id <> :sessionId
          AND s.startsAt < :endsAt AND s.endsAt > :startsAt
        """)
  long countTeacherOverlaps(
      @Param("teacherId") Long teacherId,
      @Param("sessionId") Long sessionId,
      @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt);

  @Query(
      """
        SELECT COUNT(a) FROM LessonSession a, LessonSession b
        WHERE a.lesson.module.course.id = :courseId
          AND b.lesson.module.course.id <> :courseId
          AND b.lesson.module.course.teacher.id = :teacherId
          AND b.lesson.module.course.status = org.courseflow.catalog.entity.CourseStatus.ACTIVE
          AND a.endsAt > :now AND b.endsAt > :now
          AND a.startsAt < b.endsAt AND a.endsAt > b.startsAt
        """)
  long countReassignmentOverlaps(
      @Param("courseId") Long courseId,
      @Param("teacherId") Long teacherId,
      @Param("now") Instant now);
}


package org.courseflow.content.repository;

import org.courseflow.content.entity.LessonSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonSessionRepository
        extends JpaRepository<LessonSession, Long> {

    Page<LessonSession> findAllByLesson_Id(
            Long lessonId,
            Pageable pageable
    );

    Page<LessonSession> findAllByLesson_Module_Course_Id(
            Long courseId,
            Pageable pageable
    );

    Page<LessonSession>
    findAllByLesson_Module_Course_IdAndStartsAtGreaterThanEqual(
            Long courseId,
            Instant startsAt,
            Pageable pageable
    );

    boolean existsByLesson_Id(Long lessonId);

    Optional<LessonSession> findByIdAndLesson_Id(
            Long id,
            Long lessonId
    );

    @Query("""
        SELECT COUNT(s)
        FROM LessonSession s
        WHERE s.lesson.id = :lessonId
          AND s.startsAt < :endsAt
          AND s.endsAt > :startsAt
        """)
    long countOverlappingSessions(
            @Param("lessonId") Long lessonId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );


    @Query("""
        SELECT COUNT(s)
        FROM LessonSession s
        WHERE s.lesson.id = :lessonId
          AND s.id <> :sessionId
          AND s.startsAt < :endsAt
          AND s.endsAt > :startsAt
        """)
    long countOverlappingSessionsExcept(
            @Param("lessonId") Long lessonId,
            @Param("sessionId") Long sessionId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );


}

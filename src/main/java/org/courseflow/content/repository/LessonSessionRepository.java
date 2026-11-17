
package org.courseflow.content.repository;

import org.courseflow.content.entity.LessonSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

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
}

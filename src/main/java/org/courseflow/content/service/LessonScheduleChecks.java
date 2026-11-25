package org.courseflow.content.service;

import java.time.Instant;
import org.courseflow.content.repository.LessonSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Schedule queries for course lifecycle operations. Locking remains with the caller. */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class LessonScheduleChecks {
  private final LessonSessionRepository sessions;

  public LessonScheduleChecks(LessonSessionRepository sessions) {
    this.sessions = sessions;
  }

  public boolean hasSessionsForLesson(Long lessonId) {
    return sessions.existsByLesson_Id(lessonId);
  }

  public boolean hasUnfinishedSessions(Long courseId, Instant now) {
    return sessions.existsByLesson_Module_Course_IdAndEndsAtGreaterThan(courseId, now);
  }

  public boolean hasReassignmentOverlap(Long courseId, Long teacherId, Instant now) {
    return sessions.countReassignmentOverlaps(courseId, teacherId, now) > 0;
  }
}

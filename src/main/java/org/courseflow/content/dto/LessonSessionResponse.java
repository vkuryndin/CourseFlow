package org.courseflow.content.dto;

import java.time.Instant;
import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.entity.LessonSessionFormat;

public record LessonSessionResponse(
    Long id,
    long version,
    Long lessonId,
    Instant startsAt,
    Instant endsAt,
    LessonSessionFormat format,
    String meetingUrl) {

  public static LessonSessionResponse from(LessonSession session) {
    return new LessonSessionResponse(
        session.getId(),
        session.getVersion(),
        session.getLesson().getId(),
        session.getStartsAt(),
        session.getEndsAt(),
        session.getFormat(),
        session.getMeetingUrl());
  }
}

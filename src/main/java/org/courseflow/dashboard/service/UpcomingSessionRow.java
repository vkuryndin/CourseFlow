package org.courseflow.dashboard.service;

import java.time.Instant;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.dashboard.dto.UpcomingSession;

public record UpcomingSessionRow(
    Long sessionId,
    Long courseId,
    String courseTitle,
    Long lessonId,
    String lessonTitle,
    Instant startsAt,
    Instant endsAt,
    LessonSessionFormat format,
    String meetingUrl) {
  static final String SELECT =
      """
            SELECT s.id, c.id, c.title, l.id, l.title, s.startsAt, s.endsAt, s.format, s.meetingUrl
            FROM LessonSession s
            JOIN s.lesson l JOIN l.module m JOIN m.course c
            """;

  UpcomingSession toResponse() {
    return new UpcomingSession(
        sessionId,
        courseId,
        courseTitle,
        lessonId,
        lessonTitle,
        startsAt,
        endsAt,
        format.toString(),
        meetingUrl);
  }
}

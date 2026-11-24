package org.courseflow.dashboard.dto;

import java.time.Instant;

public record UpcomingSession(Long sessionId, Long courseId, String courseTitle,
                              Long lessonId, String lessonTitle, Instant startsAt,
                              Instant endsAt, String format, String meetingUrl) {}

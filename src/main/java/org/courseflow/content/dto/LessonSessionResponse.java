
package org.courseflow.content.dto;

import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.entity.LessonSessionFormat;

import java.time.Instant;

public record LessonSessionResponse(
        Long id,
        Long lessonId,
        Instant startsAt,
        Instant endsAt,
        LessonSessionFormat format,
        String meetingUrl
) {

    public static LessonSessionResponse from(
            LessonSession session
    ) {
        return new LessonSessionResponse(
                session.getId(),
                session.getLesson().getId(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getFormat(),
                session.getMeetingUrl()
        );
    }
}


package org.courseflow.content.dto;

import org.courseflow.content.entity.Lesson;

public record LessonResponse(
        Long id,
        Long moduleId,
        String title,
        String content,
        String videoUrl,
        Integer position
) {
    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getModule().getId(),
                lesson.getTitle(),
                lesson.getContent(),
                lesson.getVideoUrl(),
                lesson.getPosition()
        );
    }
}

package org.courseflow.content.entity;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.lesson;
import static org.courseflow.testing.Fixtures.module;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.courseflow.content.entity.Module;
import org.junit.jupiter.api.Test;

class ContentDomainTest {

    @Test
    void moduleUpdateAndLessonAssociation() {
        Module module = module();
        module.updateDetails("New", "Description");
        module.changePosition(3);

        assertEquals("New", module.getTitle());
        assertEquals("Description", module.getDescription());
        assertEquals(3, module.getPosition());
        Lesson lesson = module.addLesson("Lesson", "Body", null, 1);
        assertSame(module, lesson.getModule());
        assertEquals(List.of(lesson), module.getLessons());
        module.removeLesson(lesson);
        assertTrue(module.getLessons().isEmpty());
    }

    @Test
    void lessonUpdateAndPosition() {
        Lesson lesson = lesson();
        lesson.updateDetails("New", "Text", "https://example.test/video");
        lesson.changePosition(2);

        assertEquals("New", lesson.getTitle());
        assertEquals("Text", lesson.getContent());
        assertEquals("https://example.test/video", lesson.getVideoUrl());
        assertEquals(2, lesson.getPosition());
    }

    @Test
    void sessionAssociationAndReschedule() {
        Lesson lesson = lesson();
        LessonSession session = lesson.addSession(NOW, NOW.plusSeconds(60), LessonSessionFormat.ONLINE,
                "https://example.test/meeting");

        assertSame(lesson, session.getLesson());
        assertEquals(List.of(session), lesson.getSessions());
        session.changeTime(NOW.plusSeconds(120), NOW.plusSeconds(180));
        assertEquals(NOW.plusSeconds(120), session.getStartsAt());
        assertEquals(NOW.plusSeconds(180), session.getEndsAt());
        session.updateDetails(NOW, NOW.plusSeconds(300), LessonSessionFormat.OFFLINE, null);
        assertEquals(LessonSessionFormat.OFFLINE, session.getFormat());
        assertNull(session.getMeetingUrl());
        lesson.removeSession(session);
        assertTrue(lesson.getSessions().isEmpty());
    }
}

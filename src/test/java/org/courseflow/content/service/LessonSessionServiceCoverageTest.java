package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.lesson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.dto.CreateLessonSessionRequest;
import org.courseflow.content.dto.RescheduleLessonSessionRequest;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.LessonSessionRepository;
import org.courseflow.identity.api.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

class LessonSessionServiceCoverageTest {
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final LessonSessionRepository sessions = mock(LessonSessionRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final ContentAccessService access = mock(ContentAccessService.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final LessonSessionService service = new LessonSessionService(sessions, lessons, access, guard, users, CLOCK);
    private final Lesson lesson = lesson();
    private final LessonSession session = id(lesson.addSession(NOW.plusSeconds(60), NOW.plusSeconds(120), LessonSessionFormat.OFFLINE, null), 90);
    private final Authentication auth = mock(Authentication.class);

    private void setup() {
        when(lessons.findCourseId(60L)).thenReturn(Optional.of(40L));
        when(lessons.findById(60L)).thenReturn(Optional.of(lesson));
        when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString())).thenReturn(lesson.getModule().getCourse());
        when(sessions.findByIdAndLesson_Id(90L, 60L)).thenReturn(Optional.of(session));
    }

    @Test
    void findByLessonReturnsOrderedPage() {
        setup();
        var page = PageRequest.of(0, 10, Sort.by("startsAt", "id"));
        when(sessions.findAllByLesson_Id(60L, page)).thenReturn(new PageImpl<>(List.of(session), page, 1));
        assertEquals(90L, service.findByLesson(60L, 0, 10, 10L, auth).content().get(0).id());
        verify(access).checkLessonAccess(60L, 10L, auth);
    }

    @Test
    void getByIdMapsSession() {
        setup();
        assertEquals(NOW.plusSeconds(60), service.getById(60L, 90L, 10L, auth).startsAt());
    }

    @Test
    void getMissingSessionRejected() {
        setup();
        assertThrows(ResourceNotFoundException.class, () -> service.getById(60L, 99L, 10L, auth));
    }

    @Test
    void updateChangesTimeAndFormat() {
        setup();
        var request = new CreateLessonSessionRequest(NOW.plusSeconds(180), NOW.plusSeconds(240), LessonSessionFormat.ONLINE, "https://example.test/meeting", 0L);
        var result = service.update(60L, 90L, request, 20L, false);
        assertEquals(LessonSessionFormat.ONLINE, result.format());
        assertEquals(request.startsAt(), result.startsAt());
        assertEquals(request.meetingUrl(), result.meetingUrl());
        verify(users).lockTeacherReference(20L);
        verify(sessions).flush();
    }

    @Test
    void deleteRemovesSessionFromLesson() {
        setup();
        service.delete(60L, 90L, 20L, false);
        assertTrue(lesson.getSessions().isEmpty());
        verify(sessions).delete(session);
        verify(sessions).flush();
    }

    @Test
    void missingLessonCourseRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.delete(60L, 90L, 20L, false));
        verifyNoInteractions(sessions);
    }

    @Test
    void missingEditableSessionRejected() {
        setup();
        assertThrows(ResourceNotFoundException.class, () -> service.delete(60L, 99L, 20L, false));
    }

    @Test
    void cannotMoveToPast() {
        setup();
        var request = new RescheduleLessonSessionRequest(NOW, NOW.plusSeconds(60), 0L);
        assertThrows(BadRequestException.class, () -> service.reschedule(60L, 90L, request, 20L, false));
        verify(sessions, never()).flush();
    }
}

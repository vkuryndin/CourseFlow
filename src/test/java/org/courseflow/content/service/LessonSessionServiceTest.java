package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.lesson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.courseflow.catalog.api.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.content.api.ContentAccessService;
import org.courseflow.content.dto.CreateLessonSessionRequest;
import org.courseflow.content.dto.RescheduleLessonSessionRequest;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.LessonSessionRepository;
import org.courseflow.identity.api.UserDirectory;
import org.junit.jupiter.api.Test;

class LessonSessionServiceTest {
  private final LessonSessionRepository sessions = mock(LessonSessionRepository.class);
  private final LessonRepository lessons = mock(LessonRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final UserDirectory users = mock(UserDirectory.class);
  private final LessonSessionService service =
      new LessonSessionService(
          sessions, lessons, mock(ContentAccessService.class), guard, users, CLOCK);
  private final Lesson l = lesson();
  private final Instant start = NOW.plusSeconds(60);
  private final Instant end = NOW.plusSeconds(120);

  private void setup() {
    when(lessons.findCourseId(60L)).thenReturn(Optional.of(40L));
    when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString()))
        .thenReturn(l.getModule().getCourse());
    when(lessons.findById(60L)).thenReturn(Optional.of(l));
  }

  private CreateLessonSessionRequest request(
      Instant a, Instant b, LessonSessionFormat format, String url) {
    return new CreateLessonSessionRequest(a, b, format, url, null);
  }

  @Test
  void onlineSessionCreatedAndTeacherLocked() {
    setup();
    when(sessions.saveAndFlush(any(LessonSession.class)))
        .thenAnswer((i) -> id(i.getArgument(0), 1));
    var r =
        service.create(
            60L,
            request(start, end, LessonSessionFormat.ONLINE, " https://example.test/meeting "),
            20L,
            false);

    assertEquals(start, r.startsAt());
    assertEquals("https://example.test/meeting", r.meetingUrl());
    verify(users).lockTeacherReference(20L);
    verify(sessions).countTeacherOverlaps(20L, -1L, start, end);
  }

  @Test
  void invalidTimeIntervalOrPastStartRejected() {
    setup();

    assertThrows(
        BadRequestException.class,
        () ->
            service.create(
                60L, request(start, start, LessonSessionFormat.OFFLINE, null), 20L, false));
    assertThrows(
        BadRequestException.class,
        () ->
            service.create(60L, request(NOW, end, LessonSessionFormat.OFFLINE, null), 20L, false));
    verify(sessions, never()).saveAndFlush(any());
  }

  @Test
  void onlineRequiresUrlAndOfflineForbidsUrl() {
    setup();

    assertThrows(
        BadRequestException.class,
        () ->
            service.create(60L, request(start, end, LessonSessionFormat.ONLINE, null), 20L, false));
    assertThrows(
        BadRequestException.class,
        () ->
            service.create(
                60L,
                request(start, end, LessonSessionFormat.OFFLINE, "https://example.test"),
                20L,
                false));
    verify(sessions, never()).saveAndFlush(any());
  }

  @Test
  void teacherOverlapRejected() {
    setup();
    when(sessions.countTeacherOverlaps(20L, -1L, start, end)).thenReturn(1L);

    assertThrows(
        BusinessConflictException.class,
        () ->
            service.create(
                60L, request(start, end, LessonSessionFormat.OFFLINE, null), 20L, false));
    verify(sessions, never()).saveAndFlush(any());
  }

  @Test
  void startedSessionCannotBeDeletedOrRescheduled() {
    setup();
    var session = id(l.addSession(NOW, end, LessonSessionFormat.OFFLINE, null), 1);
    when(sessions.findByIdAndLesson_Id(1L, 60L)).thenReturn(Optional.of(session));

    assertThrows(BusinessConflictException.class, () -> service.delete(60L, 1L, 20L, false));
    assertThrows(
        BusinessConflictException.class,
        () ->
            service.reschedule(
                60L, 1L, new RescheduleLessonSessionRequest(start, end, 0L), 20L, false));
    verify(sessions, never()).delete(any(LessonSession.class));
  }

  @Test
  void reschedulingFutureSessionPreservesFormat() {
    setup();
    var session =
        id(l.addSession(start, end, LessonSessionFormat.ONLINE, "https://example.test"), 1);
    when(sessions.findByIdAndLesson_Id(1L, 60L)).thenReturn(Optional.of(session));

    var response =
        service.reschedule(
            60L, 1L, new RescheduleLessonSessionRequest(end, end.plusSeconds(60), 0L), 20L, false);

    assertEquals(end, response.startsAt());
    assertEquals(LessonSessionFormat.ONLINE, response.format());
    verify(sessions).flush();
  }
}

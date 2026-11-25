package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.lesson;
import static org.courseflow.testing.Fixtures.module;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.catalog.api.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentAccessService;
import org.courseflow.content.api.LessonScheduleChecks;
import org.courseflow.content.dto.ChangeLessonPositionRequest;
import org.courseflow.content.dto.CreateLessonRequest;
import org.courseflow.content.dto.UpdateLessonRequest;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.ModuleRepository;
import org.courseflow.learning.api.AssignmentAccess;
import org.junit.jupiter.api.Test;

class LessonServiceTest {
  private final LessonRepository lessons = mock(LessonRepository.class);
  private final ModuleRepository modules = mock(ModuleRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final LessonScheduleChecks schedule = mock(LessonScheduleChecks.class);
  private final AssignmentAccess assignments = mock(AssignmentAccess.class);
  private final LessonService service =
      new LessonService(
          lessons, modules, mock(ContentAccessService.class), guard, schedule, assignments);
  private final Lesson lesson = lesson();
  private final org.courseflow.content.entity.Module module = lesson.getModule();

  private void setup() {
    when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString()))
        .thenReturn(module.getCourse());
    when(modules.findByIdAndCourse_Id(50L, 40L)).thenReturn(Optional.of(module));
    when(lessons.findByIdAndModule_Id(60L, 50L)).thenReturn(Optional.of(lesson));
  }

  @Test
  void createsLessonWithTrimmedTextAndUrl() {
    setup();
    when(lessons.saveAndFlush(any(Lesson.class))).thenAnswer((i) -> id(i.getArgument(0), 61));

    var response =
        service.create(
            40L,
            50L,
            new CreateLessonRequest(" New ", " Text ", " https://example.test/video ", 2),
            20L,
            false);

    assertEquals("New", response.title());
    assertEquals("Text", response.content());
    assertEquals("https://example.test/video", response.videoUrl());
  }

  @Test
  void occupiedPositionAndInvalidVideoRejected() {
    setup();
    when(lessons.existsByModule_IdAndPosition(50L, 1)).thenReturn(true);

    assertThrows(
        BusinessConflictException.class,
        () ->
            service.create(40L, 50L, new CreateLessonRequest("New", "Text", null, 1), 20L, false));
    assertThrows(
        BadRequestException.class,
        () ->
            service.create(
                40L, 50L, new CreateLessonRequest("New", "Text", "javascript:bad", 2), 20L, false));
    verify(lessons, never()).saveAndFlush(any());
  }

  @Test
  void updatesLessonAndNoOpPosition() {
    setup();
    var r =
        service.update(
            40L, 50L, 60L, new UpdateLessonRequest(" New ", " Text ", " ", 0L), 20L, false);

    assertEquals("New", r.title());
    assertNull(r.videoUrl());
    assertEquals(
        1,
        service
            .changePosition(40L, 50L, 60L, new ChangeLessonPositionRequest(1, 0L), 20L, false)
            .position());
    verify(lessons).flush();
  }

  @Test
  void wrongParentRejected() {
    setup();

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            service.create(40L, 51L, new CreateLessonRequest("New", "Text", null, 1), 20L, false));
  }
}

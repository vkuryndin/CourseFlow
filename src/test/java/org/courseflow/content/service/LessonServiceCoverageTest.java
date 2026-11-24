package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.lesson;
import static org.courseflow.testing.Fixtures.module;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.dto.ChangeLessonPositionRequest;
import org.courseflow.content.dto.LessonSummaryResponse;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.ModuleRepository;
import org.courseflow.learning.api.AssignmentAccess;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

class LessonServiceCoverageTest {
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final ModuleRepository modules = mock(ModuleRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final ContentAccessService access = mock(ContentAccessService.class);
    private final LessonScheduleChecks schedules = mock(LessonScheduleChecks.class);
    private final AssignmentAccess assignments = mock(AssignmentAccess.class);
    private final LessonService service = new LessonService(lessons, modules, access, guard, schedules, assignments);
    private final Lesson lesson = lesson();
    private final Module module = lesson.getModule();
    private final Authentication auth = mock(Authentication.class);

    private void setup() {
        when(modules.findByIdAndCourse_Id(50L, 40L)).thenReturn(Optional.of(module));
        when(lessons.findByIdAndModule_Id(60L, 50L)).thenReturn(Optional.of(lesson));
    }

    @Test
    void summaryPageUsesStableOrder() {
        setup();
        var summary = new LessonSummaryResponse(60L, 0L, 50L, "Entities", 1);
        var page = PageRequest.of(0, 10, Sort.by("position", "id"));
        when(lessons.findSummariesByModuleId(50L, page)).thenReturn(new PageImpl<>(List.of(summary), page, 1));
        assertEquals(List.of(summary), service.findByModule(40L, 50L, 0, 10, 10L, auth).content());
        verify(access).checkCourseAccess(40L, 10L, auth);
    }

    @Test
    void detailIncludesContent() {
        setup();
        assertEquals("Content", service.getById(40L, 50L, 60L, 10L, auth).content());
    }

    @Test
    void missingLessonRejected() {
        when(modules.findByIdAndCourse_Id(50L, 40L)).thenReturn(Optional.of(module));
        assertThrows(ResourceNotFoundException.class, () -> service.getById(40L, 50L, 60L, 10L, auth));
    }

    @Test
    void deletionBlockedBySessions() {
        setup();
        when(schedules.hasSessionsForLesson(60L)).thenReturn(true);
        assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 60L, 20L, false));
        verifyNoInteractions(assignments);
        verify(lessons, never()).delete(any(Lesson.class));
    }

    @Test
    void deletionBlockedByAssignments() {
        setup();
        when(assignments.hasAssignments(60L)).thenReturn(true);
        assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 60L, 20L, false));
    }

    @Test
    void deleteRemovesChildFromParent() {
        setup();
        service.delete(40L, 50L, 60L, 20L, false);
        assertTrue(module.getLessons().isEmpty());
        verify(lessons).delete(lesson);
        verify(lessons).flush();
    }

    @Test
    void deleteTranslatesForeignKeyConflict() {
        setup();
        doThrow(new DataIntegrityViolationException("related")).when(lessons).flush();
        assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 60L, 20L, false));
    }

    @Test
    void positionUnchangedDoesNotFlush() {
        setup();
        assertEquals(1, service.changePosition(40L, 50L, 60L, new ChangeLessonPositionRequest(1, 0L), 20L, false).position());
        verify(lessons, never()).flush();
    }

    @Test
    void positionShiftMovesNeighbour() {
        setup();
        Lesson neighbour = id(module.addLesson("Next", "Content", null, 2), 61);
        when(lessons.findByModule_IdAndPositionBetweenOrderByPositionAsc(50L, 1, 2)).thenReturn(List.of(lesson, neighbour));
        when(lessons.findFirstByModule_IdOrderByPositionDescIdDesc(50L)).thenReturn(Optional.of(neighbour));
        assertEquals(2, service.changePosition(40L, 50L, 60L, new ChangeLessonPositionRequest(2, 0L), 20L, false).position());
        assertEquals(1, neighbour.getPosition());
        verify(lessons, times(3)).flush();
    }
}

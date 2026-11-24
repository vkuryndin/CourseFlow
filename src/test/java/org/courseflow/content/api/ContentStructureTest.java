package org.courseflow.content.api;

import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.ModuleRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.courseflow.testing.Fixtures.lesson;
import static org.courseflow.testing.Fixtures.module;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentStructureTest {
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final ModuleRepository modules = mock(ModuleRepository.class);
    private final ContentStructure content = new ContentStructure(lessons, modules);

    @Test
    void findsModuleAndLessonOrThrowsNotFound() {
        var m = module();
        var l = lesson();
        when(modules.findById(50L)).thenReturn(Optional.of(m));
        when(lessons.findById(60L)).thenReturn(Optional.of(l));
        assertSame(m, content.requireModule(50L));
        assertSame(l, content.requireLesson(60L));
        assertThrows(ResourceNotFoundException.class, () -> content.requireModule(99L));
        assertThrows(ResourceNotFoundException.class, () -> content.requireLesson(99L));
    }

    @Test
    void checksParentMembershipAndChildExistence() {
        when(modules.existsByCourse_Id(40L)).thenReturn(true);
        when(lessons.existsByModule_Id(50L)).thenReturn(true);
        when(modules.existsByIdAndCourse_Id(50L, 40L)).thenReturn(true);
        assertTrue(content.hasModules(40L));
        assertFalse(content.hasModules(41L));
        assertTrue(content.hasLessons(50L));
        assertFalse(content.hasLessons(51L));
        content.requireModuleInCourse(40L, 50L);
        assertThrows(ResourceNotFoundException.class, () -> content.requireModuleInCourse(41L, 50L));
    }
}

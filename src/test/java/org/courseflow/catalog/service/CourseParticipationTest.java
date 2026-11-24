package org.courseflow.catalog.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.junit.jupiter.api.Test;

class CourseParticipationTest {
    private final CourseRepository repository = mock(CourseRepository.class);
    private final CourseParticipation service = new CourseParticipation(repository);

    @Test
    void historicalCoursesCount() {
        when(repository.existsByTeacher_Id(20L)).thenReturn(true);
        assertTrue(service.teachesAnyCourse(20L));
        assertFalse(service.teachesAnyCourse(10L));
    }

    @Test
    void activeCoursesOnly() {
        when(repository.existsByTeacher_IdAndStatus(20L, CourseStatus.ACTIVE)).thenReturn(true);
        assertTrue(service.teachesActiveCourse(20L));
        assertFalse(service.teachesActiveCourse(10L));
    }
}

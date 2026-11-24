package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.courseflow.catalog.entity.Tag;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.catalog.repository.TagRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class CourseTagServiceTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final TagRepository tags = mock(TagRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final CourseTagService service = new CourseTagService(courses, tags, guard);

    @Test
    void tagLinksAreIdempotent() {
        var c = course();
        var tag = id(new Tag("JPA"), 1);
        when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString())).thenReturn(c);
        when(tags.findByIdForShare(1L)).thenReturn(Optional.of(tag));
        service.addTagToCourse(40L, 1L, 20L, false);
        service.addTagToCourse(40L, 1L, 20L, false);

        assertEquals(Set.of(tag), c.getTags());
        service.removeTagFromCourse(40L, 1L, 20L, false);
        service.removeTagFromCourse(40L, 1L, 20L, false);
        assertTrue(c.getTags().isEmpty());
    }

    @Test
    void unknownTagRejected() {
        when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString())).thenReturn(course());

        assertThrows(ResourceNotFoundException.class, () -> service.addTagToCourse(40L, 1L, 20L, false));
    }

    @Test
    void unknownCourseAndInvalidPageRejected() {
        assertThrows(BadRequestException.class, () -> service.findCourseTags(40L, -1, 20));
        assertThrows(ResourceNotFoundException.class, () -> service.findCourseTags(40L, 0, 20));
    }
}

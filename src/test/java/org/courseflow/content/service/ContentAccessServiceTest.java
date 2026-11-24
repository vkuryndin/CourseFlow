package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.lesson;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.learning.service.EnrollmentAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ContentAccessServiceTest {
    private final CourseGuard guard = mock(CourseGuard.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
    private final ContentAccessService service = new ContentAccessService(guard, lessons, enrollments);

    private org.springframework.security.core.Authentication auth(String role) {
        return new UsernamePasswordAuthenticationToken("actor", "",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void adminAndOwnerCanRead() {
        when(guard.find(40L)).thenReturn(course());
        service.checkCourseAccess(40L, 99L, auth("ADMIN"));
        service.checkCourseAccess(40L, 20L, auth("TEACHER"));

        verifyNoInteractions(enrollments);
    }

    @Test
    void otherTeacherAndUnenrolledStudentDenied() {
        when(guard.find(40L)).thenReturn(course());

        assertThrows(AccessDeniedException.class, () -> service.checkCourseAccess(40L, 99L, auth("TEACHER")));
        assertThrows(AccessDeniedException.class, () -> service.checkCourseAccess(40L, 10L, auth("STUDENT")));
    }

    @Test
    void enrolledStudentReadsArchivedCourse() {
        var c = course();
        c.archive();
        when(guard.find(40L)).thenReturn(c);
        when(enrollments.hasActiveEnrollment(10L, 40L)).thenReturn(true);
        service.checkCourseAccess(40L, 10L, auth("STUDENT"));
    }

    @Test
    void lessonAccessResolvesParentCourse() {
        when(lessons.findById(60L)).thenReturn(Optional.of(lesson()));
        when(guard.find(40L)).thenReturn(course());
        service.checkLessonAccess(60L, 20L, auth("TEACHER"));

        verify(guard).find(40L);
        assertThrows(ResourceNotFoundException.class, () -> service.checkLessonAccess(999L, 20L, auth("TEACHER")));
    }
}

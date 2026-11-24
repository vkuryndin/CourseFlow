package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.category;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.courseflow.catalog.dto.CreateCourseRequest;
import org.courseflow.catalog.dto.UpdateCourseRequest;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.service.LessonScheduleChecks;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.review.api.ReviewAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CourseServiceTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final ContentStructure content = mock(ContentStructure.class);
    private final ReviewAccess reviews = mock(ReviewAccess.class);
    private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
    private final LessonScheduleChecks schedules = mock(LessonScheduleChecks.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final CourseService service = new CourseService(courses, categories, users, content, reviews,
            enrollments, schedules, CLOCK, guard);
    private final Course c = course();
    private final LocalDate start = LocalDate.of(2026, 1, 1);

    private CreateCourseRequest createRequest() {
        return new CreateCourseRequest(" New ", " Text ", 30L, 20L, 30, start);
    }

    @Test
    void teacherCreatesOwnCourse() {
        when(categories.findByIdForShare(30L)).thenReturn(Optional.of(category()));
        when(users.lockTeacherReference(20L)).thenReturn(teacher());
        when(courses.save(any(Course.class))).thenAnswer((i) -> id(i.getArgument(0), 40));

        var response = service.create(createRequest(), 20L, false);

        assertEquals("New", response.title());
        assertEquals(40L, response.id());
    }

    @Test
    void teacherCannotCreateForOtherTeacher() {
        assertThrows(AccessDeniedException.class, () -> service.create(createRequest(), 99L, false));
        verifyNoInteractions(courses, categories, users);
    }

    @Test
    void inactiveTeacherOrStudentCannotBeAssigned() {
        when(categories.findByIdForShare(30L)).thenReturn(Optional.of(category()));
        var t = teacher();
        t.deactivate();
        when(users.lockTeacherReference(20L)).thenReturn(t, student());

        assertThrows(BusinessConflictException.class, () -> service.create(createRequest(), 20L, false));
        assertThrows(BusinessConflictException.class, () -> service.create(createRequest(), 20L, false));
        verifyNoInteractions(courses);
    }

    @Test
    void regularUpdateCannotChangeTeacher() {
        when(guard.lockEditable(eq(40L), anyLong(), anyBoolean(), anyString())).thenReturn(c);
        var req = new UpdateCourseRequest("New", "Text", 30L, 99L, 30, start, 0L);

        assertThrows(AccessDeniedException.class, () -> service.update(40L, req, 20L, false));
        assertThrows(BusinessConflictException.class, () -> service.update(40L, req, 99L, true));
    }

    @Test
    void archiveBlocksUpcomingOrOngoingSessionsAndIsIdempotent() {
        when(guard.lockOwned(40L, 20L, false)).thenReturn(c);
        when(schedules.hasUnfinishedSessions(40L, NOW)).thenReturn(true, false);

        assertThrows(BusinessConflictException.class, () -> service.archive(40L, 20L, false));
        assertEquals(CourseStatus.ACTIVE, c.getStatus());
        service.archive(40L, 20L, false);
        service.archive(40L, 20L, false);
        assertEquals(CourseStatus.ARCHIVED, c.getStatus());
        verify(schedules, times(2)).hasUnfinishedSessions(40L, NOW);
    }

    @Test
    void deletionBlockedByEducationalHistory() {
        when(guard.lockOwned(40L, 20L, false)).thenReturn(c);
        when(content.hasModules(40L)).thenReturn(true, false, false);
        when(enrollments.hasEnrollmentHistory(40L)).thenReturn(true, false);
        when(reviews.hasReviews(40L)).thenReturn(true);
        for (int i = 0; i < 3; i++) {
            assertThrows(BusinessConflictException.class, () -> service.delete(40L, 20L, false));
        }
        verify(courses, never()).delete(any(Course.class));
    }

    @Test
    void emptyCourseDeleted() {
        when(guard.lockOwned(40L, 20L, false)).thenReturn(c);
        service.delete(40L, 20L, false);

        verify(courses).delete(c);
        verify(courses).flush();
    }

    @Test
    void reassignRequiresAdminAndCompatibleSchedule() {
        assertThrows(AccessDeniedException.class, () -> service.reassignTeacher(40L, 99L, 0L, false));
        when(guard.lock(40L)).thenReturn(c);
        var replacement = id(teacher(), 99);
        when(users.lockTeacherReference(99L)).thenReturn(replacement);
        when(enrollments.hasActiveEnrollment(99L, 40L)).thenReturn(true, false, false);
        when(schedules.hasReassignmentOverlap(40L, 99L, NOW)).thenReturn(true, false);
        assertThrows(BusinessConflictException.class, () -> service.reassignTeacher(40L, 99L, 0L, true));
        assertThrows(BusinessConflictException.class, () -> service.reassignTeacher(40L, 99L, 0L, true));
        service.reassignTeacher(40L, 99L, 0L, true);
        assertSame(replacement, c.getTeacher());
    }
}

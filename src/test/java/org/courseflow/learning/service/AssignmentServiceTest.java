package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.assignment;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.service.ContentAccessService;
import org.courseflow.learning.dto.CreateAssignmentRequest;
import org.courseflow.learning.dto.ExtendAssignmentDeadlineRequest;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

class AssignmentServiceTest {
    private final AssignmentRepository repo = mock(AssignmentRepository.class);
    private final ContentStructure content = mock(ContentStructure.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final ContentAccessService access = mock(ContentAccessService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AssignmentService service = new AssignmentService(repo, content, guard, access, em, CLOCK);
    private final Assignment a = assignment();
    private final org.courseflow.catalog.entity.Course c = a.getLesson().getModule().getCourse();

    private void setup() {
        when(content.requireLesson(60L)).thenReturn(a.getLesson());
        when(guard.lock(40L)).thenReturn(c);
        when(repo.findById(70L)).thenReturn(Optional.of(a));
    }

    @SuppressWarnings("unchecked")
    private void submissions(long n) {
        TypedQuery<Long> q = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(q);
        when(q.setParameter("assignmentId", 70L)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(n);
    }

    private CreateAssignmentRequest request(Instant due, Long version) {
        return new CreateAssignmentRequest(" New ", " Text ", due, 50, version);
    }

    @Test
    void createTrimsAndPersists() {
        setup();
        when(repo.saveAndFlush(any(Assignment.class))).thenAnswer((i) -> id(i.getArgument(0), 71));
        var r = service.create(60L, request(NOW.plusSeconds(1), null), 20L, false);

        assertEquals("New", r.title());
        assertEquals("Text", r.description());
        assertEquals(50, r.maxScore());
    }

    @Test
    void createRejectsPastOrEqualDeadline() {
        setup();

        assertThrows(BadRequestException.class, () -> service.create(60L, request(NOW, null), 20L, false));
        assertThrows(BadRequestException.class,
                () -> service.create(60L, request(NOW.minusSeconds(1), null), 20L, false));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void teacherCannotModifyOtherCourse() {
        setup();

        assertThrows(AccessDeniedException.class,
                () -> service.extendDeadline(60L, 70L,
                new ExtendAssignmentDeadlineRequest(NOW.plusSeconds(7200), 0L), 99L, false));
    }

    @Test
    void archivedCourseCannotBeModified() {
        setup();
        c.archive();

        assertThrows(BusinessConflictException.class,
                () -> service.create(60L, request(NOW.plusSeconds(1), null), 20L, false));
    }

    @Test
    void extensionDoesNotQueryOrModifySubmissions() {
        setup();
        Instant newDue = NOW.plusSeconds(7200);
        var r = service.extendDeadline(60L, 70L, new ExtendAssignmentDeadlineRequest(newDue, 0L), 20L, false);

        assertEquals(newDue, r.dueAt());
        assertEquals("Task", r.title());
        assertEquals(100, r.maxScore());
        verifyNoInteractions(em);
        verify(repo).flush();
    }

    @Test
    void extensionRejectsStaleVersionAndShortening() {
        setup();

        assertThrows(OptimisticLockingFailureException.class,
                () -> service.extendDeadline(60L, 70L,
                new ExtendAssignmentDeadlineRequest(NOW.plusSeconds(7200), 1L), 20L, false));
        assertThrows(BadRequestException.class,
                () -> service.extendDeadline(60L, 70L, new ExtendAssignmentDeadlineRequest(NOW.plusSeconds(10), 0L),
                20L, false));
        assertThrows(BadRequestException.class,
                () -> service.extendDeadline(60L, 70L, new ExtendAssignmentDeadlineRequest(NOW, 0L), 20L, false));
        verify(repo, never()).flush();
    }

    @Test
    void updateAndDeleteBlockedWhenSubmissionsExist() {
        setup();
        submissions(1);

        assertThrows(BusinessConflictException.class,
                () -> service.update(60L, 70L, request(NOW.plusSeconds(10), 0L), 20L, false));
        assertThrows(BusinessConflictException.class, () -> service.delete(60L, 70L, 20L, false));
        verify(repo, never()).delete(any(Assignment.class));
    }

    @Test
    void updateWithoutSubmissions() {
        setup();
        submissions(0);
        var r = service.update(60L, 70L, request(NOW.plusSeconds(10), 0L), 20L, false);

        assertEquals("New", r.title());
        assertEquals(50, r.maxScore());
        verify(repo).flush();
    }

    @Test
    void deleteWithoutSubmissions() {
        setup();
        submissions(0);
        service.delete(60L, 70L, 20L, false);

        verify(repo).delete(a);
        verify(repo).flush();
    }

    @Test
    void deleteTranslatesDatabaseConflict() {
        setup();
        submissions(0);
        doThrow(new DataIntegrityViolationException("related")).when(repo).flush();

        assertThrows(BusinessConflictException.class, () -> service.delete(60L, 70L, 20L, false));
    }
}

package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.assignment;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.service.ContentAccessService;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

class AssignmentServiceReadTest {
    private final AssignmentRepository repository = mock(AssignmentRepository.class);
    private final ContentAccessService access = mock(ContentAccessService.class);
    private final AssignmentService service = new AssignmentService(repository, mock(ContentStructure.class), mock(CourseGuard.class), access, mock(EntityManager.class), CLOCK);
    private final Authentication auth = mock(Authentication.class);
    private final Assignment assignment = assignment();

    @Test
    void getAssignmentChecksLessonAccess() {
        when(repository.findById(70L)).thenReturn(Optional.of(assignment));
        assertEquals(70L, service.getById(60L, 70L, 10L, auth).id());
        verify(access).checkLessonAccess(60L, 10L, auth);
    }

    @Test
    void assignmentMustBelongToLesson() {
        when(repository.findById(70L)).thenReturn(Optional.of(assignment));
        assertThrows(ResourceNotFoundException.class, () -> service.getById(61L, 70L, 10L, auth));
    }

    @Test
    void missingAssignmentRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.getById(60L, 70L, 10L, auth));
    }

    @Test
    void listAssignmentsUsesRequestedSort() {
        var page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dueAt").and(Sort.by("id")));
        when(repository.findAllByLesson_Id(60L, page)).thenReturn(new PageImpl<>(List.of(assignment), page, 1));
        var result = service.findByLesson(60L, 10L, auth, 0, 10, "dueAt", "desc");
        assertEquals(70L, result.content().get(0).id());
        assertEquals(1L, result.totalElements());
    }

    @Test
    void unsupportedSortRejected() {
        assertThrows(BadRequestException.class, () -> service.findByLesson(60L, 10L, auth, 0, 10, "description", "asc"));
        verifyNoInteractions(repository);
    }
}

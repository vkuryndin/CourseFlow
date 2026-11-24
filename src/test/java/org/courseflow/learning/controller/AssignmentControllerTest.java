package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.AssignmentResponse;
import org.courseflow.learning.dto.CreateAssignmentRequest;
import org.courseflow.learning.dto.ExtendAssignmentDeadlineRequest;
import org.courseflow.learning.service.AssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class AssignmentControllerTest {
    private final AssignmentService assignmentService = mock(AssignmentService.class);
    private final AssignmentController controller = new AssignmentController(assignmentService);

    @Test
    void createDelegatesAndReturnsContract() {
        Long lessonId = 101L;
        CreateAssignmentRequest request = mock(CreateAssignmentRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        AssignmentResponse expected = mock(AssignmentResponse.class);
        when(expected.id()).thenReturn(999L);
        when(assignmentService.create(lessonId, request, 42L, true)).thenReturn(expected);

        var actual = controller.create(lessonId, request, jwt, authentication);

        assertEquals(201, actual.getStatusCode().value());
        assertSame(expected, actual.getBody());
        assertEquals("/api/lessons/101/assignments/999", actual.getHeaders().getLocation().toString());
        verify(assignmentService).create(lessonId, request, 42L, true);
    }

    @Test
    void findByLessonDelegatesAndReturnsContract() {
        Long lessonId = 101L;
        int page = 1;
        int size = 15;
        String sortBy = "id";
        String direction = "desc";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        PageResponse<AssignmentResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(assignmentService.findByLesson(lessonId, 42L, authentication, page, size, sortBy,
                direction)).thenReturn(expected);

        var actual = controller.findByLesson(lessonId, page, size, sortBy, direction, jwt, authentication);

        assertSame(expected, actual);
        verify(assignmentService).findByLesson(lessonId, 42L, authentication, page, size, sortBy, direction);
    }

    @Test
    void getByIdDelegatesAndReturnsContract() {
        Long lessonId = 101L;
        Long assignmentId = 102L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        AssignmentResponse expected = mock(AssignmentResponse.class);
        when(assignmentService.getById(lessonId, assignmentId, 42L, authentication)).thenReturn(expected);

        var actual = controller.getById(lessonId, assignmentId, jwt, authentication);

        assertSame(expected, actual);
        verify(assignmentService).getById(lessonId, assignmentId, 42L, authentication);
    }

    @Test
    void updateDelegatesAndReturnsContract() {
        Long lessonId = 101L;
        Long assignmentId = 102L;
        CreateAssignmentRequest request = mock(CreateAssignmentRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        AssignmentResponse expected = mock(AssignmentResponse.class);
        when(assignmentService.update(lessonId, assignmentId, request, 42L, true)).thenReturn(expected);

        var actual = controller.update(lessonId, assignmentId, request, jwt, authentication);

        assertSame(expected, actual);
        verify(assignmentService).update(lessonId, assignmentId, request, 42L, true);
    }

    @Test
    void extendDeadlineDelegatesAndReturnsContract() {
        Long lessonId = 101L;
        Long assignmentId = 102L;
        ExtendAssignmentDeadlineRequest request = mock(ExtendAssignmentDeadlineRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        AssignmentResponse expected = mock(AssignmentResponse.class);
        when(assignmentService.extendDeadline(lessonId, assignmentId, request, 42L, true)).thenReturn(expected);

        var actual = controller.extendDeadline(lessonId, assignmentId, request, jwt, authentication);

        assertSame(expected, actual);
        verify(assignmentService).extendDeadline(lessonId, assignmentId, request, 42L, true);
    }

    @Test
    void deleteDelegatesAndReturnsContract() {
        Long lessonId = 101L;
        Long assignmentId = 102L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var actual = controller.delete(lessonId, assignmentId, jwt, authentication);

        assertEquals(204, actual.getStatusCode().value());
        assertNull(actual.getBody());
        verify(assignmentService).delete(lessonId, assignmentId, 42L, true);
    }
}

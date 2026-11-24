package org.courseflow.content.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.common.api.PageResponse;
import org.courseflow.content.dto.ChangeLessonPositionRequest;
import org.courseflow.content.dto.CreateLessonRequest;
import org.courseflow.content.dto.LessonResponse;
import org.courseflow.content.dto.LessonSummaryResponse;
import org.courseflow.content.dto.UpdateLessonRequest;
import org.courseflow.content.service.LessonService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class LessonControllerTest {
    private final LessonService lessonService = mock(LessonService.class);
    private final LessonController controller = new LessonController(lessonService);

    @Test
    void createDelegatesAndReturnsContract() {
        Long courseId = 101L;
        Long moduleId = 102L;
        CreateLessonRequest request = mock(CreateLessonRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        LessonResponse expected = mock(LessonResponse.class);
        when(expected.id()).thenReturn(999L);
        when(lessonService.create(courseId, moduleId, request, 42L, true)).thenReturn(expected);

        var actual = controller.create(courseId, moduleId, request, jwt, authentication);

        assertEquals(201, actual.getStatusCode().value());
        assertSame(expected, actual.getBody());
        assertEquals("/api/courses/101/modules/102/lessons/999", actual.getHeaders().getLocation().toString());
        verify(lessonService).create(courseId, moduleId, request, 42L, true);
    }

    @Test
    void findByModuleDelegatesAndReturnsContract() {
        Long courseId = 101L;
        Long moduleId = 102L;
        int page = 1;
        int size = 15;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        PageResponse<LessonSummaryResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(lessonService.findByModule(courseId, moduleId, page, size, 42L, authentication)).thenReturn(expected);

        var actual = controller.findByModule(courseId, moduleId, page, size, jwt, authentication);

        assertSame(expected, actual);
        verify(lessonService).findByModule(courseId, moduleId, page, size, 42L, authentication);
    }

    @Test
    void getByIdDelegatesAndReturnsContract() {
        Long courseId = 101L;
        Long moduleId = 102L;
        Long lessonId = 103L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        LessonResponse expected = mock(LessonResponse.class);
        when(lessonService.getById(courseId, moduleId, lessonId, 42L, authentication)).thenReturn(expected);

        var actual = controller.getById(courseId, moduleId, lessonId, jwt, authentication);

        assertSame(expected, actual);
        verify(lessonService).getById(courseId, moduleId, lessonId, 42L, authentication);
    }

    @Test
    void updateDelegatesAndReturnsContract() {
        Long courseId = 101L;
        Long moduleId = 102L;
        Long lessonId = 103L;
        UpdateLessonRequest request = mock(UpdateLessonRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        LessonResponse expected = mock(LessonResponse.class);
        when(lessonService.update(courseId, moduleId, lessonId, request, 42L, true)).thenReturn(expected);

        var actual = controller.update(courseId, moduleId, lessonId, request, jwt, authentication);

        assertSame(expected, actual);
        verify(lessonService).update(courseId, moduleId, lessonId, request, 42L, true);
    }

    @Test
    void changePositionDelegatesAndReturnsContract() {
        Long courseId = 101L;
        Long moduleId = 102L;
        Long lessonId = 103L;
        ChangeLessonPositionRequest request = mock(ChangeLessonPositionRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        LessonResponse expected = mock(LessonResponse.class);
        when(lessonService.changePosition(courseId, moduleId, lessonId, request, 42L, true)).thenReturn(expected);

        var actual = controller.changePosition(courseId, moduleId, lessonId, request, jwt, authentication);

        assertSame(expected, actual);
        verify(lessonService).changePosition(courseId, moduleId, lessonId, request, 42L, true);
    }

    @Test
    void deleteDelegatesAndReturnsContract() {
        Long courseId = 101L;
        Long moduleId = 102L;
        Long lessonId = 103L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var actual = controller.delete(courseId, moduleId, lessonId, jwt, authentication);

        assertEquals(204, actual.getStatusCode().value());
        assertNull(actual.getBody());
        verify(lessonService).delete(courseId, moduleId, lessonId, 42L, true);
    }
}

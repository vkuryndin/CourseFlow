package org.courseflow.content.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.content.dto.CreateLessonSessionRequest;
import org.courseflow.content.dto.LessonSessionResponse;
import org.courseflow.content.dto.RescheduleLessonSessionRequest;
import org.courseflow.content.service.LessonSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class LessonSessionControllerTest {
  private final LessonSessionService sessionService = mock(LessonSessionService.class);
  private final LessonSessionController controller = new LessonSessionController(sessionService);

  @Test
  void createDelegatesAndReturnsContract() {
    Long lessonId = 101L;
    CreateLessonSessionRequest request = mock(CreateLessonSessionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    LessonSessionResponse expected = mock(LessonSessionResponse.class);
    when(expected.id()).thenReturn(999L);
    when(sessionService.create(lessonId, request, 42L, true)).thenReturn(expected);

    var actual = controller.create(lessonId, request, jwt, authentication);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals("/api/lessons/101/sessions/999", actual.getHeaders().getLocation().toString());
    verify(sessionService).create(lessonId, request, 42L, true);
  }

  @Test
  void findByLessonDelegatesAndReturnsContract() {
    Long lessonId = 101L;
    int page = 1;
    int size = 15;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    PageResponse<LessonSessionResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(sessionService.findByLesson(lessonId, page, size, 42L, authentication))
        .thenReturn(expected);

    var actual = controller.findByLesson(lessonId, page, size, jwt, authentication);

    assertSame(expected, actual);
    verify(sessionService).findByLesson(lessonId, page, size, 42L, authentication);
  }

  @Test
  void getByIdDelegatesAndReturnsContract() {
    Long lessonId = 101L;
    Long sessionId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    LessonSessionResponse expected = mock(LessonSessionResponse.class);
    when(sessionService.getById(lessonId, sessionId, 42L, authentication)).thenReturn(expected);

    var actual = controller.getById(lessonId, sessionId, jwt, authentication);

    assertSame(expected, actual);
    verify(sessionService).getById(lessonId, sessionId, 42L, authentication);
  }

  @Test
  void updateDelegatesAndReturnsContract() {
    Long lessonId = 101L;
    Long sessionId = 102L;
    CreateLessonSessionRequest request = mock(CreateLessonSessionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    LessonSessionResponse expected = mock(LessonSessionResponse.class);
    when(sessionService.update(lessonId, sessionId, request, 42L, true)).thenReturn(expected);

    var actual = controller.update(lessonId, sessionId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(sessionService).update(lessonId, sessionId, request, 42L, true);
  }

  @Test
  void rescheduleDelegatesAndReturnsContract() {
    Long lessonId = 101L;
    Long sessionId = 102L;
    RescheduleLessonSessionRequest request = mock(RescheduleLessonSessionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    LessonSessionResponse expected = mock(LessonSessionResponse.class);
    when(sessionService.reschedule(lessonId, sessionId, request, 42L, true)).thenReturn(expected);

    var actual = controller.reschedule(lessonId, sessionId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(sessionService).reschedule(lessonId, sessionId, request, 42L, true);
  }

  @Test
  void deleteDelegatesAndReturnsContract() {
    Long lessonId = 101L;
    Long sessionId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.delete(lessonId, sessionId, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(sessionService).delete(lessonId, sessionId, 42L, true);
  }
}

package org.courseflow.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.catalog.dto.CourseResponse;
import org.courseflow.catalog.dto.CreateCourseRequest;
import org.courseflow.catalog.dto.ReassignCourseTeacherRequest;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.dto.UpdateCourseRequest;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.service.CourseQueryService;
import org.courseflow.catalog.service.CourseService;
import org.courseflow.catalog.service.CourseTagService;
import org.courseflow.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class CourseControllerTest {
  private final CourseService courseService = mock(CourseService.class);
  private final CourseQueryService courseQueryService = mock(CourseQueryService.class);
  private final CourseTagService courseTagService = mock(CourseTagService.class);
  private final CourseController controller =
      new CourseController(courseService, courseQueryService, courseTagService);

  @Test
  void createDelegatesAndReturnsContract() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    CreateCourseRequest request = mock(CreateCourseRequest.class);
    CourseResponse expected = mock(CourseResponse.class);
    when(expected.id()).thenReturn(999L);
    when(courseService.create(request, 42L, true)).thenReturn(expected);

    var actual = controller.create(jwt, authentication, request);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals("/api/courses/999", actual.getHeaders().getLocation().toString());
    verify(courseService).create(request, 42L, true);
  }

  @Test
  void updateDelegatesAndReturnsContract() {
    Long id = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    UpdateCourseRequest request = mock(UpdateCourseRequest.class);
    CourseResponse expected = mock(CourseResponse.class);
    when(courseService.update(id, request, 42L, true)).thenReturn(expected);

    var actual = controller.update(id, jwt, authentication, request);

    assertSame(expected, actual);
    verify(courseService).update(id, request, 42L, true);
  }

  @Test
  void getByIdDelegatesAndReturnsContract() {
    Long id = 101L;
    CourseResponse expected = mock(CourseResponse.class);
    when(courseQueryService.getById(id)).thenReturn(expected);

    var actual = controller.getById(id);

    assertSame(expected, actual);
    verify(courseQueryService).getById(id);
  }

  @Test
  void findAllDelegatesAndReturnsContract() {
    int page = 1;
    int size = 15;
    String q = "search";
    Long categoryId = 101L;
    Long teacherId = 102L;
    CourseStatus status = null;
    String sortBy = "id";
    String direction = "desc";
    PageResponse<CourseResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(courseQueryService.findAll(
            page, size, q, categoryId, teacherId, status, sortBy, direction))
        .thenReturn(expected);

    var actual =
        controller.findAll(page, size, q, categoryId, teacherId, status, sortBy, direction);

    assertSame(expected, actual);
    verify(courseQueryService)
        .findAll(page, size, q, categoryId, teacherId, status, sortBy, direction);
  }

  @Test
  void archiveDelegatesAndReturnsContract() {
    Long id = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    CourseResponse expected = mock(CourseResponse.class);
    when(courseService.archive(id, 42L, true)).thenReturn(expected);

    var actual = controller.archive(id, jwt, authentication);

    assertSame(expected, actual);
    verify(courseService).archive(id, 42L, true);
  }

  @Test
  void deleteDelegatesAndReturnsContract() {
    Long id = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.delete(id, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(courseService).delete(id, 42L, true);
  }

  @Test
  void addTagDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long tagId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.addTag(courseId, tagId, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(courseTagService).addTagToCourse(courseId, tagId, 42L, true);
  }

  @Test
  void removeTagDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long tagId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.removeTag(courseId, tagId, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(courseTagService).removeTagFromCourse(courseId, tagId, 42L, true);
  }

  @Test
  void getCourseTagsDelegatesAndReturnsContract() {
    Long courseId = 101L;
    int page = 1;
    int size = 15;
    PageResponse<TagResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(courseTagService.findCourseTags(courseId, page, size)).thenReturn(expected);

    var actual = controller.getCourseTags(courseId, page, size);

    assertSame(expected, actual);
    verify(courseTagService).findCourseTags(courseId, page, size);
  }

  @Test
  void reassignTeacherDelegatesAndReturnsContract() {
    Long courseId = 101L;
    ReassignCourseTeacherRequest request = mock(ReassignCourseTeacherRequest.class);
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    when(request.teacherId()).thenReturn(77L);
    when(request.version()).thenReturn(5L);
    CourseResponse expected = mock(CourseResponse.class);
    when(courseService.reassignTeacher(courseId, 77L, 5L, true)).thenReturn(expected);

    var actual = controller.reassignTeacher(courseId, request, authentication);

    assertSame(expected, actual);
    verify(courseService).reassignTeacher(courseId, 77L, 5L, true);
  }
}

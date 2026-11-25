package org.courseflow.content.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.content.dto.ChangeModulePositionRequest;
import org.courseflow.content.dto.CreateModuleRequest;
import org.courseflow.content.dto.ModuleResponse;
import org.courseflow.content.dto.UpdateModuleRequest;
import org.courseflow.content.service.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class ModuleControllerTest {
  private final ModuleService moduleService = mock(ModuleService.class);
  private final ModuleController controller = new ModuleController(moduleService);

  @Test
  void createDelegatesAndReturnsContract() {
    Long courseId = 101L;
    CreateModuleRequest request = mock(CreateModuleRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    ModuleResponse expected = mock(ModuleResponse.class);
    when(expected.id()).thenReturn(999L);
    when(moduleService.create(courseId, request, 42L, true)).thenReturn(expected);

    var actual = controller.create(courseId, request, jwt, authentication);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals("/api/courses/101/modules/999", actual.getHeaders().getLocation().toString());
    verify(moduleService).create(courseId, request, 42L, true);
  }

  @Test
  void findByCourseDelegatesAndReturnsContract() {
    Long courseId = 101L;
    int page = 1;
    int size = 15;
    PageResponse<ModuleResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(moduleService.findByCourse(courseId, page, size)).thenReturn(expected);

    var actual = controller.findByCourse(courseId, page, size);

    assertSame(expected, actual);
    verify(moduleService).findByCourse(courseId, page, size);
  }

  @Test
  void getByIdDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long moduleId = 102L;
    ModuleResponse expected = mock(ModuleResponse.class);
    when(moduleService.getById(courseId, moduleId)).thenReturn(expected);

    var actual = controller.getById(courseId, moduleId);

    assertSame(expected, actual);
    verify(moduleService).getById(courseId, moduleId);
  }

  @Test
  void updateDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long moduleId = 102L;
    UpdateModuleRequest request = mock(UpdateModuleRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    ModuleResponse expected = mock(ModuleResponse.class);
    when(moduleService.update(courseId, moduleId, request, 42L, true)).thenReturn(expected);

    var actual = controller.update(courseId, moduleId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(moduleService).update(courseId, moduleId, request, 42L, true);
  }

  @Test
  void changePositionDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long moduleId = 102L;
    ChangeModulePositionRequest request = mock(ChangeModulePositionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    ModuleResponse expected = mock(ModuleResponse.class);
    when(moduleService.changePosition(courseId, moduleId, request, 42L, true)).thenReturn(expected);

    var actual = controller.changePosition(courseId, moduleId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(moduleService).changePosition(courseId, moduleId, request, 42L, true);
  }

  @Test
  void deleteDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long moduleId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.delete(courseId, moduleId, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(moduleService).delete(courseId, moduleId, 42L, true);
  }
}

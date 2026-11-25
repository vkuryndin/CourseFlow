package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.dto.EnrollmentResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.EnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class EnrollmentControllerTest {
  private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
  private final EnrollmentController controller = new EnrollmentController(enrollmentService);

  @Test
  void getMyEnrollmentDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    EnrollmentResponse expected = mock(EnrollmentResponse.class);
    when(enrollmentService.getMyEnrollment(courseId, 42L)).thenReturn(expected);

    var actual = controller.getMyEnrollment(courseId, jwt);

    assertSame(expected, actual);
    verify(enrollmentService).getMyEnrollment(courseId, 42L);
  }

  @Test
  void withdrawDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");

    var actual = controller.withdraw(courseId, jwt);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(enrollmentService).withdraw(courseId, 42L);
  }

  @Test
  void withdrawStudentDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Long studentId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");

    var actual = controller.withdrawStudent(courseId, studentId, jwt);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(enrollmentService).withdraw(courseId, studentId);
  }

  @Test
  void getCourseEnrollmentsDelegatesAndReturnsContract() {
    Long courseId = 101L;
    EnrollmentStatus status = null;
    int page = 1;
    int size = 15;
    String sortBy = "id";
    String direction = "desc";
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    PageResponse<EnrollmentListItemResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(enrollmentService.getCourseEnrollments(
            courseId, 42L, true, status, page, size, sortBy, direction))
        .thenReturn(expected);

    var actual =
        controller.getCourseEnrollments(
            courseId, status, page, size, sortBy, direction, jwt, authentication);

    assertSame(expected, actual);
    verify(enrollmentService)
        .getCourseEnrollments(courseId, 42L, true, status, page, size, sortBy, direction);
  }

  @Test
  void enrollDistinguishesCreationAndIdempotentRequest() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    EnrollmentResponse enrollment = mock(EnrollmentResponse.class);
    when(enrollmentService.enroll(1L, 42L))
        .thenReturn(
            new EnrollmentService.EnrollResult(enrollment, true),
            new EnrollmentService.EnrollResult(enrollment, false));
    var created = controller.enroll(1L, jwt);

    assertEquals(201, created.getStatusCode().value());
    assertEquals("/api/courses/1/enrollments/me", created.getHeaders().getLocation().toString());
    assertSame(enrollment, created.getBody());
    var existing = controller.enroll(1L, jwt);
    assertEquals(200, existing.getStatusCode().value());
    assertSame(enrollment, existing.getBody());
  }
}

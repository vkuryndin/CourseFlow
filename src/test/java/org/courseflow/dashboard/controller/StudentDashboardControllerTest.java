package org.courseflow.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.dashboard.dto.StudentDashboardResponse;
import org.courseflow.dashboard.service.StudentDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class StudentDashboardControllerTest {
  private final StudentDashboardService studentDashboardService =
      mock(StudentDashboardService.class);
  private final StudentDashboardController controller =
      new StudentDashboardController(studentDashboardService);

  @Test
  void getDashboardDelegatesAndReturnsContract() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    StudentDashboardResponse expected = mock(StudentDashboardResponse.class);
    when(studentDashboardService.getDashboard(42L)).thenReturn(expected);

    var actual = controller.getDashboard(jwt);

    assertSame(expected, actual);
    verify(studentDashboardService).getDashboard(42L);
  }
}

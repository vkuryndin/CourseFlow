package org.courseflow.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.dashboard.dto.TeacherDashboardResponse;
import org.courseflow.dashboard.service.TeacherDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class TeacherDashboardControllerTest {
  private final TeacherDashboardService teacherDashboardService =
      mock(TeacherDashboardService.class);
  private final TeacherDashboardController controller =
      new TeacherDashboardController(teacherDashboardService);

  @Test
  void getDashboardDelegatesAndReturnsContract() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    TeacherDashboardResponse expected = mock(TeacherDashboardResponse.class);
    when(teacherDashboardService.getDashboard(42L)).thenReturn(expected);

    var actual = controller.getDashboard(jwt);

    assertSame(expected, actual);
    verify(teacherDashboardService).getDashboard(42L);
  }
}

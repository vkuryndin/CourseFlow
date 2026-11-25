package org.courseflow.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.dashboard.dto.AdminDashboardResponse;
import org.courseflow.dashboard.service.AdminDashboardService;
import org.junit.jupiter.api.Test;

/** Direct controller contract tests; no Spring MVC runtime. */
class AdminDashboardControllerTest {
  private final AdminDashboardService adminDashboardService = mock(AdminDashboardService.class);
  private final AdminDashboardController controller =
      new AdminDashboardController(adminDashboardService);

  @Test
  void getDashboardDelegatesAndReturnsContract() {
    AdminDashboardResponse expected = mock(AdminDashboardResponse.class);
    when(adminDashboardService.getDashboard()).thenReturn(expected);

    var actual = controller.getDashboard();

    assertSame(expected, actual);
    verify(adminDashboardService).getDashboard();
  }
}

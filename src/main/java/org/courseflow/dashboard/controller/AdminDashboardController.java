package org.courseflow.dashboard.controller;

import org.courseflow.dashboard.dto.AdminDashboardResponse;
import org.courseflow.dashboard.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/admin")
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;

  public AdminDashboardController(AdminDashboardService adminDashboardService) {
    this.adminDashboardService = adminDashboardService;
  }

  @GetMapping
  public AdminDashboardResponse getDashboard() {

    return adminDashboardService.getDashboard();
  }
}

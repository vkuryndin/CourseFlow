package org.courseflow.dashboard.controller;

import org.courseflow.dashboard.dto.TeacherDashboardResponse;
import org.courseflow.dashboard.service.TeacherDashboardService;
import org.courseflow.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/teacher")
public class TeacherDashboardController {

  private final TeacherDashboardService teacherDashboardService;

  public TeacherDashboardController(TeacherDashboardService teacherDashboardService) {
    this.teacherDashboardService = teacherDashboardService;
  }

  @GetMapping
  public TeacherDashboardResponse getDashboard(@AuthenticationPrincipal Jwt jwt) {
    return teacherDashboardService.getDashboard(CurrentUser.id(jwt));
  }
}

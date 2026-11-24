package org.courseflow.dashboard.controller;

import org.courseflow.security.CurrentUser;
import org.courseflow.dashboard.dto.StudentDashboardResponse;
import org.courseflow.dashboard.service.StudentDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/student")
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;

    public StudentDashboardController(
            StudentDashboardService studentDashboardService
    ) {
        this.studentDashboardService =
                studentDashboardService;
    }

    @GetMapping
    public StudentDashboardResponse getDashboard(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return studentDashboardService.getDashboard(
                CurrentUser.id(jwt)
        );
    }
}

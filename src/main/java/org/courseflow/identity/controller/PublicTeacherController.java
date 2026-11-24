package org.courseflow.identity.controller;

import org.courseflow.identity.dto.PublicTeacherResponse;
import org.courseflow.identity.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teachers")
public class PublicTeacherController {
    private final UserService userService;

    public PublicTeacherController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public PublicTeacherResponse get(@PathVariable Long id) {
        return userService.getPublicTeacher(id);
    }
}

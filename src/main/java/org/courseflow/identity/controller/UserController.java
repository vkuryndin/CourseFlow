
package org.courseflow.identity.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.courseflow.common.api.PageResponse;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.service.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getMe(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return userService.getById(userId);
    }

    @GetMapping("/{id}")
    public UserResponse getById(
            @PathVariable Long id
    ) {
        return userService.getById(id);
    }

    @GetMapping
    public PageResponse<UserResponse> findAll(
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size,

            @RequestParam(defaultValue = "")
            String q,

            @RequestParam(required = false)
            UserStatus status,

            @RequestParam(required = false)
            UserRole role,

            @RequestParam(defaultValue = "id")
            String sortBy,

            @RequestParam(defaultValue = "asc")
            String direction
    ) {
        return userService.findAll(
                page,
                size,
                q,
                status,
                role,
                sortBy,
                direction
        );
    }
}

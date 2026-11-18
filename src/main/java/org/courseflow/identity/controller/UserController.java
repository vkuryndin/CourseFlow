
package org.courseflow.identity.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.courseflow.common.api.PageResponse;
import org.courseflow.identity.dto.AdminCreateUserRequest;
import org.courseflow.identity.dto.UpdateUserRequest;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.service.AdminUserService;
import org.courseflow.identity.service.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AdminUserService adminUserService;

    public UserController(
            UserService userService,
            AdminUserService adminUserService
    ) {
        this.userService = userService;
        this.adminUserService = adminUserService;
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

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        UserResponse response = adminUserService.create(request);

        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return adminUserService.updateRoles(id, request);
    }

    @PutMapping("/{id}")
    public UserResponse updateDetails(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return adminUserService.updateDetails(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(
            @PathVariable Long id
    ) {
        return adminUserService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public UserResponse activate(
            @PathVariable Long id
    ) {
        return adminUserService.activate(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

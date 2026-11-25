package org.courseflow.identity.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.courseflow.common.api.PageResponse;
import org.courseflow.identity.dto.AdminCreateUserRequest;
import org.courseflow.identity.dto.UpdateUserRequest;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.service.AdminUserService;
import org.courseflow.identity.service.UserService;
import org.courseflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);
  private final UserService userService;
  private final AdminUserService adminUserService;

  public UserController(UserService userService, AdminUserService adminUserService) {
    this.userService = userService;
    this.adminUserService = adminUserService;
  }

  @GetMapping("/me")
  public UserResponse getMe(@AuthenticationPrincipal Jwt jwt) {
    Long userId = CurrentUser.id(jwt);
    return userService.getById(userId);
  }

  @GetMapping("/{id}")
  public UserResponse getById(@PathVariable Long id) {
    return userService.getById(id);
  }

  @GetMapping
  public PageResponse<UserResponse> findAll(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) UserRole role,
      @RequestParam(defaultValue = "id") String sortBy,
      @RequestParam(defaultValue = "asc") String direction) {
    return userService.findAll(page, size, q, status, role, sortBy, direction);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<UserResponse> create(
      @Valid @RequestBody AdminCreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
    UserResponse response = adminUserService.create(request);
    LOG.info("admin_action=create_user actor={} target={}", jwt.getSubject(), response.id());

    return ResponseEntity.created(URI.create("/api/users/" + response.id())).body(response);
  }

  @PutMapping("/{id}/roles")
  public UserResponse updateRoles(
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserRolesRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    UserResponse response = adminUserService.updateRoles(id, request);
    LOG.info("admin_action=update_roles actor={} target={}", jwt.getSubject(), id);
    return response;
  }

  @PutMapping("/{id}")
  public UserResponse updateDetails(
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    UserResponse response = adminUserService.updateDetails(id, request);
    LOG.info("admin_action=update_details actor={} target={}", jwt.getSubject(), id);
    return response;
  }

  @PostMapping("/{id}/deactivate")
  public UserResponse deactivate(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    UserResponse response = adminUserService.deactivate(id);
    LOG.info("admin_action=deactivate_user actor={} target={}", jwt.getSubject(), id);
    return response;
  }

  @PostMapping("/{id}/activate")
  public UserResponse activate(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    UserResponse response = adminUserService.activate(id);
    LOG.info("admin_action=activate_user actor={} target={}", jwt.getSubject(), id);
    return response;
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    adminUserService.deleteUser(id);
    LOG.info("admin_action=delete_user actor={} target={}", jwt.getSubject(), id);
    return ResponseEntity.noContent().build();
  }
}

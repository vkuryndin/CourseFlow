package org.courseflow.auth.controller;

import jakarta.validation.Valid;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.service.LogoutService;
import org.courseflow.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final AuthHttpOperations operations;
  private final LogoutService logoutService;

  public AuthController(AuthHttpOperations operations, LogoutService logoutService) {
    this.operations = operations;
    this.logoutService = logoutService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<RegistrationResponse> register(
      @Valid @RequestBody RegistrationRequest request) {
    RegistrationResponse response = operations.register(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return operations.login(request);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
    logoutService.logout(jwt);
    log.info("event=logout_succeeded userId={}", CurrentUser.id(jwt));

    return ResponseEntity.noContent().build();
  }
}

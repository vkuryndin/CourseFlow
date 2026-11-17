
package org.courseflow.auth.controller;

import jakarta.validation.Valid;

import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.service.AuthService;
import org.courseflow.auth.service.LogoutService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LogoutService logoutService;

    public AuthController(
            AuthService authService,
            LogoutService logoutService
    ) {
        this.authService = authService;
        this.logoutService = logoutService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        RegistrationResponse response =
                authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt
    ) {
        logoutService.logout(jwt);

        return ResponseEntity.noContent().build();
    }
}

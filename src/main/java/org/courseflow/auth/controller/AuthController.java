
package org.courseflow.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.service.AuthService;
import org.courseflow.auth.service.AuthRateLimiter;
import org.courseflow.auth.service.LogoutService;

import org.springframework.security.authentication.BadCredentialsException;
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
    private final AuthRateLimiter authRateLimiter;

    public AuthController(
            AuthService authService,
            LogoutService logoutService,
            AuthRateLimiter authRateLimiter
    ) {
        this.authService = authService;
        this.logoutService = logoutService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkRegistration(servletRequest.getRemoteAddr());
        RegistrationResponse response =
                authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkLogin(servletRequest.getRemoteAddr(), request.email());
        try {
            return authService.login(request);
        } catch (BadCredentialsException ex) {
            authRateLimiter.recordLoginFailure(request.email());
            throw ex;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt
    ) {
        logoutService.logout(jwt);

        return ResponseEntity.noContent().build();
    }
}

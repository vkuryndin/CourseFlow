
package org.courseflow.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.courseflow.auth.dto.ForgotPasswordRequest;
import org.courseflow.auth.dto.ResetPasswordRequest;
import org.courseflow.auth.service.PasswordResetService;
import org.courseflow.auth.service.AuthRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final AuthRateLimiter authRateLimiter;

    public PasswordResetController(
            PasswordResetService passwordResetService,
            AuthRateLimiter authRateLimiter
    ) {
        this.passwordResetService = passwordResetService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkForgotPassword(servletRequest.getRemoteAddr());
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkResetPassword(servletRequest.getRemoteAddr());
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}

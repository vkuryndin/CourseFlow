package org.courseflow.auth.controller;

import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.http.HttpStatus;

import org.courseflow.security.CurrentUser;
import jakarta.validation.Valid;
import org.courseflow.auth.dto.ChangePasswordRequest;
import org.courseflow.auth.service.PasswordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        Long userId = CurrentUser.id(jwt);

        passwordService.changePassword(userId, request);

        return ResponseEntity.noContent().build();
    }
}

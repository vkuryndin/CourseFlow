
package org.courseflow.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.courseflow.auth.service.AdminPasswordResetService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AdminPasswordResetController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPasswordResetController.class);
    private final AdminPasswordResetService passwordResetService;

    public AdminPasswordResetController(
            AdminPasswordResetService passwordResetService
    ) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        passwordResetService.sendResetLink(id);
        LOG.info("admin_action=request_password_reset actor={} target={}", jwt.getSubject(), id);

        return ResponseEntity.accepted().build();
    }
}

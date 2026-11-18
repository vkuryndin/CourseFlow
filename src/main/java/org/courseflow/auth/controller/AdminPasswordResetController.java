
package org.courseflow.auth.controller;

import org.courseflow.auth.service.AdminPasswordResetService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AdminPasswordResetController {

    private final AdminPasswordResetService passwordResetService;

    public AdminPasswordResetController(
            AdminPasswordResetService passwordResetService
    ) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id
    ) {
        passwordResetService.sendResetLink(id);

        return ResponseEntity.accepted().build();
    }
}

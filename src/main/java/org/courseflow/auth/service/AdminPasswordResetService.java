
package org.courseflow.auth.service;

import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.api.UserDirectory;

import org.springframework.stereotype.Service;

@Service
public class AdminPasswordResetService {

    private final UserDirectory userDirectory;
    private final PasswordResetService passwordResetService;

    public AdminPasswordResetService(
            UserDirectory userDirectory,
            PasswordResetService passwordResetService
    ) {
        this.userDirectory = userDirectory;
        this.passwordResetService = passwordResetService;
    }

    public void sendResetLink(Long userId) {

        User user = userDirectory.requireUser(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Password reset is available only "
                            + "for active users"
            );
        }

        if (user.getEmail() == null
                || user.getEmail().isBlank()) {
            throw new BusinessConflictException(
                    "User does not have an email address"
            );
        }

        passwordResetService.requestReset(user.getEmail());
    }
}

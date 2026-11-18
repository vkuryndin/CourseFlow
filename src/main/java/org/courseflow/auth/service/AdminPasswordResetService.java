
package org.courseflow.auth.service;

import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;

import org.springframework.stereotype.Service;

@Service
public class AdminPasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;

    public AdminPasswordResetService(
            UserRepository userRepository,
            PasswordResetService passwordResetService
    ) {
        this.userRepository = userRepository;
        this.passwordResetService = passwordResetService;
    }

    public void sendResetLink(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId
                        )
                );

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

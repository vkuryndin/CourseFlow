package org.courseflow.auth.service;

import org.courseflow.auth.dto.ChangePasswordRequest;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class PasswordService {

    private final UserAccountRepository userAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(
            UserAccountRepository userAccountRepository,
            AuthSessionRepository authSessionRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(
            Long userId,
            ChangePasswordRequest request
    ) {
        // Use the same account lock as login and password reset.
        UserAccount account = userAccountRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User account not found"
                        )
                );

        if (!passwordEncoder.matches(
                request.currentPassword(),
                account.getPasswordHash()
        )) {
            throw new BadRequestException(
                    "Current password is incorrect"
            );
        }

        String newPassword = request.newPassword();

        if (newPassword
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException(
                    "New password must not exceed 72 UTF-8 bytes"
            );
        }

        if (passwordEncoder.matches(
                newPassword,
                account.getPasswordHash()
        )) {
            throw new BadRequestException(
                    "New password must differ from current password"
            );
        }

        account.replacePasswordHash(
                passwordEncoder.encode(newPassword)
        );

        // Previously issued reset links must also stop working.
        tokenRepository.invalidateUnusedTokensForAccount(
                account.getId(),
                Instant.now()
        );

        authSessionRepository
                .findAllByUserAccount_User_IdAndRevokedFalse(userId)
                .forEach(AuthSession::revoke);
    }
}

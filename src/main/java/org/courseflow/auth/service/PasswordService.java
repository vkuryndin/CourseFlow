package org.courseflow.auth.service;

import org.courseflow.common.validation.PasswordBytes;
import java.time.Clock;
import org.courseflow.auth.dto.ChangePasswordRequest;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.UserStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.courseflow.auth.repository.AccountPasswordSnapshot;

@Service
public class PasswordService {

    private final Clock clock;
    private final TransactionTemplate transaction;

    private final UserAccountRepository userAccountRepository;
    private final CredentialLifecycle credentialLifecycle;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(
            UserAccountRepository userAccountRepository,
            CredentialLifecycle credentialLifecycle,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.userAccountRepository = userAccountRepository;
        this.credentialLifecycle = credentialLifecycle;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        PasswordBytes.requireFits(request.currentPassword(), "Current password");
        AccountPasswordSnapshot snapshot = userAccountRepository.findPasswordByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
        if (!passwordEncoder.matches(request.currentPassword(), snapshot.passwordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        String newPassword = request.newPassword();
        PasswordBytes.requireFits(newPassword, "New password");
        if (passwordEncoder.matches(newPassword, snapshot.passwordHash())) {
            throw new BadRequestException("New password must differ from current password");
        }
        String newHash = passwordEncoder.encode(newPassword);

        transaction.executeWithoutResult(status -> {
            UserAccount account = userAccountRepository.findByIdForUpdate(snapshot.accountId())
                    .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
            if (!account.getUser().getId().equals(userId)
                    || account.getUser().getStatus() != UserStatus.ACTIVE) {
                throw new AccessDeniedException("Active user is required");
            }
            // A concurrent reset/change must not be overwritten with a stale password.
            if (!snapshot.passwordHash().equals(account.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            account.replacePasswordHash(newHash);
            tokenRepository.invalidateUnusedTokensForAccount(account.getId(), clock.instant());
            credentialLifecycle.revokeSessions(userId);
        });
    }
}

package org.courseflow.auth.service;

import java.time.Clock;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CredentialLifecycle {
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository tokens;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public CredentialLifecycle(AuthSessionRepository sessions, PasswordResetTokenRepository tokens,
                               UserAccountRepository accounts, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.accounts = accounts;
        this.clock = clock;
    }

    // The caller owns the account lock.
    public void revokeSessions(Long userId) {
        sessions.revokeActiveSessionsByUserId(userId);
    }

    public void invalidateResetTokens(Long userId) {
        accounts.findByUser_Id(userId).ifPresent(account ->
                tokens.invalidateUnusedTokensForAccount(account.getId(), clock.instant()));
    }

    /** Serialize identity mutations with login and password operations, if credentials exist. */
    public void lockAccountIfPresent(Long userId) {
        accounts.findByUserIdForUpdate(userId);
    }

    /** The caller must hold the account lock before deleting the user's credentials. */
    public void deleteCredentials(Long userId) {
        var account = accounts.findByUser_Id(userId).orElseThrow(() ->
                new BusinessConflictException("User account is missing"));
        sessions.deleteAllByAccountId(account.getId());
        tokens.deleteAllByAccountId(account.getId());
        accounts.delete(account);
    }
}

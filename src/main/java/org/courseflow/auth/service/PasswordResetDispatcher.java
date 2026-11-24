package org.courseflow.auth.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.courseflow.common.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Keeps all account-dependent work outside the public HTTP request. */
@Service
public class PasswordResetDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetDispatcher.class);

    private final PasswordResetService passwordResetService;
    private final Executor executor;

    public PasswordResetDispatcher(
            PasswordResetService passwordResetService,
            @Qualifier("passwordResetExecutor") Executor executor
    ) {
        this.passwordResetService = passwordResetService;
        this.executor = executor;
    }

    public void enqueue(String email) {
        // Submit exactly the same task for every valid email, without
        // querying PostgreSQL or waiting for SMTP on the HTTP thread.
        try {
            executor.execute(() -> process(email));
        } catch (RejectedExecutionException ex) {
            // Same overload response regardless of account existence.
            throw new RateLimitExceededException(60);
        }
    }

    private void process(String email) {
        try {
            // Calls the transactional Spring proxy on the worker thread.
            // Token generation and after-commit email delivery stay intact.
            passwordResetService.requestReset(email);
        } catch (RuntimeException ex) {
            // Do not log the supplied email, raw reset token or exception
            // message, which may contain sensitive request data.
            log.error("Password reset processing failed: {}",
                    ex.getClass().getSimpleName());
        }
    }
}

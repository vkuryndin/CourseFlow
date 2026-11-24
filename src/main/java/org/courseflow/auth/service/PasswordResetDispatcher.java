package org.courseflow.auth.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.courseflow.common.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Arrays;
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
        String requestId = MDC.get("requestId");
        try {
            executor.execute(() -> process(email, requestId));
        } catch (RejectedExecutionException ex) {
            // Same overload response regardless of account existence.
            log.warn("Password reset queue is full; request rejected");
            throw new RateLimitExceededException(60);
        }
    }

    private void process(String email, String requestId) {
        String previous = MDC.get("requestId");
        if (requestId == null) MDC.remove("requestId");
        else MDC.put("requestId", requestId);
        try {
            passwordResetService.requestReset(email);
        } catch (RuntimeException ex) {
            // Stack frames are useful; messages and causes may contain credentials or email addresses.
            log.error("Password reset processing failed: {}; stack={}",
                    ex.getClass().getName(), Arrays.toString(ex.getStackTrace()));
        } finally {
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
    }
}

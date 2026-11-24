package org.courseflow.auth.controller;

import org.courseflow.auth.dto.*;
import org.courseflow.auth.service.*;
import org.courseflow.common.exception.RateLimitExceededException;
import org.courseflow.common.system.CourseFlowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** HTTP boundary: apply limits after request-body validation and before business operations. */
@Component
public class AuthHttpOperations {
    private static final Logger log = LoggerFactory.getLogger(AuthHttpOperations.class);
    private final CourseFlowMetrics metrics;
    private final AuthService auth;
    private final PasswordService passwords;
    private final PasswordResetService resets;
    private final PasswordResetDispatcher dispatcher;
    private final AuthRateLimiter limiter;

    public AuthHttpOperations(AuthService auth, PasswordService passwords, PasswordResetService resets,
                              PasswordResetDispatcher dispatcher, AuthRateLimiter limiter, CourseFlowMetrics metrics) {
        this.metrics = metrics;
        this.auth = auth;
        this.passwords = passwords;
        this.resets = resets;
        this.dispatcher = dispatcher;
        this.limiter = limiter;
    }

    private String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest().getRemoteAddr();
    }

    private void checkLimit(Runnable check) {
        try {
            check.run();
        } catch (RateLimitExceededException ex) {
            metrics.rateLimited();
            throw ex;
        }
    }

    public RegistrationResponse register(RegistrationRequest request) {
        checkLimit(() -> limiter.checkRegistration(clientIp()));
        RegistrationResponse response = auth.register(request);
        log.info("event=registration_succeeded userId={}", response.id());
        return response;
    }

    public LoginResponse login(LoginRequest request) {
        checkLimit(() -> limiter.checkLogin(clientIp(), request.email()));
        try {
            LoginResponse response = auth.login(request);
            log.info("event=login_succeeded userId={}", response.userId());
            return response;
        } catch (BadCredentialsException ex) {
            limiter.recordLoginFailure(request.email());
            log.warn("event=login_failed");
            throw ex;
        }
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        checkLimit(() -> limiter.checkChangePassword(clientIp(), userId));
        passwords.changePassword(userId, request);
        log.info("event=password_changed userId={}", userId);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        metrics.resetRequested();
        checkLimit(() -> limiter.checkForgotPassword(clientIp()));
        dispatcher.enqueue(request.email());
    }

    public void resetPassword(ResetPasswordRequest request) {
        checkLimit(() -> limiter.checkResetPassword(clientIp()));
        resets.resetPassword(request);
        log.info("event=password_reset_completed");
    }
}

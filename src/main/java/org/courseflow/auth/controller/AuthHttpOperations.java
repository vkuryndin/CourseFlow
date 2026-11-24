package org.courseflow.auth.controller;

import org.courseflow.auth.dto.*;
import org.courseflow.auth.service.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** HTTP boundary: apply limits after request-body validation and before business operations. */
@Component
public class AuthHttpOperations {
    private final AuthService auth;
    private final PasswordService passwords;
    private final PasswordResetService resets;
    private final PasswordResetDispatcher dispatcher;
    private final AuthRateLimiter limiter;

    public AuthHttpOperations(AuthService auth, PasswordService passwords, PasswordResetService resets,
                              PasswordResetDispatcher dispatcher, AuthRateLimiter limiter) {
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

    public RegistrationResponse register(RegistrationRequest request) {
        limiter.checkRegistration(clientIp());
        return auth.register(request);
    }

    public LoginResponse login(LoginRequest request) {
        limiter.checkLogin(clientIp(), request.email());
        try {
            return auth.login(request);
        } catch (BadCredentialsException ex) {
            limiter.recordLoginFailure(request.email());
            throw ex;
        }
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        limiter.checkChangePassword(clientIp(), userId);
        passwords.changePassword(userId, request);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        limiter.checkForgotPassword(clientIp());
        dispatcher.enqueue(request.email());
    }

    public void resetPassword(ResetPasswordRequest request) {
        limiter.checkResetPassword(clientIp());
        resets.resetPassword(request);
    }
}

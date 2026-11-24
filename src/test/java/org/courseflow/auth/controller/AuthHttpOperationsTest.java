package org.courseflow.auth.controller;

import org.courseflow.auth.dto.ChangePasswordRequest;
import org.courseflow.auth.dto.ForgotPasswordRequest;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.dto.ResetPasswordRequest;
import org.courseflow.auth.service.AuthRateLimiter;
import org.courseflow.auth.service.AuthRateLimiter.LoginReservation;
import org.courseflow.auth.service.AuthService;
import org.courseflow.auth.service.PasswordResetDispatcher;
import org.courseflow.auth.service.PasswordResetService;
import org.courseflow.auth.service.PasswordService;
import org.courseflow.common.exception.RateLimitExceededException;
import org.courseflow.common.system.CourseFlowMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthHttpOperationsTest {
    private static final String IP = "192.0.2.10";
    private final AuthService auth = mock(AuthService.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final PasswordResetService resets = mock(PasswordResetService.class);
    private final PasswordResetDispatcher dispatcher = mock(PasswordResetDispatcher.class);
    private final AuthRateLimiter limiter = mock(AuthRateLimiter.class);
    private final CourseFlowMetrics metrics = mock(CourseFlowMetrics.class);
    private final AuthHttpOperations operations = new AuthHttpOperations(auth, passwords, resets, dispatcher, limiter, metrics);
    private final LoginRequest login = new LoginRequest("student@example.test", "password123");

    @BeforeEach
    void bindRequest() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void successfulLoginReleasesReservation() {
        var reservation = mock(LoginReservation.class);
        var response = mock(LoginResponse.class);
        when(limiter.checkLogin(IP, login.email())).thenReturn(reservation);
        when(auth.login(login)).thenReturn(response);
        assertSame(response, operations.login(login));
        var order = inOrder(limiter, auth);
        order.verify(limiter).checkLogin(IP, login.email());
        order.verify(auth).login(login);
        order.verify(limiter).completeLogin(reservation, false);
    }

    @Test
    void wrongPasswordConsumesReservationAndUnexpectedFailureDoesNot() {
        var reservation = mock(LoginReservation.class);
        when(limiter.checkLogin(IP, login.email())).thenReturn(reservation);
        when(auth.login(login)).thenThrow(new BadCredentialsException("invalid"), new IllegalStateException("failure"));
        assertThrows(BadCredentialsException.class, () -> operations.login(login));
        assertThrows(IllegalStateException.class, () -> operations.login(login));
        verify(limiter).completeLogin(reservation, true);
        verify(limiter).completeLogin(reservation, false);
    }

    @Test
    void rateLimitedLoginNeverCallsAuthenticationService() {
        when(limiter.checkLogin(IP, login.email())).thenThrow(new RateLimitExceededException(10));
        assertThrows(RateLimitExceededException.class, () -> operations.login(login));
        verify(metrics).rateLimited();
        verifyNoInteractions(auth);
    }

    @Test
    void registrationAndPasswordChangesApplyLimitsBeforeBusinessOperations() {
        var request = new RegistrationRequest("Alice", login.email(), login.password());
        var response = mock(RegistrationResponse.class);
        when(auth.register(request)).thenReturn(response);
        assertSame(response, operations.register(request));
        var change = new ChangePasswordRequest("oldPassword", "newPassword");
        operations.changePassword(10L, change);
        var reset = new ResetPasswordRequest("token", "newPassword");
        operations.resetPassword(reset);
        var order = inOrder(limiter, auth, passwords, resets);
        order.verify(limiter).checkRegistration(IP);
        order.verify(auth).register(request);
        order.verify(limiter).checkChangePassword(IP, 10L);
        order.verify(passwords).changePassword(10L, change);
        order.verify(limiter).checkResetPassword(IP);
        order.verify(resets).resetPassword(reset);
    }

    @Test
    void forgotPasswordEnqueuesOnlyAcceptedRequests() {
        var request = new ForgotPasswordRequest(login.email());
        operations.forgotPassword(request);
        verify(dispatcher).enqueue(login.email());
        verify(metrics).resetRequested();
        verify(limiter).checkForgotPassword(IP);
        verifyNoInteractions(resets);
    }

    @Test
    void rejectedForgotPasswordIsCountedWithoutDispatching() {
        doThrow(new RateLimitExceededException(10)).when(limiter).checkForgotPassword(IP);
        assertThrows(RateLimitExceededException.class,
                () -> operations.forgotPassword(new ForgotPasswordRequest(login.email())));
        verify(metrics).resetRequested();
        verify(metrics).rateLimited();
        verifyNoInteractions(dispatcher, resets);
    }
}

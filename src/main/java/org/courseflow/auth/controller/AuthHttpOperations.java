package org.courseflow.auth.controller;

import org.courseflow.auth.dto.ChangePasswordRequest;
import org.courseflow.auth.dto.ForgotPasswordRequest;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.dto.ResetPasswordRequest;
import org.courseflow.auth.service.AuthRateLimiter;
import org.courseflow.auth.service.AuthService;
import org.courseflow.auth.service.PasswordResetDispatcher;
import org.courseflow.auth.service.PasswordResetService;
import org.courseflow.auth.service.PasswordService;
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

  public AuthHttpOperations(
      AuthService auth,
      PasswordService passwords,
      PasswordResetService resets,
      PasswordResetDispatcher dispatcher,
      AuthRateLimiter limiter,
      CourseFlowMetrics metrics) {
    this.metrics = metrics;
    this.auth = auth;
    this.passwords = passwords;
    this.resets = resets;
    this.dispatcher = dispatcher;
    this.limiter = limiter;
  }

  private String clientIp() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
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
    AuthRateLimiter.LoginReservation reservation;
    try {
      reservation = limiter.checkLogin(clientIp(), request.email());
    } catch (RateLimitExceededException ex) {
      metrics.rateLimited();
      throw ex;
    }
    boolean invalidCredentials = false;
    try {
      LoginResponse response = auth.login(request);
      log.info("event=login_succeeded userId={}", response.userId());
      return response;
    } catch (BadCredentialsException ex) {
      invalidCredentials = true;
      log.warn("event=login_failed");
      throw ex;
    } finally {
      limiter.completeLogin(reservation, invalidCredentials);
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

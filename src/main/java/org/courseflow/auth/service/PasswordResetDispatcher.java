package org.courseflow.auth.service;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.courseflow.common.exception.RateLimitExceededException;
import org.courseflow.common.system.CourseFlowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Keeps all account-dependent work outside the public HTTP request. */
@Service
public class PasswordResetDispatcher {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetDispatcher.class);

  private final PasswordResetService passwordResetService;
  private final Executor executor;
  private final CourseFlowMetrics metrics;

  public PasswordResetDispatcher(
      PasswordResetService passwordResetService,
      @Qualifier("passwordResetExecutor") Executor executor,
      CourseFlowMetrics metrics) {
    this.passwordResetService = passwordResetService;
    this.executor = executor;
    this.metrics = metrics;
  }

  public void enqueue(String email) {
    // Submit exactly the same task for every valid email, without
    // querying PostgreSQL or waiting for SMTP on the HTTP thread.
    String requestId = MDC.get("requestId");
    try {
      executor.execute(() -> process(email, requestId));
      metrics.resetAccepted();
      log.info("event=password_reset_accepted");
    } catch (RejectedExecutionException ex) {
      // Same overload response regardless of account existence.
      metrics.resetRejected();
      log.warn("event=password_reset_queue_rejected");
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
      metrics.resetProcessingFailed();
      // Stack frames are useful; messages and causes may contain credentials or email addresses.
      log.error(
          "Password reset processing failed: {}; stack={}",
          ex.getClass().getName(),
          Arrays.toString(ex.getStackTrace()));
    } finally {
      if (previous == null) MDC.remove("requestId");
      else MDC.put("requestId", previous);
    }
  }
}

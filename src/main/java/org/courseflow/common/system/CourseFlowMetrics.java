package org.courseflow.common.system;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Fixed metric names; no email, IP, user ID or other high-cardinality tags. */
@Component
public class CourseFlowMetrics {
  private final Counter rateLimited,
      resetRequests,
      resetAccepted,
      resetRejected,
      resetProcessingFailed,
      resetMailSent,
      resetMailFailed;

  public CourseFlowMetrics(MeterRegistry registry) {
    rateLimited = registry.counter("courseflow.auth.rate_limited");
    resetRequests = registry.counter("courseflow.password.reset.requests");
    resetAccepted = registry.counter("courseflow.password.reset.accepted");
    resetRejected = registry.counter("courseflow.password.reset.rejected");
    resetProcessingFailed = registry.counter("courseflow.password.reset.processing_failed");
    resetMailSent = registry.counter("courseflow.password.reset.mail_sent");
    resetMailFailed = registry.counter("courseflow.password.reset.mail_failed");
  }

  public void rateLimited() {
    rateLimited.increment();
  }

  public void resetRequested() {
    resetRequests.increment();
  }

  public void resetAccepted() {
    resetAccepted.increment();
  }

  public void resetRejected() {
    resetRejected.increment();
  }

  public void resetProcessingFailed() {
    resetProcessingFailed.increment();
  }

  public void resetMailSent() {
    resetMailSent.increment();
  }

  public void resetMailFailed() {
    resetMailFailed.increment();
  }
}

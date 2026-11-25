package org.courseflow.auth.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Test-only access to the package-private monotonic-clock constructor. */
public final class RateLimitTestTicker {
  private final AtomicLong nanos = new AtomicLong();

  public AuthRateLimiter limiter() {
    return new AuthRateLimiter(nanos::get);
  }

  public void advance(Duration duration) {
    nanos.addAndGet(duration.toNanos());
  }
}

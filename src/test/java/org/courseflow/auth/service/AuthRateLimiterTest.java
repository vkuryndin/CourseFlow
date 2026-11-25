package org.courseflow.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.courseflow.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {
  private final AtomicLong time = new AtomicLong();
  private final AuthRateLimiter limiter = new AuthRateLimiter(time::get);

  @Test
  void failedCredentialsAreRetainedAcrossIpAndCase() {
    for (int i = 0; i < 12; i++) {
      limiter.completeLogin(limiter.checkLogin("ip" + i, " USER@example.test "), true);
    }
    var ex =
        assertThrows(
            RateLimitExceededException.class,
            () -> limiter.checkLogin("other", "user@example.test"));

    assertEquals(60, ex.getRetryAfterSeconds());
    time.addAndGet(TimeUnit.SECONDS.toNanos(59));
    assertEquals(
        1,
        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkLogin("other", "user@example.test"))
            .getRetryAfterSeconds());
    time.addAndGet(TimeUnit.SECONDS.toNanos(1));
    assertDoesNotThrow(() -> limiter.checkLogin("other", "user@example.test"));
  }

  @Test
  void successfulLoginRefundsOnlyOwnReservation() {
    for (int i = 0; i < 20; i++) {
      limiter.completeLogin(limiter.checkLogin("ip", "a@example.test"), false);
    }
    for (int i = 0; i < 12; i++) {
      limiter.completeLogin(limiter.checkLogin("ip", "a@example.test"), true);
    }
    assertThrows(
        RateLimitExceededException.class, () -> limiter.checkLogin("new-ip", "a@example.test"));
  }

  @Test
  void duplicateCompletionCannotRefundAnotherReservation() {
    var first = limiter.checkLogin("ip", "a@example.test");
    limiter.completeLogin(first, false);
    for (int i = 0; i < 12; i++) {
      limiter.checkLogin("ip", "a@example.test");
    }
    limiter.completeLogin(first, false);

    assertThrows(
        RateLimitExceededException.class, () -> limiter.checkLogin("ip", "a@example.test"));
  }

  @Test
  void expiredReservationCannotRefundNewWindow() {
    var old = limiter.checkLogin("ip", "a@example.test");
    time.set(TimeUnit.MINUTES.toNanos(1));
    for (int i = 0; i < 12; i++) {
      limiter.checkLogin("ip", "a@example.test");
    }
    limiter.completeLogin(old, false);

    assertThrows(
        RateLimitExceededException.class, () -> limiter.checkLogin("ip", "a@example.test"));
  }

  @Test
  void rejectsForeignReservation() {
    var reservation = limiter.checkLogin("ip", "a@example.test");

    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthRateLimiter(time::get).completeLogin(reservation, false));
  }

  @Test
  void loginIpBudgetCountsSuccessfulLoginsToo() {
    for (int i = 0; i < 60; i++) {
      limiter.completeLogin(limiter.checkLogin("ip", "a@example.test"), false);
    }
    assertThrows(
        RateLimitExceededException.class, () -> limiter.checkLogin("ip", "different@example.test"));
  }

  @Test
  void independentRegistrationAndRecoveryBudgets() {
    for (int i = 0; i < 10; i++) {
      limiter.checkRegistration("ip");
      limiter.checkForgotPassword("ip");
    }
    assertThrows(RateLimitExceededException.class, () -> limiter.checkRegistration("ip"));
    assertThrows(RateLimitExceededException.class, () -> limiter.checkForgotPassword("ip"));
    for (int i = 0; i < 20; i++) {
      limiter.checkResetPassword("ip");
    }
    assertThrows(RateLimitExceededException.class, () -> limiter.checkResetPassword("ip"));
  }

  @Test
  void changePasswordLimitsUserAcrossIps() {
    for (int i = 0; i < 10; i++) {
      limiter.checkChangePassword("ip" + i, 1L);
    }
    assertThrows(RateLimitExceededException.class, () -> limiter.checkChangePassword("other", 1L));
    assertDoesNotThrow(() -> limiter.checkChangePassword("other", 2L));
  }

  @Test
  void changePasswordLimitsIpAcrossUsers() {
    for (long i = 0; i < 30; i++) {
      limiter.checkChangePassword("ip", i + 1);
    }
    assertThrows(RateLimitExceededException.class, () -> limiter.checkChangePassword("ip", 100L));
  }

  @Test
  void emailBudgetCannotBeEvictedByRotatingKeys() {
    for (int i = 0; i < 4096; i++) {
      limiter.completeLogin(limiter.checkLogin("ip" + i, "u" + i + "@example.test"), true);
    }
    assertThrows(
        RateLimitExceededException.class, () -> limiter.checkLogin("new-ip", "new@example.test"));
    time.set(TimeUnit.MINUTES.toNanos(1));
    assertDoesNotThrow(() -> limiter.checkLogin("new-ip", "new@example.test"));
  }

  @Test
  void simultaneousReservationsNeverExceedTwelve() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(24);
    CountDownLatch gate = new CountDownLatch(1);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 48; i++) {
        final int n = i;
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    limiter.checkLogin("ip" + n, "a@example.test");
                    return true;
                  } catch (RateLimitExceededException e) {
                    return false;
                  }
                }));
      }
      gate.countDown();
      int admitted = 0;
      for (var result : results) {
        if (result.get(5, TimeUnit.SECONDS)) {
          admitted++;
        }
      }
      assertEquals(12, admitted);
    } finally {
      pool.shutdownNow();
    }
  }
}

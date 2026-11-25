package org.courseflow.auth.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.courseflow.common.exception.RateLimitExceededException;
import org.courseflow.common.validation.EmailAddresses;
import org.springframework.stereotype.Component;

/** Bounded, local fixed-window limiter. All access is serialized. */
@Component
public class AuthRateLimiter {
  private static final long WINDOW = TimeUnit.MINUTES.toNanos(1);
  private static final int MAX_KEYS = 4_096; // per independent budget (28,672 total)
  private final LongSupplier ticker;
  // Separate budgets: rotating email addresses cannot evict IP counters,
  // nor can login traffic evict password recovery or registration counters.
  private final Windows loginIp = new Windows();
  private final Windows loginEmailFailures = new Windows();
  private final Windows registerIp = new Windows();
  private final Windows forgotIp = new Windows();
  private final Windows resetIp = new Windows();
  private final Windows changeIp = new Windows();
  private final Windows changeUser = new Windows();

  public AuthRateLimiter() {
    this(System::nanoTime);
  }

  // Monotonic clock seam for deterministic tests; not a wall clock.
  AuthRateLimiter(LongSupplier ticker) {
    this.ticker = ticker;
  }

  /** A request reserves an email slot before BCrypt, atomically with checking the budget. */
  public synchronized LoginReservation checkLogin(String ip, String email) {
    long now = ticker.getAsLong();
    loginIp.consume(ip, 60, now);
    String key = EmailAddresses.normalize(email);
    Window window = loginEmailFailures.reserve(key, 12, now);
    return new LoginReservation(this, key, window);
  }

  /**
   * Keep failed credentials in the budget; refund successful or aborted requests only. A
   * reservation belongs to exactly one window, even if a request finishes much later.
   */
  public synchronized void completeLogin(LoginReservation reservation, boolean invalidCredentials) {
    if (reservation.owner != this) {
      throw new IllegalArgumentException("Foreign login reservation");
    }
    if (reservation.completed) {
      return;
    }
    reservation.completed = true;
    if (!invalidCredentials) {
      loginEmailFailures.refund(reservation.key, reservation.window, ticker.getAsLong());
    }
  }

  public static final class LoginReservation {
    private final AuthRateLimiter owner;
    private final String key;
    private final Window window;
    private boolean completed;

    private LoginReservation(AuthRateLimiter owner, String key, Window window) {
      this.owner = owner;
      this.key = key;
      this.window = window;
    }
  }

  public synchronized void checkRegistration(String ip) {
    registerIp.consume(ip, 10, ticker.getAsLong());
  }

  public synchronized void checkForgotPassword(String ip) {
    forgotIp.consume(ip, 10, ticker.getAsLong());
  }

  public synchronized void checkResetPassword(String ip) {
    resetIp.consume(ip, 20, ticker.getAsLong());
  }

  public synchronized void checkChangePassword(String ip, Long userId) {
    long now = ticker.getAsLong();
    changeIp.consume(ip, 30, now);
    changeUser.consume(userId.toString(), 10, now);
  }

  private static final class Windows {
    private final Map<String, Window> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long lastCleanup;
    private boolean initialized;

    private void cleanup(long now) {
      if (!initialized || now - lastCleanup >= WINDOW) {
        entries.values().removeIf(w -> now - w.startedAt >= WINDOW);
        lastCleanup = now;
        initialized = true;
      }
    }

    private Window current(String key, long now) {
      cleanup(now);
      Window w = entries.get(key);
      if (w != null && now - w.startedAt >= WINDOW) {
        entries.remove(key);
        return null;
      }
      return w;
    }

    void check(String key, int limit, long now) {
      Window w = current(key, now);
      if (w != null && w.count >= limit) {
        long remaining = WINDOW - (now - w.startedAt);
        long second = TimeUnit.SECONDS.toNanos(1);
        throw new RateLimitExceededException(Math.max(1, (remaining + second - 1) / second));
      }
    }

    // Do not evict a live email window to make room for attacker-controlled keys.
    // The bounded email budget fails closed until a slot expires; IP budgets
    // retain their original bounded LRU behavior.
    Window reserve(String key, int limit, long now) {
      check(key, limit, now);
      Window w = current(key, now);
      if (w == null) {
        if (entries.size() >= MAX_KEYS) {
          entries.values().removeIf(entry -> now - entry.startedAt >= WINDOW);
          if (entries.size() >= MAX_KEYS) {
            long earliest =
                entries.values().stream()
                    .mapToLong(entry -> WINDOW - (now - entry.startedAt))
                    .min()
                    .orElse(WINDOW);
            long second = TimeUnit.SECONDS.toNanos(1);
            throw new RateLimitExceededException(Math.max(1, (earliest + second - 1) / second));
          }
        }
        w = new Window(now);
        entries.put(key, w);
      } else {
        w.count++;
      }
      return w;
    }

    void refund(String key, Window reserved, long now) {
      Window current = current(key, now);
      if (current == reserved && --current.count == 0) {
        entries.remove(key);
      }
    }

    void consume(String key, int limit, long now) {
      check(key, limit, now);
      record(key, limit, now);
    }

    void record(String key, int limit, long now) {
      Window w = current(key, now);
      if (w == null) {
        if (entries.size() >= MAX_KEYS) {
          var iterator = entries.keySet().iterator();
          iterator.next();
          iterator.remove();
        }
        entries.put(key, new Window(now));
      } else if (w.count < limit) {
        w.count++;
      }
    }
  }

  private static final class Window {
    private final long startedAt;
    private int count = 1;

    private Window(long startedAt) {
      this.startedAt = startedAt;
    }
  }
}

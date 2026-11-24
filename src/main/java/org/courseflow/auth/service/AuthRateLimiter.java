package org.courseflow.auth.service;

import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Local, bounded rate limiter for a single backend instance. */
@Component
public class AuthRateLimiter {

    private static final long WINDOW = TimeUnit.MINUTES.toNanos(1);
    private static final int MAX_KEYS = 20_000;
    private static final int LOGIN_PER_IP = 60;
    private static final int LOGIN_PER_EMAIL = 12;
    private static final int FORGOT_PER_IP = 10;
    private static final int RESET_PER_IP = 20;

    private final Map<String, Window> windows = new HashMap<>();
    private long lastCleanup = System.nanoTime();

    public synchronized void checkLogin(String ip, String email) {
        long now = System.nanoTime();
        cleanup(now);
        consume("login:ip:" + ip, LOGIN_PER_IP, now);
        consume("login:email:" + EmailAddresses.normalize(email),
                LOGIN_PER_EMAIL, now);
    }

    public synchronized void checkForgotPassword(String ip) {
        long now = System.nanoTime();
        cleanup(now);
        consume("forgot:ip:" + ip, FORGOT_PER_IP, now);
    }

    public synchronized void checkResetPassword(String ip) {
        long now = System.nanoTime();
        cleanup(now);
        consume("reset:ip:" + ip, RESET_PER_IP, now);
    }

    private void consume(String key, int limit, long now) {
        Window window = windows.get(key);
        if (window == null || now - window.startedAt >= WINDOW) {
            if (window == null && windows.size() >= MAX_KEYS) {
                removeExpired(now);
                if (windows.size() >= MAX_KEYS) {
                    throw new RateLimitExceededException(60);
                }
            }
            windows.put(key, new Window(now));
            return;
        }
        if (window.count >= limit) {
            long remaining = WINDOW - (now - window.startedAt);
            long seconds = (remaining + TimeUnit.SECONDS.toNanos(1) - 1)
                    / TimeUnit.SECONDS.toNanos(1);
            throw new RateLimitExceededException(Math.max(1, seconds));
        }
        window.count++;
    }

    private void cleanup(long now) {
        if (now - lastCleanup >= WINDOW) {
            removeExpired(now);
        }
    }

    private void removeExpired(long now) {
        windows.values().removeIf(window -> now - window.startedAt >= WINDOW);
        lastCleanup = now;
    }

    private static final class Window {
        private final long startedAt;
        private int count = 1;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}

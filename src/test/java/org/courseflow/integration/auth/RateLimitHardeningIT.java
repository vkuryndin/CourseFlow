package org.courseflow.integration.auth;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.courseflow.auth.service.AuthRateLimiter;
import org.courseflow.auth.service.RateLimitTestTicker;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Real controller and limiter, with only the monotonic time source controlled. */
@Import(RateLimitHardeningIT.LimiterConfiguration.class)
class RateLimitHardeningIT extends AdvancedHttpTest {
    private static final String WRONG = "WrongPassword2026!";

    @Autowired private RateLimitTestTicker ticker;

    @TestConfiguration(proxyBeanMethods = false)
    static class LimiterConfiguration {
        @Bean
        RateLimitTestTicker rateLimitTestTicker() {
            return new RateLimitTestTicker();
        }

        @Bean
        @Primary
        AuthRateLimiter testRateLimiter(RateLimitTestTicker ticker) {
            return ticker.limiter();
        }
    }

    @BeforeEach
    void startNewWindow() {
        ticker.advance(Duration.ofMinutes(1));
    }

    @Test
    void loginIpBudgetCannotBeBypassedByRotatingEmailsAndExpiresExactlyAtBoundary() throws Exception {
        for (int i = 0; i < 60; i++) {
            invalidLogin(email());
        }
        limited(loginRequest(student.email(), PASSWORD), 60);
        // Login exhaustion must not consume the independent registration budget.
        String registeredEmail = email();
        Object registration = expect(api("POST", "/api/auth/register", null, Map.of(
                "email", registeredEmail, "displayName", "Independent registration", "password", PASSWORD)), 201);
        assertEquals(1L, count("SELECT count(*) FROM users WHERE id = ? AND email = ?",
                id(registration), registeredEmail));
        ticker.advance(Duration.ofSeconds(59));
        limited(loginRequest(student.email(), PASSWORD), 1);
        ticker.advance(Duration.ofSeconds(1));
        assertLoginFor(student);
    }

    @Test
    void emailBudgetIsCaseInsensitiveAndDoesNotBlockOtherUsers() throws Exception {
        for (int i = 0; i < 12; i++) {
            invalidLogin(i % 2 == 0 ? student.email() : student.email().toUpperCase(Locale.ROOT));
        }
        limited(loginRequest(student.email().toUpperCase(Locale.ROOT), PASSWORD), 60);
        assertLoginFor(teacher);
        ticker.advance(Duration.ofMinutes(1));
        assertLoginFor(student);
    }

    @Test
    void resetPasswordIpBudgetRejectsTwentyFirstRequestAndRecovers() throws Exception {
        for (int i = 0; i < 20; i++) {
            invalidReset();
        }
        limited(resetRequest(), 60);
        assertLoginFor(student);
        ticker.advance(Duration.ofSeconds(59));
        limited(resetRequest(), 1);
        ticker.advance(Duration.ofSeconds(1));
        invalidReset();
    }

    @Test
    void changePasswordIpBudgetAggregatesDifferentAuthenticatedUsers() throws Exception {
        Actor a = actor(List.of("STUDENT"));
        Actor b = actor(List.of("STUDENT"));
        Actor c = actor(List.of("STUDENT"));
        Actor d = actor(List.of("STUDENT"));
        for (Actor user : List.of(a, b, c)) {
            for (int i = 0; i < 10; i++) {
                invalidChange(user);
            }
        }
        // The fourth account has not used its own change-password budget.
        limited(changeRequest(d), 60);
        ticker.advance(Duration.ofMinutes(1));
        invalidChange(d);
        // Wrong-password requests must not have changed any credentials.
        for (Actor user : List.of(a, b, c, d)) {
            assertLoginFor(user);
        }
    }

    private Response loginRequest(String email, String password) throws Exception {
        return api("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
    }

    private void invalidLogin(String email) throws Exception {
        assertEquals("INVALID_CREDENTIALS", field(expect(loginRequest(email, WRONG), 401), "code"));
    }

    private void assertLoginFor(Actor actor) throws Exception {
        Object body = expect(loginRequest(actor.email(), PASSWORD), 200);
        assertEquals(actor.id(), number(body, "userId"));
        String token = (String) field(body, "accessToken");
        assertFalse(token.isBlank());
        assertEquals(actor.id(), id(expect(api("GET", "/api/users/me", token, null), 200)));
    }

    private Response resetRequest() throws Exception {
        return api("POST", "/api/auth/reset-password", null,
                Map.of("token", unique(), "newPassword", NEW_PASSWORD));
    }

    private void invalidReset() throws Exception {
        Object body = expect(resetRequest(), 400);
        assertEquals("BAD_REQUEST", field(body, "code"));
        assertEquals("Invalid or expired reset token", field(body, "message"));
    }

    private Response changeRequest(Actor actor) throws Exception {
        return api("POST", "/api/auth/change-password", actor.token(),
                Map.of("currentPassword", WRONG, "newPassword", NEW_PASSWORD));
    }

    private void invalidChange(Actor actor) throws Exception {
        Object body = expect(changeRequest(actor), 400);
        assertEquals("BAD_REQUEST", field(body, "code"));
        assertEquals("Current password is incorrect", field(body, "message"));
    }

    private void limited(Response response, long retrySeconds) {
        assertEquals("RATE_LIMIT_EXCEEDED", field(expect(response, 429), "code"));
        assertEquals(Long.toString(retrySeconds), response.header("Retry-After"));
    }
}

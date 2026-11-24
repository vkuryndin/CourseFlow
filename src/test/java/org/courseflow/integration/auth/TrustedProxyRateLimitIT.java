package org.courseflow.integration.auth;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.courseflow.auth.service.RateLimitTestTicker;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.courseflow.integration.support.JsonChecks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Real HTTP and Tomcat, modeling requests delivered by the explicitly trusted Nginx peer. */
@Import(RateLimitHardeningIT.LimiterConfiguration.class)
@TestPropertySource(properties = "COURSEFLOW_TRUSTED_PROXY_IP=127.0.0.1")
class TrustedProxyRateLimitIT extends HttpIntegrationTest {
    @Autowired private RateLimitTestTicker ticker;

    @BeforeEach
    void startNewWindow() {
        ticker.advance(Duration.ofMinutes(1));
    }

    @Test
    void differentForwardedClientsHaveIndependentBudgets() throws Exception {
        for (int i = 0; i < 60; i++) {
            invalidLogin(login("192.0.2.10"));
        }
        limited(login("192.0.2.10"));
        invalidLogin(login("192.0.2.11"));
        ticker.advance(Duration.ofMinutes(1));
        invalidLogin(login("192.0.2.10"));
    }

    @Test
    void alternativeIpHeadersCannotOverrideForwardedFor() throws Exception {
        for (int i = 1; i <= 20; i++) {
            invalidReset(reset("X-Forwarded-For", "192.0.2.20",
                    "X-Real-IP", "198.51.100." + i,
                    "Forwarded", "for=203.0.113." + i));
        }
        limited(reset("X-Forwarded-For", "192.0.2.20",
                "X-Real-IP", "198.51.100.21", "Forwarded", "for=203.0.113.21"));
        invalidReset(reset("X-Forwarded-For", "192.0.2.21"));
    }

    @Test
    void missingForwardedForFallsBackToConnectionAddress() throws Exception {
        for (int i = 1; i <= 20; i++) {
            invalidReset(reset("X-Real-IP", "198.51.100." + i));
        }
        limited(reset());
        invalidReset(reset("X-Forwarded-For", "192.0.2.30"));
    }

    private Response login(String ip) throws Exception {
        return request("POST", "/api/auth/login", JsonChecks.json(Map.of(
                "email", "proxy." + UUID.randomUUID() + "@example.invalid",
                "password", PASSWORD)), "Content-Type", "application/json",
                "X-Forwarded-For", ip);
    }

    private void invalidLogin(Response response) {
        assertEquals("INVALID_CREDENTIALS", field(expect(response, 401), "code"));
    }

    private Response reset(String... extraHeaders) throws Exception {
        String[] headers = new String[extraHeaders.length + 2];
        headers[0] = "Content-Type";
        headers[1] = "application/json";
        System.arraycopy(extraHeaders, 0, headers, 2, extraHeaders.length);
        return request("POST", "/api/auth/reset-password", JsonChecks.json(Map.of(
                "token", UUID.randomUUID().toString(), "newPassword", NEW_PASSWORD)), headers);
    }

    private void invalidReset(Response response) {
        Object body = expect(response, 400);
        assertEquals("BAD_REQUEST", field(body, "code"));
        assertEquals("Invalid or expired reset token", field(body, "message"));
    }

    private void limited(Response response) {
        assertEquals("RATE_LIMIT_EXCEEDED", field(expect(response, 429), "code"));
        assertEquals("60", response.header("Retry-After"));
    }
}

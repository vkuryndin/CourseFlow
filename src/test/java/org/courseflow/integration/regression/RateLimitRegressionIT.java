package org.courseflow.integration.regression;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.courseflow.integration.support.JsonChecks.str;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Isolated 59-step rate limit regression, with a fresh Spring context and limiter. */
class RateLimitRegressionIT extends HttpIntegrationTest {
  private static final String WRONG_PASSWORD = "WrongPassword2026!";
  private final String email = "rate." + UUID.randomUUID() + "@courseflow.local";
  private String token;
  private boolean failed;

  @TestFactory
  List<DynamicTest> rateLimits() {
    List<DynamicTest> tests = new ArrayList<>();
    var registration =
        Map.of("email", email, "displayName", "Rate limit regression", "password", PASSWORD);
    tests.add(
        step(
            "Register disposable user",
            () -> expect(api("POST", "/api/auth/register", null, registration), 201)));
    for (int i = 2; i <= 10; i++) {
      tests.add(
          step(
              "Register duplicate " + i,
              () -> expect(api("POST", "/api/auth/register", null, registration), 409)));
    }
    tests.add(
        step(
            "Registration IP limit",
            () -> limited(api("POST", "/api/auth/register", null, registration))));
    for (int i = 1; i <= 13; i++) {
      tests.add(
          step(
              "Successful login " + i,
              () -> {
                Object body =
                    expect(
                        api(
                            "POST",
                            "/api/auth/login",
                            null,
                            Map.of("email", email, "password", PASSWORD)),
                        200);
                token = str(field(body, "accessToken"));
                assertFalse(token.isBlank());
              }));
    }
    var badLogin = Map.of("email", email, "password", WRONG_PASSWORD);
    for (int i = 1; i <= 12; i++) {
      tests.add(
          step(
              "Failed login " + i,
              () -> expect(api("POST", "/api/auth/login", null, badLogin), 401)));
    }
    tests.add(
        step(
            "Login email failure limit",
            () -> limited(api("POST", "/api/auth/login", null, badLogin))));
    var change = Map.of("currentPassword", WRONG_PASSWORD, "newPassword", NEW_PASSWORD);
    for (int i = 1; i <= 10; i++) {
      tests.add(
          step(
              "Wrong current password " + i,
              () -> expect(api("POST", "/api/auth/change-password", token, change), 400)));
    }
    tests.add(
        step(
            "Change password user limit",
            () -> limited(api("POST", "/api/auth/change-password", token, change))));
    for (int i = 1; i <= 10; i++) {
      tests.add(
          step(
              "Unknown email recovery " + i,
              () ->
                  expect(
                      api(
                          "POST",
                          "/api/auth/forgot-password",
                          null,
                          Map.of("email", "unknown." + UUID.randomUUID() + "@courseflow.local")),
                      202)));
    }
    tests.add(
        step(
            "Forgot password IP limit",
            () ->
                limited(
                    api(
                        "POST",
                        "/api/auth/forgot-password",
                        null,
                        Map.of("email", "unknown." + UUID.randomUUID() + "@courseflow.local")))));
    return tests;
  }

  private void limited(Response response) {
    Object body = expect(response, 429);
    assertEquals("RATE_LIMIT_EXCEEDED", field(body, "code"));
    assertEquals(429L, org.courseflow.integration.support.JsonChecks.requiredInteger(body, "status"));
    assertEquals(field(body, "requestId"), response.header("X-Request-Id"));
    assertTrue(response.header("X-Request-Id") != null && !response.header("X-Request-Id").isBlank());
    long retry = Long.parseLong(response.header("Retry-After"));
    assertTrue(retry >= 1 && retry <= 60);
  }

  private DynamicTest step(String name, Executable action) {
    return DynamicTest.dynamicTest(
        name,
        () -> {
          Assumptions.assumeFalse(failed, "A prerequisite rate limit step failed");
          try {
            action.execute();
          } catch (Throwable error) {
            failed = true;
            throw error;
          }
        });
  }
}

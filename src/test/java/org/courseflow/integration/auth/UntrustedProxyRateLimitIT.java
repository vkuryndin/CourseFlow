package org.courseflow.integration.auth;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.courseflow.integration.support.JsonChecks;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** The default direct-access configuration must never trust arbitrary client headers. */
@Import(RateLimitHardeningIT.LimiterConfiguration.class)
@TestPropertySource(properties = "COURSEFLOW_TRUSTED_PROXY_IP=")
class UntrustedProxyRateLimitIT extends HttpIntegrationTest {
  @Test
  void forgedForwardingHeadersCannotCreateNewIpBudgets() throws Exception {
    for (int i = 1; i <= 20; i++) {
      Object body = expect(reset(i), 400);
      assertEquals("BAD_REQUEST", field(body, "code"));
      assertEquals("Invalid or expired reset token", field(body, "message"));
    }
    Response limited = reset(21);
    assertEquals("RATE_LIMIT_EXCEEDED", field(expect(limited, 429), "code"));
    assertEquals("60", limited.header("Retry-After"));
  }

  private Response reset(int suffix) throws Exception {
    return request(
        "POST",
        "/api/auth/reset-password",
        JsonChecks.json(Map.of("token", UUID.randomUUID().toString(), "newPassword", NEW_PASSWORD)),
        "Content-Type",
        "application/json",
        "X-Forwarded-For",
        "192.0.2." + suffix,
        "X-Real-IP",
        "198.51.100." + suffix,
        "Forwarded",
        "for=203.0.113." + suffix);
  }
}

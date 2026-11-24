package org.courseflow.integration.auth;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Repeat the direct spoofing test with a trusted proxy configured at a different IP. */
@Import(RateLimitHardeningIT.LimiterConfiguration.class)
@TestPropertySource(properties = "COURSEFLOW_TRUSTED_PROXY_IP=192.0.2.99", inheritProperties = false)
class NonMatchingProxyRateLimitIT extends UntrustedProxyRateLimitIT {
}

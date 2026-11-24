package org.courseflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void corsAllowsOnlyExplicitOriginsAndExposesRequiredHeaders() {
        var source = config.corsConfigurationSource(" https://example.test, ,http://localhost:5173 ");
        var request = new MockHttpServletRequest("GET", "/api/courses");
        var cors = source.getCorsConfiguration(request);
        assertEquals(List.of("https://example.test", "http://localhost:5173"), cors.getAllowedOrigins());
        assertFalse(cors.getAllowCredentials());
        assertTrue(cors.getAllowedMethods().contains("PATCH"));
        assertTrue(cors.getExposedHeaders().contains("X-Request-Id"));
    }

    @Test
    void wildcardOriginIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurationSource("https://example.test,*"));
    }

    @Test
    void passwordEncoderHashesAndChecksPasswords() {
        var encoder = config.passwordEncoder();
        var hash = encoder.encode("Example-password-1");
        assertTrue(encoder.matches("Example-password-1", hash));
        assertFalse(encoder.matches("incorrect", hash));
    }
}

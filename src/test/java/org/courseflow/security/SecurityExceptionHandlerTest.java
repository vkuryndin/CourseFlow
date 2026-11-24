package org.courseflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityExceptionHandlerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SecurityExceptionHandler handler = new SecurityExceptionHandler(mapper, CLOCK);

    @Test
    void unauthorizedIncludesBearerChallengeAndMachineReadableError() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        var response = new MockHttpServletResponse();
        handler.commence(request, response, new BadCredentialsException("secret details"));
        var body = mapper.readTree(response.getContentAsString());
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals("AUTHENTICATION_REQUIRED", body.get("code").asText());
        assertEquals("Authentication is required", body.get("message").asText());
        assertEquals("/api/users/me", body.get("path").asText());
    }

    @Test
    void forbiddenDoesNotExposeInternalExceptionOrBearerChallenge() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/courses");
        var response = new MockHttpServletResponse();
        handler.handle(request, response, new AccessDeniedException("internal details"));
        var body = mapper.readTree(response.getContentAsString());
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("WWW-Authenticate"));
        assertEquals("ACCESS_DENIED", body.get("code").asText());
        assertEquals("Access denied", body.get("message").asText());
    }
}

package org.courseflow.common.exception;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler(CLOCK);
  private final MockHttpServletRequest request =
      new MockHttpServletRequest("GET", "/api/courses/1");

  private void check(ResponseEntity<ApiErrorResponse> response, int status, String code) {
    assertEquals(status, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(status, response.getBody().status());
    assertEquals(code, response.getBody().code());
    assertEquals(NOW, response.getBody().timestamp());
    assertEquals("/api/courses/1", response.getBody().path());
  }

  @Test
  void mapsExpectedBusinessErrors() {
    check(
        handler.handleNotFound(new ResourceNotFoundException("missing"), request),
        404,
        "RESOURCE_NOT_FOUND");
    check(handler.handleBadRequest(new BadRequestException("bad"), request), 400, "BAD_REQUEST");
    check(
        handler.handleConflict(new BusinessConflictException("conflict"), request),
        409,
        "BUSINESS_CONFLICT");
  }

  @Test
  void hidesSensitiveAccessDeniedAndCredentialMessages() {
    var denied = handler.handleAccessDenied(new AccessDeniedException("private"), request);
    check(denied, 403, "ACCESS_DENIED");

    assertEquals("Access denied", denied.getBody().message());
    var invalid = handler.handleBadCredentials(new BadCredentialsException("private"), request);
    check(invalid, 401, "INVALID_CREDENTIALS");
    assertEquals("Invalid email or password", invalid.getBody().message());
  }

  @Test
  void invalidSessionReturnsBearerChallenge() {
    var invalid = handler.handleInvalidSession(new InvalidSessionException(), request);
    check(invalid, 401, "INVALID_SESSION");

    assertEquals("Bearer", invalid.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
  }
}

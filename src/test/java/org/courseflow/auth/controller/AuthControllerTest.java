package org.courseflow.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.service.LogoutService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class AuthControllerTest {
  private final AuthHttpOperations operations = mock(AuthHttpOperations.class);
  private final LogoutService logoutService = mock(LogoutService.class);
  private final AuthController controller = new AuthController(operations, logoutService);

  @Test
  void registerDelegatesAndReturnsContract() {
    RegistrationRequest request = mock(RegistrationRequest.class);
    RegistrationResponse expected = mock(RegistrationResponse.class);
    when(operations.register(request)).thenReturn(expected);

    var actual = controller.register(request);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    verify(operations).register(request);
  }

  @Test
  void loginDelegatesAndReturnsContract() {
    LoginRequest request = mock(LoginRequest.class);
    LoginResponse expected = mock(LoginResponse.class);
    when(operations.login(request)).thenReturn(expected);

    var actual = controller.login(request);

    assertSame(expected, actual);
    verify(operations).login(request);
  }

  @Test
  void logoutDelegatesAndReturnsContract() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");

    var actual = controller.logout(jwt);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(logoutService).logout(jwt);
  }
}

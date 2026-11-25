package org.courseflow.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.auth.service.AdminPasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class AdminPasswordResetControllerTest {
  private final AdminPasswordResetService passwordResetService =
      mock(AdminPasswordResetService.class);
  private final AdminPasswordResetController controller =
      new AdminPasswordResetController(passwordResetService);

  @Test
  void resetPasswordDelegatesAndReturnsContract() {
    Long id = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");

    var actual = controller.resetPassword(id, jwt);

    assertEquals(202, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(passwordResetService).sendResetLink(id);
  }
}

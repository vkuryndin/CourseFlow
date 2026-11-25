package org.courseflow.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.courseflow.auth.dto.ForgotPasswordRequest;
import org.courseflow.auth.dto.ResetPasswordRequest;
import org.junit.jupiter.api.Test;

/** Direct controller contract tests; no Spring MVC runtime. */
class PasswordResetControllerTest {
  private final AuthHttpOperations operations = mock(AuthHttpOperations.class);
  private final PasswordResetController controller = new PasswordResetController(operations);

  @Test
  void forgotPasswordDelegatesAndReturnsContract() {
    ForgotPasswordRequest request = mock(ForgotPasswordRequest.class);

    var actual = controller.forgotPassword(request);

    assertEquals(202, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(operations).forgotPassword(request);
  }

  @Test
  void resetPasswordDelegatesAndReturnsContract() {
    ResetPasswordRequest request = mock(ResetPasswordRequest.class);

    var actual = controller.resetPassword(request);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(operations).resetPassword(request);
  }
}

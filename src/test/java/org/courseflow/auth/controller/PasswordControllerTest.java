package org.courseflow.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.auth.dto.ChangePasswordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class PasswordControllerTest {
    private final AuthHttpOperations operations = mock(AuthHttpOperations.class);
    private final PasswordController controller = new PasswordController(operations);

    @Test
    void changePasswordDelegatesAndReturnsContract() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        ChangePasswordRequest request = mock(ChangePasswordRequest.class);

        var actual = controller.changePassword(jwt, request);

        assertEquals(204, actual.getStatusCode().value());
        assertNull(actual.getBody());
        verify(operations).changePassword(42L, request);
    }
}

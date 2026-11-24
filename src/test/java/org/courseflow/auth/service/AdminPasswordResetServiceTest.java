package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.identity.api.UserDirectory;
import org.junit.jupiter.api.Test;

class AdminPasswordResetServiceTest {
    private final UserDirectory users = mock(UserDirectory.class);
    private final PasswordResetService resets = mock(PasswordResetService.class);
    private final AdminPasswordResetService service = new AdminPasswordResetService(users, resets);

    @Test
    void activeUserReceivesResetLink() {
        when(users.requireUser(10L)).thenReturn(student());
        service.sendResetLink(10L);

        verify(resets).requestReset("student@example.test");
    }

    @Test
    void inactiveOrEmailLessUserRejected() {
        var user = student();
        user.deactivate();
        when(users.requireUser(10L)).thenReturn(user);

        assertThrows(BusinessConflictException.class, () -> service.sendResetLink(10L));
        user.activate();
        user.updateDetails("Student", null);
        assertThrows(BusinessConflictException.class, () -> service.sendResetLink(10L));
        verifyNoInteractions(resets);
    }
}

package org.courseflow.identity.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.identity.dto.PublicTeacherResponse;
import org.courseflow.identity.service.UserService;
import org.junit.jupiter.api.Test;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class PublicTeacherControllerTest {
    private final UserService userService = mock(UserService.class);
    private final PublicTeacherController controller = new PublicTeacherController(userService);

    @Test
    void getDelegatesAndReturnsContract() {
        Long id = 101L;
        PublicTeacherResponse expected = mock(PublicTeacherResponse.class);
        when(userService.getPublicTeacher(id)).thenReturn(expected);

        var actual = controller.get(id);

        assertSame(expected, actual);
        verify(userService).getPublicTeacher(id);
    }
}

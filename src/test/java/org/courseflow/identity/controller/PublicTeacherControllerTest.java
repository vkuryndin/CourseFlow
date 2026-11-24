package org.courseflow.identity.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.common.api.PageResponse;
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
    void findAllDelegatesAndReturnsPage() {
        var expected = new PageResponse<>(List.of(new PublicTeacherResponse(101L, "Teacher")),
                0, 20, 1, 1, true, true);
        when(userService.findPublicTeachers(0, 20, "Tea")).thenReturn(expected);
        assertSame(expected, controller.findAll(0, 20, "Tea"));
        verify(userService).findPublicTeachers(0, 20, "Tea");
    }

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

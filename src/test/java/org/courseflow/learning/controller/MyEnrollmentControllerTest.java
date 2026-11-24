package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.EnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class MyEnrollmentControllerTest {
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final MyEnrollmentController controller = new MyEnrollmentController(enrollmentService);

    @Test
    void getMyEnrollmentsDelegatesAndReturnsContract() {
        EnrollmentStatus status = null;
        int page = 1;
        int size = 15;
        String sortBy = "id";
        String direction = "desc";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        PageResponse<EnrollmentListItemResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(enrollmentService.getMyEnrollments(42L, status, page, size, sortBy, direction)).thenReturn(expected);

        var actual = controller.getMyEnrollments(status, page, size, sortBy, direction, jwt);

        assertSame(expected, actual);
        verify(enrollmentService).getMyEnrollments(42L, status, page, size, sortBy, direction);
    }
}

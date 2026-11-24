package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CourseProgressResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.ProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class ProgressControllerTest {
    private final ProgressService progressService = mock(ProgressService.class);
    private final ProgressController controller = new ProgressController(progressService);

    @Test
    void getMyProgressDelegatesAndReturnsContract() {
        Long courseId = 101L;
        EnrollmentStatus enrollmentStatus = null;
        int page = 1;
        int size = 15;
        String sortBy = "id";
        String direction = "desc";
        Boolean includeDetails = null;
        int detailPage = 3;
        int detailSize = 10;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        PageResponse<CourseProgressResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(progressService.getMyProgress(42L, courseId, enrollmentStatus, page, size, sortBy, direction, true,
                detailPage, detailSize)).thenReturn(expected);

        var actual = controller.getMyProgress(courseId, enrollmentStatus, page, size, sortBy, direction,
                includeDetails, detailPage, detailSize, jwt);

        assertSame(expected, actual);
        verify(progressService).getMyProgress(42L, courseId, enrollmentStatus, page, size, sortBy, direction, true,
                detailPage, detailSize);
    }

    @Test
    void summaryDefaultAndExplicitDetailsOverride() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        PageResponse<CourseProgressResponse> expected = new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
        when(progressService.getMyProgress(42L, null, null, 0, 20, "courseTitle", "asc", false, 0,
                50)).thenReturn(expected);

        assertSame(expected, controller.getMyProgress(null, null, 0, 20, "courseTitle", "asc", null, 0, 50, jwt));
        when(progressService.getMyProgress(42L, 1L, null, 0, 20, "courseTitle", "asc", false, 0,
                50)).thenReturn(expected);
        assertSame(expected, controller.getMyProgress(1L, null, 0, 20, "courseTitle", "asc", false, 0, 50, jwt));
    }
}

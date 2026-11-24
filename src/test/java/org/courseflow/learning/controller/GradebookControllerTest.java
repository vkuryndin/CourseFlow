package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.learning.dto.GradebookResponse;
import org.courseflow.learning.dto.GradebookResponse.AssessmentType;
import org.courseflow.learning.dto.GradebookResponse.ReviewStatus;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.GradebookService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class GradebookControllerTest {
    private final GradebookService gradebookService = mock(GradebookService.class);
    private final GradebookController controller = new GradebookController(gradebookService);

    @Test
    void getGradebookDelegatesAndReturnsContract() {
        Long courseId = 101L;
        String q = "search";
        EnrollmentStatus enrollmentStatus = null;
        ReviewStatus reviewStatus = null;
        Long moduleId = 102L;
        AssessmentType type = null;
        int page = 1;
        int size = 15;
        int columnPage = 2;
        int columnSize = 25;
        String sortBy = "id";
        String direction = "desc";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        GradebookResponse expected = mock(GradebookResponse.class);
        when(gradebookService.getGradebook(courseId, 42L, true, q, enrollmentStatus, reviewStatus, moduleId, type,
                page, size, sortBy, direction, columnPage, columnSize)).thenReturn(expected);

        var actual = controller.getGradebook(courseId, q, enrollmentStatus, reviewStatus, moduleId, type, page, size,
                columnPage, columnSize, sortBy, direction, jwt, authentication);

        assertSame(expected, actual);
        verify(gradebookService).getGradebook(courseId, 42L, true, q, enrollmentStatus, reviewStatus, moduleId, type,
                page, size, sortBy, direction, columnPage, columnSize);
    }
}

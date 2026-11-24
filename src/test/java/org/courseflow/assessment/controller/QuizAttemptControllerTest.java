package org.courseflow.assessment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.assessment.dto.QuizAttemptDetailResponse;
import org.courseflow.assessment.dto.QuizAttemptResponse;
import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.service.QuizAttemptQueryService;
import org.courseflow.assessment.service.QuizAttemptService;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class QuizAttemptControllerTest {
    private final QuizAttemptService quizAttemptService = mock(QuizAttemptService.class);
    private final QuizAttemptQueryService quizAttemptQueryService = mock(QuizAttemptQueryService.class);
    private final QuizAttemptController controller = new QuizAttemptController(quizAttemptService,
            quizAttemptQueryService);

    @Test
    void getMyAttemptDelegatesAndReturnsContract() {
        Long quizId = 101L;
        Long attemptId = 102L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        QuizAttemptResponse expected = mock(QuizAttemptResponse.class);
        when(quizAttemptQueryService.getMyAttempt(quizId, attemptId, 42L)).thenReturn(expected);

        var actual = controller.getMyAttempt(quizId, attemptId, jwt);

        assertSame(expected, actual);
        verify(quizAttemptQueryService).getMyAttempt(quizId, attemptId, 42L);
    }

    @Test
    void getMyAttemptsDelegatesAndReturnsContract() {
        Long quizId = 101L;
        QuizSubmissionStatus status = null;
        int page = 1;
        int size = 15;
        String sortBy = "id";
        String direction = "desc";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        PageResponse<QuizAttemptResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(quizAttemptQueryService.getMyAttempts(quizId, 42L, status, page, size, sortBy,
                direction)).thenReturn(expected);

        var actual = controller.getMyAttempts(quizId, status, page, size, sortBy, direction, jwt);

        assertSame(expected, actual);
        verify(quizAttemptQueryService).getMyAttempts(quizId, 42L, status, page, size, sortBy, direction);
    }

    @Test
    void getQuizResultsDelegatesAndReturnsContract() {
        Long quizId = 101L;
        Long studentId = 102L;
        QuizSubmissionStatus status = null;
        int page = 1;
        int size = 15;
        String sortBy = "id";
        String direction = "desc";
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        PageResponse<QuizAttemptResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(quizAttemptQueryService.getQuizResults(quizId, 42L, true, studentId, status, page, size, sortBy,
                direction)).thenReturn(expected);

        var actual = controller.getQuizResults(quizId, studentId, status, page, size, sortBy, direction, jwt,
                authentication);

        assertSame(expected, actual);
        verify(quizAttemptQueryService).getQuizResults(quizId, 42L, true, studentId, status, page, size, sortBy,
                direction);
    }

    @Test
    void getMyResultDelegatesAndReturnsContract() {
        Long quizId = 101L;
        Long attemptId = 102L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        QuizAttemptDetailResponse expected = mock(QuizAttemptDetailResponse.class);
        when(quizAttemptQueryService.getMyResult(quizId, attemptId, 42L)).thenReturn(expected);

        var actual = controller.getMyResult(quizId, attemptId, jwt);

        assertSame(expected, actual);
        verify(quizAttemptQueryService).getMyResult(quizId, attemptId, 42L);
    }

    @Test
    void getResultForManagementDelegatesAndReturnsContract() {
        Long quizId = 101L;
        Long attemptId = 102L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        Authentication authentication = new UsernamePasswordAuthenticationToken("42", "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        QuizAttemptDetailResponse expected = mock(QuizAttemptDetailResponse.class);
        when(quizAttemptQueryService.getResultForManagement(quizId, attemptId, 42L, true)).thenReturn(expected);

        var actual = controller.getResultForManagement(quizId, attemptId, jwt, authentication);

        assertSame(expected, actual);
        verify(quizAttemptQueryService).getResultForManagement(quizId, attemptId, 42L, true);
    }

    @Test
    void startCreatesNewAttemptWithLocation() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        QuizAttemptResponse attempt = mock(QuizAttemptResponse.class);
        when(attempt.id()).thenReturn(9L);
        when(quizAttemptService.start(1L, 42L)).thenReturn(new QuizAttemptService.Started(attempt));

        var result = controller.start(1L, jwt);

        assertEquals(201, result.getStatusCode().value());
        assertEquals("/api/quizzes/1/attempts/9", result.getHeaders().getLocation().toString());
        assertSame(attempt, result.getBody());
    }

    @Test
    void startReturnsExistingAttemptOrRejectsLimit() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        QuizAttemptResponse attempt = mock(QuizAttemptResponse.class);
        when(quizAttemptService.start(1L, 42L)).thenReturn(new QuizAttemptService.Existing(attempt),
                new QuizAttemptService.LimitReached());

        var result = controller.start(1L, jwt);

        assertEquals(200, result.getStatusCode().value());
        assertSame(attempt, result.getBody());
        assertThrows(BusinessConflictException.class, () -> controller.start(1L, jwt));
    }

    @Test
    void submitReturnsResultOrRejectsExpiredAttempt() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        SubmitQuizRequest request = mock(SubmitQuizRequest.class);
        QuizAttemptResponse attempt = mock(QuizAttemptResponse.class);
        when(quizAttemptService.submit(1L, 2L, 42L, request)).thenReturn(java.util.Optional.of(attempt),
                java.util.Optional.empty());

        assertSame(attempt, controller.submit(1L, 2L, request, jwt));
        assertThrows(BusinessConflictException.class, () -> controller.submit(1L, 2L, request, jwt));
    }
}

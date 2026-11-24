package org.courseflow.assessment.service;

import jakarta.persistence.EntityManager;
import org.courseflow.assessment.dto.QuizAttemptDetailResponse.SelectedAnswer;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.courseflow.testing.Fixtures.student;
import static org.courseflow.testing.QueryMocks.count;
import static org.courseflow.testing.QueryMocks.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuizAttemptQueryServiceTest {
    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final QuizSubmissionRepository attempts = mock(QuizSubmissionRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final EntityManager em = mock(EntityManager.class);
    private final QuizAttemptQueryService service = new QuizAttemptQueryService(quizzes, attempts, guard, em, CLOCK);

    private void existingQuiz() {
        when(quizzes.findCourseIdByQuizId(80L)).thenReturn(Optional.of(40L));
        when(guard.find(40L)).thenReturn(course());
    }

    @Test
    void expiredReadDoesNotMutateStoredAttempt() {
        existingQuiz();
        var attempt = id(new QuizSubmission(quiz(), student(), 1, NOW.minusSeconds(600)), 90);
        when(attempts.findByIdAndQuiz_IdAndStudent_Id(90L, 80L, 10L)).thenReturn(Optional.of(attempt));

        var result = service.getMyAttempt(80L, 90L, 10L);

        assertEquals(QuizSubmissionStatus.EXPIRED, result.status());
        assertEquals(NOW, result.finishedAt());
        assertEquals(QuizSubmissionStatus.IN_PROGRESS, attempt.getStatus());
        assertNull(attempt.getFinishedAt());
        verifyNoInteractions(em);
    }

    @Test
    void missingQuizAndForeignAttemptAreNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getMyAttempt(80L, 90L, 10L));
        existingQuiz();
        assertThrows(ResourceNotFoundException.class, () -> service.getMyAttempt(80L, 90L, 99L));
        verifyNoInteractions(em);
    }

    @Test
    void historyBindsStudentStatusAndPagination() {
        existingQuiz();
        var attempt = id(new QuizSubmission(quiz(), student(), 1, NOW), 90);
        var q = query(em, QuizSubmission.class, List.of(attempt), "quizId", "studentId", "status", "now");
        count(em, 1, "quizId", "studentId", "status", "now");

        var result = service.getMyAttempts(80L, 10L, QuizSubmissionStatus.IN_PROGRESS, 0, 20,
                "finishedAt", "desc");

        assertEquals(90L, result.content().get(0).id());
        verify(q).setParameter("status", "IN_PROGRESS");
        verify(q).setParameter("studentId", 10L);
        verify(q).setParameter("now", NOW);
        verify(q).setMaxResults(20);
    }

    @Test
    void managementResultsRequireOwnershipOrAdmin() {
        existingQuiz();
        assertThrows(AccessDeniedException.class, () -> service.getQuizResults(80L, 99L, false, null,
                null, 0, 20, "id", "asc"));
        assertThrows(BadRequestException.class, () -> service.getQuizResults(80L, 20L, false, 0L,
                null, 0, 20, "id", "asc"));
        assertThrows(BadRequestException.class, () -> service.getQuizResults(80L, 99L, true, null,
                null, 0, 20, "bad", "asc"));
        verifyNoInteractions(em);
    }

    @Test
    void studentAndTeacherDetailsIncludeSelectedAnswers() {
        existingQuiz();
        var attempt = id(new QuizSubmission(quiz(), student(), 1, NOW.minusSeconds(10)), 90);
        attempt.complete(80, NOW);
        when(attempts.findByIdAndQuiz_IdAndStudent_Id(90L, 80L, 10L)).thenReturn(Optional.of(attempt));
        when(attempts.findByIdAndQuiz_Id(90L, 80L)).thenReturn(Optional.of(attempt));
        var answer = new SelectedAnswer(1L, "Question", 2L, "Answer");
        query(em, SelectedAnswer.class, List.of(answer));

        var studentResult = service.getMyResult(80L, 90L, 10L);
        var teacherResult = service.getResultForManagement(80L, 90L, 20L, false);

        assertEquals(80, studentResult.attempt().score());
        assertEquals(List.of(answer), studentResult.selectedAnswers());
        assertEquals(studentResult, teacherResult);
        assertThrows(AccessDeniedException.class, () -> service.getResultForManagement(80L, 90L, 99L, false));
    }
}

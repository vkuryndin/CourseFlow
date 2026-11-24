package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.service.EnrollmentAccess;
import org.junit.jupiter.api.Test;

class QuizAttemptServiceTest {
    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final QuizSubmissionRepository attempts = mock(QuizSubmissionRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
    private final StudentAccess students = mock(StudentAccess.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final QuizAttemptService service = new QuizAttemptService(quizzes, attempts, guard, enrollments,
            students, questions, em, CLOCK);
    private final Quiz quiz = quiz();
    private final org.courseflow.catalog.entity.Course c = quiz.getModule().getCourse();
    private final org.courseflow.identity.entity.User s = student();

    private void setup() {
        when(quizzes.findCourseIdByQuizId(80L)).thenReturn(Optional.of(40L));
        when(guard.lockShared(40L)).thenReturn(c);
        when(quizzes.existsById(80L)).thenReturn(true);
        when(quizzes.findById(80L)).thenReturn(Optional.of(quiz));
        when(students.lockActive(10L)).thenReturn(s);
        when(questions.countByQuiz_Id(80L)).thenReturn(1L);
    }

    private QuizSubmission attempt(int n, Instant start) {
        return id(new QuizSubmission(quiz, s, n, start), 90);
    }

    @Test
    void startsNewAttempt() {
        setup();
        when(attempts.saveAndFlush(any(QuizSubmission.class))).thenAnswer((i) -> id(i.getArgument(0), 90));

        var result = assertInstanceOf(QuizAttemptService.Started.class, service.start(80L, 10L));

        assertEquals(1, result.attempt().attemptNumber());
        assertEquals(NOW.plusSeconds(600), result.attempt().expiresAt());
    }

    @Test
    void activeAttemptIsReused() {
        setup();
        when(attempts.findFirstByQuiz_IdAndStudent_IdOrderByAttemptNumberDesc(80L,
                10L)).thenReturn(Optional.of(attempt(1, NOW)));

        var result = assertInstanceOf(QuizAttemptService.Existing.class, service.start(80L, 10L));

        assertEquals(90L, result.attempt().id());
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void expiresPreviousAttemptAndHonorsLimit() {
        setup();
        QuizSubmission previous = attempt(2, NOW.minusSeconds(600));
        when(attempts.findFirstByQuiz_IdAndStudent_IdOrderByAttemptNumberDesc(80L,
                10L)).thenReturn(Optional.of(previous));

        assertInstanceOf(QuizAttemptService.LimitReached.class, service.start(80L, 10L));
        assertEquals(QuizSubmissionStatus.EXPIRED, previous.getStatus());
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void noQuestionsAndArchivedCourseRejectStart() {
        setup();
        when(questions.countByQuiz_Id(80L)).thenReturn(0L);

        assertThrows(BusinessConflictException.class, () -> service.start(80L, 10L));
        c.archive();
        assertThrows(BusinessConflictException.class, () -> service.start(80L, 10L));
        verifyNoInteractions(attempts);
    }

    @Test
    void submitsCorrectAnswersAndPersistsSelections() {
        setup();
        QuizSubmission a = attempt(1, NOW);
        when(attempts.findOwnedForUpdate(90L, 80L, 10L)).thenReturn(Optional.of(a));
        Question q = id(quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1), 1);
        id(q.addAnswerOption("Right", true), 11);
        id(q.addAnswerOption("Wrong", false), 12);
        when(questions.findWithOptionsByQuizId(80L)).thenReturn(List.of(q));
        var request = new SubmitQuizRequest(List.of(new SubmitQuizRequest.AnswerRequest(1L, List.of(11L))));

        var result = service.submit(80L, 90L, 10L, request).orElseThrow();

        assertEquals(100, result.score());
        assertEquals(QuizSubmissionStatus.SUBMITTED, result.status());
        assertEquals(1, a.getSelections().size());
        verify(em).persist(a.getSelections().get(0));
        verify(em).flush();
    }

    @Test
    void expiredAttemptReturnsEmptyWithoutWritingSelections() {
        setup();
        QuizSubmission a = attempt(1, NOW.minusSeconds(600));
        when(attempts.findOwnedForUpdate(90L, 80L, 10L)).thenReturn(Optional.of(a));

        assertTrue(service.submit(80L, 90L, 10L, new SubmitQuizRequest(List.of())).isEmpty());
        assertEquals(QuizSubmissionStatus.EXPIRED, a.getStatus());
        verifyNoInteractions(em);
    }

    @Test
    void duplicateSubmissionRejected() {
        setup();
        QuizSubmission a = attempt(1, NOW);
        a.complete(100, NOW);
        when(attempts.findOwnedForUpdate(90L, 80L, 10L)).thenReturn(Optional.of(a));

        assertThrows(BusinessConflictException.class,
                () -> service.submit(80L, 90L, 10L, new SubmitQuizRequest(List.of())));
        verifyNoInteractions(em);
    }

    @Test
    void missingQuizOrForeignAttemptRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.start(80L, 10L));
        setup();
        assertThrows(ResourceNotFoundException.class,
                () -> service.submit(80L, 90L, 10L, new SubmitQuizRequest(List.of())));
    }
}

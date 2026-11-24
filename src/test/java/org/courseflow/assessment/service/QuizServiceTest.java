package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.courseflow.assessment.dto.CreateQuizRequest;
import org.courseflow.assessment.dto.UpdateQuizRequest;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.service.ContentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class QuizServiceTest {
    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final QuizSubmissionRepository attempts = mock(QuizSubmissionRepository.class);
    private final ContentStructure content = mock(ContentStructure.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final QuizService service = new QuizService(quizzes, content, guard, mock(ContentAccessService.class),
            attempts, mock(EntityManager.class));
    private final Quiz quiz = quiz();

    private void setup() {
        when(content.requireModule(50L)).thenReturn(quiz.getModule());
        when(guard.lock(40L)).thenReturn(quiz.getModule().getCourse());
        when(quizzes.findCourseIdByQuizId(80L)).thenReturn(Optional.of(40L));
        when(quizzes.findById(80L)).thenReturn(Optional.of(quiz));
    }

    @Test
    void createsQuizWithDefaultAttempts() {
        setup();
        when(quizzes.saveAndFlush(any(Quiz.class))).thenAnswer((i) -> id(i.getArgument(0), 80));
        var r = service.create(50L, new CreateQuizRequest(" New ", 5), 20L, false);

        assertEquals("New", r.title());
        assertEquals(5, r.timeLimit());
        assertEquals(2, r.maxAttempts());
    }

    @Test
    void rejectsDuplicateQuizAndNonOwner() {
        setup();

        assertThrows(AccessDeniedException.class,
                () -> service.create(50L, new CreateQuizRequest("New", 5), 99L, false));
        when(quizzes.findByModule_Id(50L)).thenReturn(Optional.of(quiz));
        assertThrows(BusinessConflictException.class,
                () -> service.create(50L, new CreateQuizRequest("New", 5), 20L, false));
        verify(quizzes, never()).saveAndFlush(any());
    }

    @Test
    void quizImmutableAfterAttempt() {
        setup();
        when(attempts.existsByQuiz_Id(80L)).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> service.updateQuiz(80L, new UpdateQuizRequest("New", 15, 3, 0L), 20L, false));
        assertThrows(BusinessConflictException.class, () -> service.deleteQuiz(80L, 20L, false));
    }

    @Test
    void updateQuizBeforeAttempts() {
        setup();
        var r = service.updateQuiz(80L, new UpdateQuizRequest(" New ", 15, 3, 0L), 20L, false);

        assertEquals("New", r.title());
        assertEquals(15, r.timeLimit());
        assertEquals(3, r.maxAttempts());
        verify(quizzes).flush();
    }
}

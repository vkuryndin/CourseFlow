package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;
import org.courseflow.assessment.dto.CreateQuestionRequest;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.service.ContentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class QuestionServiceCoverageTest {
    private final QuizService quizzes = mock(QuizService.class);
    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuizSubmissionRepository attempts = mock(QuizSubmissionRepository.class);
    private final ContentAccessService access = mock(ContentAccessService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final QuestionService service = new QuestionService(quizzes, quizRepository, access, questions, attempts, em, CLOCK);
    private final Quiz quiz = quiz();

    private CreateQuestionRequest request(QuestionType type, CreateQuestionRequest.OptionRequest... options) {
        return new CreateQuestionRequest(" New question ", type, 2, List.of(options), 0L);
    }

    private CreateQuestionRequest.OptionRequest option(String text, boolean correct) {
        return new CreateQuestionRequest.OptionRequest(text, correct);
    }

    private CreateQuestionRequest validRequest() {
        return request(QuestionType.SINGLE_CHOICE, option(" A ", true), option(" B ", false));
    }

    private void stubPersist() {
        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            id(question, 90);
            long next = 100;
            for (var option : question.getAnswerOptions()) {
                id(option, next++);
            }
            return null;
        }).when(em).persist(any(Question.class));
    }

    private Query stubBulkDelete() {
        Query query = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        return query;
    }

    private UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken("user", "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void createPersistsTrimmedQuestionAndOptions() {
        when(quizzes.requireQuizManagementAccess(80L, 20L, false, true)).thenReturn(quiz);
        stubPersist();
        var result = service.createQuestion(80L, validRequest(), 20L, false);
        assertEquals(90L, result.id());
        assertEquals("New question", result.text());
        assertEquals("A", result.options().get(0).text());
        assertTrue(result.options().get(0).correct());
        assertFalse(result.options().get(1).correct());
        verify(em).flush();
    }

    @Test
    void multipleChoiceWithTwoCorrectAndOneWrong() {
        when(quizzes.requireQuizManagementAccess(80L, 20L, false, true)).thenReturn(quiz);
        stubPersist();
        var result = service.createQuestion(80L, request(QuestionType.MULTIPLE_CHOICE, option("A", true), option("B", true), option("C", false)), 20L, false);
        assertEquals(3, result.options().size());
        assertEquals(2L, result.options().stream().filter(o -> o.correct()).count());
    }

    @Test
    void multipleChoiceNeedsTwoCorrect() {
        assertThrows(BadRequestException.class, () -> service.createQuestion(80L, request(QuestionType.MULTIPLE_CHOICE, option("A", true), option("B", false), option("C", false)), 20L, false));
        verifyNoInteractions(em);
    }

    @Test
    void multipleChoiceNeedsIncorrectOption() {
        assertThrows(BadRequestException.class, () -> service.createQuestion(80L, request(QuestionType.MULTIPLE_CHOICE, option("A", true), option("B", true)), 20L, false));
        verifyNoInteractions(em);
    }

    @Test
    void duplicateTrimmedOptionsRejected() {
        assertThrows(BadRequestException.class, () -> service.createQuestion(80L, request(QuestionType.SINGLE_CHOICE, option("A", true), option(" A ", false)), 20L, false));
        verifyNoInteractions(em);
    }

    @Test
    void missingQuizRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.getQuestions(80L, 10L, authentication("STUDENT")));
    }

    @Test
    void activeAttemptCanReadQuestions() {
        when(quizRepository.findById(80L)).thenReturn(Optional.of(quiz));
        when(attempts.existsByQuiz_IdAndStudent_IdAndStatusAndExpiresAtAfter(80L, 10L, QuizSubmissionStatus.IN_PROGRESS, NOW)).thenReturn(true);
        var question = id(quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1), 90);
        id(question.addAnswerOption("A", true), 100);
        when(questions.findWithOptionsByQuizId(80L)).thenReturn(List.of(question));
        assertEquals("Q", service.getQuestions(80L, 10L, authentication("STUDENT")).get(0).text());
        verify(attempts).existsByQuiz_IdAndStudent_IdAndStatusAndExpiresAtAfter(80L, 10L, QuizSubmissionStatus.IN_PROGRESS, NOW);
    }

    @Test
    void teacherAndAdminDoNotNeedAttempt() {
        when(quizRepository.findById(80L)).thenReturn(Optional.of(quiz));
        assertTrue(service.getQuestions(80L, 20L, authentication("TEACHER")).isEmpty());
        assertTrue(service.getQuestions(80L, 99L, authentication("ADMIN")).isEmpty());
        verifyNoInteractions(attempts);
    }

    @Test
    void managementShowsCorrectOptions() {
        var question = id(quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1), 90);
        id(question.addAnswerOption("A", true), 100);
        when(questions.findWithOptionsByQuizId(80L)).thenReturn(List.of(question));
        assertTrue(service.getQuestionsForManagement(80L, 20L, false).get(0).options().get(0).correct());
        verify(quizzes).requireQuizManagementAccess(80L, 20L, false, false);
    }

    @Test
    void updateRejectsOccupiedPosition() {
        var question = id(quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1), 90);
        when(questions.findByIdAndQuiz_Id(90L, 80L)).thenReturn(Optional.of(question));
        when(questions.existsByQuiz_IdAndPositionAndIdNot(80L, 2, 90L)).thenReturn(true);
        assertThrows(BusinessConflictException.class, () -> service.updateQuestion(80L, 90L, validRequest(), 20L, false));
        verifyNoInteractions(em);
    }

    @Test
    void updateClearsContextAndReloadsQuestion() {
        var oldQuestion = id(new Question(quiz, "Old", QuestionType.SINGLE_CHOICE, 1), 90);
        var reloaded = id(new Question(quiz, "Old", QuestionType.SINGLE_CHOICE, 1), 90);
        when(questions.findByIdAndQuiz_Id(90L, 80L)).thenReturn(Optional.of(oldQuestion), Optional.of(reloaded));
        Query query = stubBulkDelete();
        stubPersist();
        var result = service.updateQuestion(80L, 90L, validRequest(), 20L, false);
        assertEquals("New question", result.text());
        assertEquals(2, result.options().size());
        var order = inOrder(em, query, questions);
        order.verify(em).lock(oldQuestion, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        order.verify(query).executeUpdate();
        order.verify(em).clear();
        order.verify(questions).findByIdAndQuiz_Id(90L, 80L);
        order.verify(em).persist(reloaded);
        order.verify(em).flush();
    }

    @Test
    void deleteRemovesOptionsBeforeQuestion() {
        when(questions.findByIdAndQuiz_Id(90L, 80L)).thenReturn(Optional.of(new Question(quiz, "Q", QuestionType.SINGLE_CHOICE, 1)));
        Query options = mock(Query.class);
        Query question = mock(Query.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM AnswerOption"))).thenReturn(options);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM Question"))).thenReturn(question);
        when(options.setParameter("questionId", 90L)).thenReturn(options);
        when(question.setParameter("questionId", 90L)).thenReturn(question);
        when(question.setParameter("quizId", 80L)).thenReturn(question);
        service.deleteQuestion(80L, 90L, 20L, false);
        var order = inOrder(em, options, question);
        order.verify(em).createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM AnswerOption"));
        order.verify(options).executeUpdate();
        order.verify(em).createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM Question"));
        order.verify(question).executeUpdate();
        order.verify(em).clear();
    }

    @Test
    void missingQuestionCannotBeDeleted() {
        assertThrows(ResourceNotFoundException.class, () -> service.deleteQuestion(80L, 90L, 20L, false));
        verifyNoInteractions(em);
    }
}

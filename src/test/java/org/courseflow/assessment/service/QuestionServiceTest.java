package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.courseflow.testing.Fixtures.student;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.courseflow.assessment.dto.CreateQuestionRequest;
import org.courseflow.assessment.dto.QuestionResponse;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.content.api.ContentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class QuestionServiceTest {
  private final QuizService quizzes = mock(QuizService.class);
  private final QuizRepository quizRepo = mock(QuizRepository.class);
  private final QuestionRepository questions = mock(QuestionRepository.class);
  private final QuizSubmissionRepository attempts = mock(QuizSubmissionRepository.class);
  private final EntityManager em = mock(EntityManager.class);
  private final QuestionService service =
      new QuestionService(
          quizzes, quizRepo, mock(ContentAccessService.class), questions, attempts, em, CLOCK);
  private final Quiz quiz = quiz();

  private CreateQuestionRequest request(
      QuestionType type, CreateQuestionRequest.OptionRequest... options) {
    return new CreateQuestionRequest(" Question ", type, 1, List.of(options), null);
  }

  private CreateQuestionRequest.OptionRequest option(String text, boolean correct) {
    return new CreateQuestionRequest.OptionRequest(text, correct);
  }

  @Test
  void cannotCreateQuestionAfterAttempts() {
    when(quizzes.hasQuizSubmissions(80L)).thenReturn(true);

    assertThrows(
        BusinessConflictException.class,
        () ->
            service.createQuestion(
                80L,
                request(QuestionType.SINGLE_CHOICE, option("A", true), option("B", false)),
                20L,
                false));
    verifyNoInteractions(em);
  }

  @Test
  void singleChoiceRequiresExactlyOneCorrectOption() {
    when(quizzes.requireQuizManagementAccess(80L, 20L, false, true)).thenReturn(quiz);

    assertThrows(
        BadRequestException.class,
        () ->
            service.createQuestion(
                80L,
                request(QuestionType.SINGLE_CHOICE, option("A", false), option("B", false)),
                20L,
                false));
    assertThrows(
        BadRequestException.class,
        () ->
            service.createQuestion(
                80L,
                request(QuestionType.SINGLE_CHOICE, option("A", true), option("B", true)),
                20L,
                false));
    verifyNoInteractions(em);
  }

  @Test
  void duplicatePositionAndQuestionLimitRejected() {
    when(quizzes.requireQuizManagementAccess(80L, 20L, false, true)).thenReturn(quiz);
    when(questions.countByQuiz_Id(80L)).thenReturn(200L, 0L);
    when(questions.existsByQuiz_IdAndPosition(80L, 1)).thenReturn(true);
    var req = request(QuestionType.SINGLE_CHOICE, option("A", true), option("B", false));

    assertThrows(
        BusinessConflictException.class, () -> service.createQuestion(80L, req, 20L, false));
    assertThrows(
        BusinessConflictException.class, () -> service.createQuestion(80L, req, 20L, false));
    verifyNoInteractions(em);
  }

  @Test
  void studentCannotReadQuestionsWithoutActiveAttempt() {
    when(quizRepo.findById(80L)).thenReturn(Optional.of(quiz));
    var auth =
        new UsernamePasswordAuthenticationToken(
            "10", "", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

    assertThrows(BusinessConflictException.class, () -> service.getQuestions(80L, 10L, auth));
    verifyNoInteractions(questions);
  }

  @Test
  void questionProjectionHidesCorrectnessAndSortsOptions() {
    var q = id(quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1), 1);
    id(q.addAnswerOption("Wrong", false), 20);
    id(q.addAnswerOption("Right", true), 10);
    var student = QuestionResponse.forStudent(q);
    var teacher = QuestionResponse.forTeacher(q);

    assertEquals(
        List.of(10L, 20L),
        student.options().stream().map(QuestionResponse.OptionResponse::id).toList());
    assertTrue(teacher.options().get(0).correct());
    assertFalse(teacher.options().get(1).correct());
  }
}

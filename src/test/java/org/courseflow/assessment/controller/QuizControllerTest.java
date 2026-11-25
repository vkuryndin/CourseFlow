package org.courseflow.assessment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.assessment.dto.CreateQuestionRequest;
import org.courseflow.assessment.dto.CreateQuizRequest;
import org.courseflow.assessment.dto.QuestionResponse;
import org.courseflow.assessment.dto.QuizResponse;
import org.courseflow.assessment.dto.UpdateQuizRequest;
import org.courseflow.assessment.service.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class QuizControllerTest {
  private final QuizService quizService = mock(QuizService.class);
  private final org.courseflow.assessment.service.QuestionService questionService =
      mock(org.courseflow.assessment.service.QuestionService.class);
  private final QuizController controller = new QuizController(quizService, questionService);

  @Test
  void createDelegatesAndReturnsContract() {
    Long moduleId = 101L;
    CreateQuizRequest request = mock(CreateQuizRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    QuizResponse expected = mock(QuizResponse.class);
    when(expected.id()).thenReturn(999L);
    when(quizService.create(moduleId, request, 42L, true)).thenReturn(expected);

    var actual = controller.create(moduleId, request, jwt, authentication);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals("/api/quizzes/999", actual.getHeaders().getLocation().toString());
    verify(quizService).create(moduleId, request, 42L, true);
  }

  @Test
  void getByModuleDelegatesAndReturnsContract() {
    Long moduleId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    QuizResponse expected = mock(QuizResponse.class);
    when(quizService.getByModule(moduleId, 42L, authentication)).thenReturn(expected);

    var actual = controller.getByModule(moduleId, jwt, authentication);

    assertSame(expected, actual);
    verify(quizService).getByModule(moduleId, 42L, authentication);
  }

  @Test
  void getByIdDelegatesAndReturnsContract() {
    Long quizId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    QuizResponse expected = mock(QuizResponse.class);
    when(quizService.getById(quizId, 42L, authentication)).thenReturn(expected);

    var actual = controller.getById(quizId, jwt, authentication);

    assertSame(expected, actual);
    verify(quizService).getById(quizId, 42L, authentication);
  }

  @Test
  void createQuestionDelegatesAndReturnsContract() {
    Long quizId = 101L;
    CreateQuestionRequest request = mock(CreateQuestionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    QuestionResponse.ManagementResponse expected = mock(QuestionResponse.ManagementResponse.class);
    when(questionService.createQuestion(quizId, request, 42L, true)).thenReturn(expected);

    var actual = controller.createQuestion(quizId, request, jwt, authentication);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    verify(questionService).createQuestion(quizId, request, 42L, true);
  }

  @Test
  void getQuestionsDelegatesAndReturnsContract() {
    Long quizId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    List<QuestionResponse> expected = List.of(mock(QuestionResponse.class));
    when(questionService.getQuestions(quizId, 42L, authentication)).thenReturn(expected);

    var actual = controller.getQuestions(quizId, jwt, authentication);

    assertSame(expected, actual);
    verify(questionService).getQuestions(quizId, 42L, authentication);
  }

  @Test
  void getQuestionsForManagementDelegatesAndReturnsContract() {
    Long quizId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    List<QuestionResponse.ManagementResponse> expected =
        List.of(mock(QuestionResponse.ManagementResponse.class));
    when(questionService.getQuestionsForManagement(quizId, 42L, true)).thenReturn(expected);

    var actual = controller.getQuestionsForManagement(quizId, jwt, authentication);

    assertSame(expected, actual);
    verify(questionService).getQuestionsForManagement(quizId, 42L, true);
  }

  @Test
  void updateQuizDelegatesAndReturnsContract() {
    Long quizId = 101L;
    UpdateQuizRequest request = mock(UpdateQuizRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    QuizResponse expected = mock(QuizResponse.class);
    when(quizService.updateQuiz(quizId, request, 42L, true)).thenReturn(expected);

    var actual = controller.updateQuiz(quizId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(quizService).updateQuiz(quizId, request, 42L, true);
  }

  @Test
  void deleteQuizDelegatesAndReturnsContract() {
    Long quizId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.deleteQuiz(quizId, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(quizService).deleteQuiz(quizId, 42L, true);
  }

  @Test
  void updateQuestionDelegatesAndReturnsContract() {
    Long quizId = 101L;
    Long questionId = 102L;
    CreateQuestionRequest request = mock(CreateQuestionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    QuestionResponse.ManagementResponse expected = mock(QuestionResponse.ManagementResponse.class);
    when(questionService.updateQuestion(quizId, questionId, request, 42L, true))
        .thenReturn(expected);

    var actual = controller.updateQuestion(quizId, questionId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(questionService).updateQuestion(quizId, questionId, request, 42L, true);
  }

  @Test
  void deleteQuestionDelegatesAndReturnsContract() {
    Long quizId = 101L;
    Long questionId = 102L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var actual = controller.deleteQuestion(quizId, questionId, jwt, authentication);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(questionService).deleteQuestion(quizId, questionId, 42L, true);
  }
}

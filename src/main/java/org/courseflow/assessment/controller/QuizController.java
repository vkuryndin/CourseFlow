package org.courseflow.assessment.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.courseflow.assessment.dto.CreateQuestionRequest;
import org.courseflow.assessment.dto.CreateQuizRequest;
import org.courseflow.assessment.dto.QuestionResponse;
import org.courseflow.assessment.dto.QuizResponse;
import org.courseflow.assessment.dto.UpdateQuizRequest;
import org.courseflow.assessment.service.QuizService;
import org.courseflow.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizController {

  private final QuizService quizService;
  private final org.courseflow.assessment.service.QuestionService questionService;

  public QuizController(
      QuizService quizService, org.courseflow.assessment.service.QuestionService questionService) {
    this.quizService = quizService;
    this.questionService = questionService;
  }

  @PostMapping("/api/modules/{moduleId}/quiz")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<QuizResponse> create(
      @PathVariable Long moduleId,
      @Valid @RequestBody CreateQuizRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    Long currentUserId = CurrentUser.id(jwt);

    boolean isAdmin = CurrentUser.isAdmin(authentication);

    QuizResponse response = quizService.create(moduleId, request, currentUserId, isAdmin);

    return ResponseEntity.created(URI.create("/api/quizzes/" + response.id())).body(response);
  }

  @GetMapping("/api/modules/{moduleId}/quiz")
  public QuizResponse getByModule(
      @PathVariable Long moduleId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return quizService.getByModule(moduleId, CurrentUser.id(jwt), authentication);
  }

  @GetMapping("/api/quizzes/{quizId}")
  public QuizResponse getById(
      @PathVariable Long quizId, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    return quizService.getById(quizId, CurrentUser.id(jwt), authentication);
  }

  @PostMapping("/api/quizzes/{quizId}/questions")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<QuestionResponse.ManagementResponse> createQuestion(
      @PathVariable Long quizId,
      @Valid @RequestBody CreateQuestionRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    QuestionResponse.ManagementResponse response =
        questionService.createQuestion(quizId, request, CurrentUser.id(jwt), isAdmin);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/quizzes/{quizId}/questions")
  public List<QuestionResponse> getQuestions(
      @PathVariable Long quizId, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    return questionService.getQuestions(quizId, CurrentUser.id(jwt), authentication);
  }

  @GetMapping("/api/quizzes/{quizId}/questions/manage")
  public List<QuestionResponse.ManagementResponse> getQuestionsForManagement(
      @PathVariable Long quizId, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    return questionService.getQuestionsForManagement(quizId, CurrentUser.id(jwt), isAdmin);
  }

  @PutMapping("/api/quizzes/{quizId}")
  public QuizResponse updateQuiz(
      @PathVariable Long quizId,
      @Valid @RequestBody UpdateQuizRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    return quizService.updateQuiz(quizId, request, CurrentUser.id(jwt), isAdmin);
  }

  @DeleteMapping("/api/quizzes/{quizId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> deleteQuiz(
      @PathVariable Long quizId, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    quizService.deleteQuiz(quizId, CurrentUser.id(jwt), isAdmin);

    return ResponseEntity.noContent().build();
  }

  @PutMapping("/api/quizzes/{quizId}/questions/{questionId}")
  public QuestionResponse.ManagementResponse updateQuestion(
      @PathVariable Long quizId,
      @PathVariable Long questionId,
      @Valid @RequestBody CreateQuestionRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    return questionService.updateQuestion(
        quizId, questionId, request, CurrentUser.id(jwt), isAdmin);
  }

  @DeleteMapping("/api/quizzes/{quizId}/questions/{questionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> deleteQuestion(
      @PathVariable Long quizId,
      @PathVariable Long questionId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    boolean isAdmin = CurrentUser.isAdmin(authentication);

    questionService.deleteQuestion(quizId, questionId, CurrentUser.id(jwt), isAdmin);

    return ResponseEntity.noContent().build();
  }
}

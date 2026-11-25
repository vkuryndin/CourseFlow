package org.courseflow.assessment.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.courseflow.assessment.dto.CreateQuestionRequest;
import org.courseflow.assessment.dto.QuestionResponse;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.api.CourseAccessPolicy;
import org.courseflow.catalog.entity.Course;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.common.security.CurrentUser;
import org.courseflow.content.api.ContentAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

  private final Clock clock;
  private final QuizService quizService;
  private final QuizRepository quizRepository;
  private final ContentAccessService contentAccessService;
  private final QuestionRepository questionRepository;
  private final QuizSubmissionRepository quizSubmissionRepository;
  private final EntityManager entityManager;

  public QuestionService(
      QuizService quizService,
      QuizRepository quizRepository,
      ContentAccessService contentAccessService,
      QuestionRepository questionRepository,
      QuizSubmissionRepository quizSubmissionRepository,
      EntityManager entityManager,
      Clock clock) {
    this.clock = clock;
    this.quizService = quizService;
    this.quizRepository = quizRepository;
    this.contentAccessService = contentAccessService;
    this.questionRepository = questionRepository;
    this.quizSubmissionRepository = quizSubmissionRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public QuestionResponse.ManagementResponse createQuestion(
      Long quizId, CreateQuestionRequest request, Long currentUserId, boolean isAdmin) {
    final Quiz quiz = quizService.requireQuizManagementAccess(quizId, currentUserId, isAdmin, true);

    if (quizService.hasQuizSubmissions(quizId)) {
      throw new BusinessConflictException("Cannot modify Quiz after student attempts");
    }

    validateQuestion(request);

    long questionCount = questionRepository.countByQuiz_Id(quizId);
    if (questionCount >= 200) {
      throw new BusinessConflictException("Quiz cannot have more than 200 questions");
    }

    if (questionRepository.existsByQuiz_IdAndPosition(quizId, request.position())) {
      throw new BusinessConflictException("Question position is already occupied");
    }

    Question question = quiz.addQuestion(request.text().trim(), request.type(), request.position());

    for (CreateQuestionRequest.OptionRequest option : request.options()) {

      question.addAnswerOption(option.text().trim(), option.correct());
    }

    entityManager.persist(question);
    entityManager.flush();

    return QuestionResponse.forTeacher(question);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<QuestionResponse> getQuestions(
      Long quizId, Long currentUserId, Authentication authentication) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

    contentAccessService.checkCourseAccess(
        quiz.getModule().getCourse().getId(), currentUserId, authentication);

    Course course = quiz.getModule().getCourse();
    boolean admin = CurrentUser.hasRole(authentication, "ADMIN");
    boolean owner =
        course.getTeacher().getId().equals(currentUserId)
            && CurrentUser.hasRole(authentication, "TEACHER");
    if (!admin && !owner) {
      CourseAccessPolicy.requireActive(course, "Quiz is in an archived course");
      boolean activeAttempt =
          quizSubmissionRepository.existsByQuiz_IdAndStudent_IdAndStatusAndExpiresAtAfter(
              quizId, currentUserId, QuizSubmissionStatus.IN_PROGRESS, clock.instant());
      if (!activeAttempt) {
        throw new BusinessConflictException(
            "Start an active Quiz attempt before reading questions");
      }
    }

    return loadQuestions(quizId).stream().map(QuestionResponse::forStudent).toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<QuestionResponse.ManagementResponse> getQuestionsForManagement(
      Long quizId, Long currentUserId, boolean isAdmin) {
    quizService.requireQuizManagementAccess(quizId, currentUserId, isAdmin, false);

    return loadQuestions(quizId).stream().map(QuestionResponse::forTeacher).toList();
  }

  private List<Question> loadQuestions(Long quizId) {
    return questionRepository.findWithOptionsByQuizId(quizId);
  }

  private void validateQuestion(CreateQuestionRequest request) {
    long correctCount =
        request.options().stream().filter(option -> Boolean.TRUE.equals(option.correct())).count();

    if (request.type() == QuestionType.SINGLE_CHOICE && correctCount != 1) {

      throw new BadRequestException("SINGLE_CHOICE must have exactly one correct option");
    }

    if (request.type() == QuestionType.MULTIPLE_CHOICE
        && (correctCount < 2 || correctCount == request.options().size())) {

      throw new BadRequestException(
          "MULTIPLE_CHOICE must have at least two correct options and one incorrect option");
    }

    long distinctOptions =
        request.options().stream().map(option -> option.text().trim()).distinct().count();

    if (distinctOptions != request.options().size()) {
      throw new BadRequestException("Duplicate answer options are not allowed");
    }
  }

  @Transactional
  public QuestionResponse.ManagementResponse updateQuestion(
      Long quizId,
      Long questionId,
      CreateQuestionRequest request,
      Long currentUserId,
      boolean isAdmin) {
    quizService.requireEditableQuiz(quizId, currentUserId, isAdmin);

    Question existing = findQuestion(quizId, questionId);

    validateQuestion(request);

    if (questionRepository.existsByQuiz_IdAndPositionAndIdNot(
        quizId, request.position(), questionId)) {
      throw new BusinessConflictException("Question position is already occupied");
    }

    EditVersion.require(request.version(), existing.getVersion());
    // Option-only edits also change the version; the course lock is already held.
    entityManager.lock(existing, jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    deleteAnswerOptions(questionId);

    // Bulk JPQL bypasses the persistence context.
    entityManager.clear();

    Question question = findQuestion(quizId, questionId);

    question.update(request.text().trim(), request.type(), request.position());

    for (CreateQuestionRequest.OptionRequest option : request.options()) {

      question.addAnswerOption(option.text().trim(), option.correct());
    }

    entityManager.persist(question);
    entityManager.flush();

    return QuestionResponse.forTeacher(question);
  }

  @Transactional
  public void deleteQuestion(Long quizId, Long questionId, Long currentUserId, boolean isAdmin) {
    quizService.requireEditableQuiz(quizId, currentUserId, isAdmin);

    findQuestion(quizId, questionId);

    deleteAnswerOptions(questionId);

    entityManager
        .createQuery(
            """
                        DELETE FROM Question q
                        WHERE q.id = :questionId
                          AND q.quiz.id = :quizId
                        """)
        .setParameter("questionId", questionId)
        .setParameter("quizId", quizId)
        .executeUpdate();

    entityManager.clear();
  }

  private Question findQuestion(Long quizId, Long questionId) {
    return questionRepository
        .findByIdAndQuiz_Id(questionId, quizId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Question not found in Quiz: " + questionId));
  }

  private void deleteAnswerOptions(Long questionId) {
    entityManager
        .createQuery(
            """
                        DELETE FROM AnswerOption a
                        WHERE a.question.id = :questionId
                        """)
        .setParameter("questionId", questionId)
        .executeUpdate();
  }
}

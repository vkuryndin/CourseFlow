package org.courseflow.assessment.service;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.courseflow.assessment.dto.CreateQuizRequest;
import org.courseflow.assessment.dto.QuizResponse;
import org.courseflow.assessment.dto.UpdateQuizRequest;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.entity.Module;
import org.courseflow.content.service.ContentAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

  private final QuizRepository quizRepository;
  private final ContentStructure contentStructure;
  private final CourseGuard courseGuard;
  private final ContentAccessService contentAccessService;
  private final QuizSubmissionRepository quizSubmissionRepository;
  private final EntityManager entityManager;

  public QuizService(
      QuizRepository quizRepository,
      ContentStructure contentStructure,
      CourseGuard courseGuard,
      ContentAccessService contentAccessService,
      QuizSubmissionRepository quizSubmissionRepository,
      EntityManager entityManager) {
    this.quizRepository = quizRepository;
    this.contentStructure = contentStructure;
    this.courseGuard = courseGuard;
    this.contentAccessService = contentAccessService;
    this.quizSubmissionRepository = quizSubmissionRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public QuizResponse create(
      Long moduleId, CreateQuizRequest request, Long currentUserId, boolean isAdmin) {
    Module module = contentStructure.requireModule(moduleId);

    Long courseId = module.getCourse().getId();

    Course course = courseGuard.lock(courseId);

    CourseAccessPolicy.requireOwnerOrAdmin(
        course, currentUserId, isAdmin, "Only course teacher or ADMIN can create Quiz");

    CourseAccessPolicy.requireActive(course, "Cannot create Quiz in archived course");

    if (findQuizByModule(moduleId).isPresent()) {
      throw new BusinessConflictException("Module already has a Quiz");
    }

    Quiz quiz = new Quiz(module, request.title().trim(), request.timeLimit());

    Quiz saved = quizRepository.saveAndFlush(quiz);

    return QuizResponse.from(saved);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public QuizResponse getByModule(
      Long moduleId, Long currentUserId, Authentication authentication) {
    Module module = contentStructure.requireModule(moduleId);

    contentAccessService.checkCourseAccess(
        module.getCourse().getId(), currentUserId, authentication);

    Quiz quiz =
        findQuizByModule(moduleId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Quiz not found for module: " + moduleId));

    return QuizResponse.from(quiz);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public QuizResponse getById(Long quizId, Long currentUserId, Authentication authentication) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

    Long courseId = quiz.getModule().getCourse().getId();

    contentAccessService.checkCourseAccess(courseId, currentUserId, authentication);

    return QuizResponse.from(quiz);
  }

  private Optional<Quiz> findQuizByModule(Long moduleId) {
    return quizRepository.findByModule_Id(moduleId);
  }

  boolean hasQuizSubmissions(Long quizId) {
    return quizSubmissionRepository.existsByQuiz_Id(quizId);
  }

  Quiz requireQuizManagementAccess(
      Long quizId, Long currentUserId, boolean isAdmin, boolean forModification) {
    Long courseId =
        quizRepository
            .findCourseIdByQuizId(quizId)
            .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

    Course course = (forModification ? courseGuard.lock(courseId) : courseGuard.find(courseId));

    CourseAccessPolicy.requireOwnerOrAdmin(
        course, currentUserId, isAdmin, "Only course teacher or ADMIN can manage Quiz");

    if (forModification && course.getStatus() != CourseStatus.ACTIVE) {

      throw new BusinessConflictException("Cannot modify Quiz in archived course");
    }

    return quizRepository
        .findById(quizId)
        .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
  }

  @Transactional
  public QuizResponse updateQuiz(
      Long quizId, UpdateQuizRequest request, Long currentUserId, boolean isAdmin) {
    Quiz quiz = requireEditableQuiz(quizId, currentUserId, isAdmin);

    EditVersion.require(request.version(), quiz.getVersion());

    quiz.update(request.title().trim(), request.timeLimit(), request.maxAttempts());

    quizRepository.flush();
    return QuizResponse.from(quiz);
  }

  @Transactional
  public void deleteQuiz(Long quizId, Long currentUserId, boolean isAdmin) {
    requireEditableQuiz(quizId, currentUserId, isAdmin);

    // Delete answer options first.
    entityManager
        .createQuery(
            """
                        DELETE FROM AnswerOption a
                        WHERE a.question.id IN (
                            SELECT q.id
                            FROM Question q
                            WHERE q.quiz.id = :quizId
                        )
                        """)
        .setParameter("quizId", quizId)
        .executeUpdate();

    // Delete questions.
    entityManager
        .createQuery(
            """
                        DELETE FROM Question q
                        WHERE q.quiz.id = :quizId
                        """)
        .setParameter("quizId", quizId)
        .executeUpdate();

    // Delete Quiz.
    entityManager
        .createQuery(
            """
                        DELETE FROM Quiz q
                        WHERE q.id = :quizId
                        """)
        .setParameter("quizId", quizId)
        .executeUpdate();

    entityManager.clear();
  }

  Quiz requireEditableQuiz(Long quizId, Long currentUserId, boolean isAdmin) {
    Quiz quiz = requireQuizManagementAccess(quizId, currentUserId, isAdmin, true);

    if (hasQuizSubmissions(quizId)) {
      throw new BusinessConflictException("Cannot modify or delete Quiz after student attempts");
    }

    return quiz;
  }
}

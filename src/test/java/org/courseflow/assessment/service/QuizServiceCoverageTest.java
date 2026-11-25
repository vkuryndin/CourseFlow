package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.quiz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.service.ContentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class QuizServiceCoverageTest {
  private final QuizRepository repository = mock(QuizRepository.class);
  private final QuizSubmissionRepository attempts = mock(QuizSubmissionRepository.class);
  private final ContentStructure content = mock(ContentStructure.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final ContentAccessService access = mock(ContentAccessService.class);
  private final EntityManager em = mock(EntityManager.class);
  private final QuizService service =
      new QuizService(repository, content, guard, access, attempts, em);
  private final Quiz quiz = quiz();
  private final Authentication authentication = mock(Authentication.class);

  private void setup() {
    when(content.requireModule(50L)).thenReturn(quiz.getModule());
    when(repository.findCourseIdByQuizId(80L)).thenReturn(Optional.of(40L));
    when(guard.lock(40L)).thenReturn(quiz.getModule().getCourse());
    when(guard.find(40L)).thenReturn(quiz.getModule().getCourse());
    when(repository.findById(80L)).thenReturn(Optional.of(quiz));
  }

  @Test
  void getQuizById() {
    setup();
    assertEquals(80L, service.getById(80L, 10L, authentication).id());
    verify(access).checkCourseAccess(40L, 10L, authentication);
  }

  @Test
  void getQuizByModule() {
    setup();
    when(repository.findByModule_Id(50L)).thenReturn(Optional.of(quiz));
    assertEquals(80L, service.getByModule(50L, 10L, authentication).id());
  }

  @Test
  void missingQuizByModule() {
    setup();
    assertThrows(
        ResourceNotFoundException.class, () -> service.getByModule(50L, 10L, authentication));
  }

  @Test
  void missingQuizById() {
    assertThrows(ResourceNotFoundException.class, () -> service.getById(80L, 10L, authentication));
  }

  @Test
  void managementReadDoesNotLockCourse() {
    setup();
    assertSame(quiz, service.requireQuizManagementAccess(80L, 20L, false, false));
    verify(guard).find(40L);
    verify(guard, never()).lock(40L);
  }

  @Test
  void archivedQuizCannotBeModified() {
    setup();
    quiz.getModule().getCourse().archive();
    assertThrows(BusinessConflictException.class, () -> service.deleteQuiz(80L, 20L, false));
    verifyNoInteractions(em);
  }

  @Test
  void missingQuizCourseRejected() {
    assertThrows(ResourceNotFoundException.class, () -> service.deleteQuiz(80L, 20L, false));
  }

  @Test
  void deleteOrdersBulkOperations() {
    setup();
    Query options = mock(Query.class);
    Query questions = mock(Query.class);
    Query quizzes = mock(Query.class);
    when(em.createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM AnswerOption")))
        .thenReturn(options);
    when(em.createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM Question")))
        .thenReturn(questions);
    when(em.createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM Quiz")))
        .thenReturn(quizzes);
    when(options.setParameter("quizId", 80L)).thenReturn(options);
    when(questions.setParameter("quizId", 80L)).thenReturn(questions);
    when(quizzes.setParameter("quizId", 80L)).thenReturn(quizzes);
    service.deleteQuiz(80L, 20L, false);
    var order = inOrder(em, options, questions, quizzes);
    order.verify(em).createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM AnswerOption"));
    order.verify(options).executeUpdate();
    order.verify(em).createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM Question"));
    order.verify(questions).executeUpdate();
    order.verify(em).createQuery(org.mockito.ArgumentMatchers.contains("DELETE FROM Quiz"));
    order.verify(quizzes).executeUpdate();
    order.verify(em).clear();
  }
}

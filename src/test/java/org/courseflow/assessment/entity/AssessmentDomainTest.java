package org.courseflow.assessment.entity;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.module;
import static org.courseflow.testing.Fixtures.quiz;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssessmentDomainTest {

  @Test
  void quizDefinitionAndQuestionAssociation() {
    Quiz quiz = quiz();

    assertEquals(2, quiz.getMaxAttempts());
    quiz.update("New", 15, 3);
    assertEquals("New", quiz.getTitle());
    assertEquals(15, quiz.getTimeLimit());
    assertEquals(3, quiz.getMaxAttempts());
    Question q = quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1);
    assertSame(quiz, q.getQuiz());
    assertEquals(List.of(q), quiz.getQuestions());
    q.update("New question", QuestionType.MULTIPLE_CHOICE, 2);
    assertEquals("New question", q.getText());
    assertEquals(2, q.getPosition());
    AnswerOption option = q.addAnswerOption("Answer", true);
    assertSame(q, option.getQuestion());
    assertEquals(List.of(option), q.getAnswerOptions());
    option.update("Wrong", false);
    assertEquals("Wrong", option.getText());
    assertFalse(option.isCorrect());
  }

  @Test
  void quizRejectsInvalidTimeLimitAndAttempts() {
    Quiz quiz = quiz();

    assertThrows(IllegalArgumentException.class, () -> new Quiz(module(), "Q", null));
    assertThrows(IllegalArgumentException.class, () -> new Quiz(module(), "Q", 0));
    assertThrows(IllegalArgumentException.class, () -> quiz.update("Q", 0, 1));
    assertThrows(IllegalArgumentException.class, () -> quiz.update("Q", 1, null));
    assertThrows(IllegalArgumentException.class, () -> quiz.update("Q", 1, 0));
    assertEquals(10, quiz.getTimeLimit());
  }

  @Test
  void attemptValidUntilButNotAtExpiry() {
    Quiz quiz = quiz();
    var student = student();
    QuizSubmission a = new QuizSubmission(quiz, student, 1, NOW);

    assertSame(quiz, a.getQuiz());
    assertSame(student, a.getStudent());
    assertEquals(NOW, a.getTakenAt());
    assertEquals(NOW.plusSeconds(600), a.getExpiresAt());
    assertTrue(a.isActive(a.getExpiresAt().minusNanos(1)));
    assertFalse(a.isActive(a.getExpiresAt()));
    a.expire(NOW);
    assertEquals(QuizSubmissionStatus.IN_PROGRESS, a.getStatus());
    a.expire(a.getExpiresAt().plusSeconds(5));
    assertEquals(QuizSubmissionStatus.EXPIRED, a.getStatus());
    assertEquals(a.getExpiresAt(), a.getFinishedAt());
    assertNull(a.getScore());
  }

  @Test
  void completedAttemptKeepsSelectionsAndCannotBeCompletedAgain() {
    Quiz quiz = quiz();
    QuizSubmission a = new QuizSubmission(quiz, student(), 1, NOW);
    Question q = quiz.addQuestion("Q", QuestionType.SINGLE_CHOICE, 1);
    AnswerOption o = q.addAnswerOption("A", true);
    QuizAnswerSelection selection = a.addSelection(q, o);

    assertSame(a, selection.getQuizSubmission());
    assertSame(q, selection.getQuestion());
    assertSame(o, selection.getAnswerOption());
    assertEquals(List.of(selection), a.getSelections());
    a.complete(100, NOW.plusSeconds(1));
    assertEquals(QuizSubmissionStatus.SUBMITTED, a.getStatus());
    assertEquals(100, a.getScore());
    assertEquals(NOW.plusSeconds(1), a.getFinishedAt());
    assertThrows(IllegalStateException.class, () -> a.complete(0, NOW.plusSeconds(2)));
    assertThrows(IllegalStateException.class, () -> a.addSelection(q, o));
    a.expire(a.getExpiresAt());
    assertEquals(QuizSubmissionStatus.SUBMITTED, a.getStatus());
  }

  @Test
  void invalidAttemptNumberAndScoreRejected() {
    for (Integer n : Arrays.asList(null, 0)) {
      assertThrows(
          IllegalArgumentException.class, () -> new QuizSubmission(quiz(), student(), n, NOW));
    }
    QuizSubmission a = new QuizSubmission(quiz(), student(), 1, NOW);
    for (Integer score : Arrays.asList(null, -1, 101)) {
      assertThrows(IllegalArgumentException.class, () -> a.complete(score, NOW));
    }
    assertThrows(IllegalStateException.class, () -> a.complete(10, a.getExpiresAt()));
    assertEquals(QuizSubmissionStatus.IN_PROGRESS, a.getStatus());
  }
}

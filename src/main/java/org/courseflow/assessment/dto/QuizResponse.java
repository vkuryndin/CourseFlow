package org.courseflow.assessment.dto;

import org.courseflow.assessment.entity.Quiz;

public record QuizResponse(
    Long id, long version, Long moduleId, String title, Integer timeLimit, Integer maxAttempts) {
  public static QuizResponse from(Quiz quiz) {
    return new QuizResponse(
        quiz.getId(),
        quiz.getVersion(),
        quiz.getModule().getId(),
        quiz.getTitle(),
        quiz.getTimeLimit(),
        quiz.getMaxAttempts());
  }
}

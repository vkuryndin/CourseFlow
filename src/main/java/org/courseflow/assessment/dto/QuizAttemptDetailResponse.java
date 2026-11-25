package org.courseflow.assessment.dto;

import java.util.List;

public record QuizAttemptDetailResponse(
    QuizAttemptResponse attempt, List<SelectedAnswer> selectedAnswers) {

  public record SelectedAnswer(
      Long questionId, String questionText, Long optionId, String optionText) {}
}

package org.courseflow.assessment.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record QuizAttemptDetailResponse(
    QuizAttemptResponse attempt, List<SelectedAnswer> selectedAnswers) {
  public QuizAttemptDetailResponse {
    selectedAnswers =
        selectedAnswers == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(selectedAnswers));
  }

  @Override
  public List<SelectedAnswer> selectedAnswers() {
    return selectedAnswers == null ? null : Collections.unmodifiableList(selectedAnswers);
  }

  public record SelectedAnswer(
      Long questionId, String questionText, Long optionId, String optionText) {}
}

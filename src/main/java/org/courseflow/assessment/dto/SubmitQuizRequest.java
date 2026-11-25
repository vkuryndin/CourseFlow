package org.courseflow.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SubmitQuizRequest(
    @NotNull @Size(max = 200) List<@NotNull @Valid AnswerRequest> answers) {

  public record AnswerRequest(
      @NotNull @Positive Long questionId,
      @NotNull @Size(max = 10) List<@NotNull @Positive Long> optionIds) {}
}

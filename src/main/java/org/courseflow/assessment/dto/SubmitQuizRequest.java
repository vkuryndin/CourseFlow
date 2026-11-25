package org.courseflow.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SubmitQuizRequest(
    @NotNull @Size(max = 200) List<@NotNull @Valid AnswerRequest> answers) {
  public SubmitQuizRequest {
    answers = answers == null ? null : Collections.unmodifiableList(new ArrayList<>(answers));
  }

  @Override
  public List<AnswerRequest> answers() {
    return answers == null ? null : Collections.unmodifiableList(answers);
  }

  public record AnswerRequest(
      @NotNull @Positive Long questionId,
      @NotNull @Size(max = 10) List<@NotNull @Positive Long> optionIds) {
    public AnswerRequest {
      optionIds =
          optionIds == null ? null : Collections.unmodifiableList(new ArrayList<>(optionIds));
    }

    @Override
    public List<Long> optionIds() {
      return optionIds == null ? null : Collections.unmodifiableList(optionIds);
    }
  }
}

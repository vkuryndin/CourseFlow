package org.courseflow.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.courseflow.assessment.entity.QuestionType;

public record CreateQuestionRequest(
    @NotBlank @Size(max = 5000) String text,
    @NotNull QuestionType type,
    @NotNull @Positive Integer position,
    @NotNull @Size(min = 2, max = 10) List<@NotNull @Valid OptionRequest> options,
    @io.swagger.v3.oas.annotations.media.Schema(description = "Required on PUT; omit on POST")
        @jakarta.validation.constraints.PositiveOrZero
        Long version) {
  public CreateQuestionRequest {
    options = options == null ? null : Collections.unmodifiableList(new ArrayList<>(options));
  }

  @Override
  public List<OptionRequest> options() {
    return options == null ? null : Collections.unmodifiableList(options);
  }

  public record OptionRequest(@NotBlank @Size(max = 2000) String text, @NotNull Boolean correct) {}
}

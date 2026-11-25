package org.courseflow.assessment.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.courseflow.assessment.entity.AnswerOption;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;

public record QuestionResponse(
    Long id, String text, QuestionType type, Integer position, List<OptionResponse> options) {
  public QuestionResponse {
    options = options == null ? null : Collections.unmodifiableList(new ArrayList<>(options));
  }

  @Override
  public List<OptionResponse> options() {
    return options == null ? null : Collections.unmodifiableList(options);
  }

  public record OptionResponse(Long id, String text) {}

  public record ManagementOptionResponse(Long id, String text, boolean correct) {}

  public record ManagementResponse(
      Long id,
      long version,
      String text,
      QuestionType type,
      Integer position,
      List<ManagementOptionResponse> options) {
    public ManagementResponse {
      options = options == null ? null : Collections.unmodifiableList(new ArrayList<>(options));
    }

    @Override
    public List<ManagementOptionResponse> options() {
      return options == null ? null : Collections.unmodifiableList(options);
    }
  }

  public static QuestionResponse forStudent(Question question) {
    return new QuestionResponse(
        question.getId(),
        question.getText(),
        question.getType(),
        question.getPosition(),
        question.getAnswerOptions().stream()
            .sorted(Comparator.comparing(AnswerOption::getId))
            .map(option -> new OptionResponse(option.getId(), option.getText()))
            .toList());
  }

  public static ManagementResponse forTeacher(Question question) {
    return new ManagementResponse(
        question.getId(),
        question.getVersion(),
        question.getText(),
        question.getType(),
        question.getPosition(),
        question.getAnswerOptions().stream()
            .sorted(Comparator.comparing(AnswerOption::getId))
            .map(
                option ->
                    new ManagementOptionResponse(
                        option.getId(), option.getText(), option.isCorrect()))
            .toList());
  }
}

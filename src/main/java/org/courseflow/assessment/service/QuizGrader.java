package org.courseflow.assessment.service;

import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.entity.AnswerOption;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.common.exception.BadRequestException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure answer validation and scoring; no database or transaction dependency. */
final class QuizGrader {
    private QuizGrader() {}

    static Map<Long, Set<Long>> validateAnswers(
            List<Question> questions,
            SubmitQuizRequest request
    ) {
        Map<Long, Question> questionsById =
                new HashMap<>();

        for (Question question : questions) {
            questionsById.put(
                    question.getId(),
                    question
            );
        }

        Map<Long, Set<Long>> selectedAnswers =
                new HashMap<>();

        for (SubmitQuizRequest.AnswerRequest answer
                : request.answers()) {

            Long questionId = answer.questionId();

            Question question =
                    questionsById.get(questionId);

            if (question == null) {
                throw new BadRequestException(
                        "Question does not belong to Quiz: "
                                + questionId
                );
            }

            if (selectedAnswers.containsKey(questionId)) {
                throw new BadRequestException(
                        "Duplicate question: " + questionId
                );
            }

            Set<Long> selected =
                    new HashSet<>(answer.optionIds());

            if (selected.size() != answer.optionIds().size()) {
                throw new BadRequestException(
                        "Duplicate answer options are not allowed"
                );
            }

            if (question.getType()
                    == QuestionType.SINGLE_CHOICE
                    && selected.size() > 1) {

                throw new BadRequestException(
                        "SINGLE_CHOICE accepts only one option"
                );
            }

            Set<Long> allowedOptions = new HashSet<>();

            for (AnswerOption option
                    : question.getAnswerOptions()) {

                allowedOptions.add(option.getId());
            }

            if (!allowedOptions.containsAll(selected)) {
                throw new BadRequestException(
                        "Answer option does not belong to question: "
                                + questionId
                );
            }

            selectedAnswers.put(
                    questionId,
                    selected
            );
        }

        return selectedAnswers;
    }

    static int score(List<Question> questions, Map<Long, Set<Long>> selectedAnswers) {
        int correctQuestions = 0;

        for (Question question : questions) {

            Set<Long> selected = selectedAnswers.getOrDefault(
                    question.getId(),
                    Set.of()
            );

            Set<Long> correct = new HashSet<>();

            for (AnswerOption option
                    : question.getAnswerOptions()) {

                if (option.isCorrect()) {
                    correct.add(option.getId());
                }
            }

            if (selected.equals(correct)) {
                correctQuestions++;
            }
        }

        return (int) Math.round(
                correctQuestions * 100.0 / questions.size()
        );
    }
}

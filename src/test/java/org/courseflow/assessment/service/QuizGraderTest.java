package org.courseflow.assessment.service;

import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class QuizGraderTest {

    private Question question(long id, QuestionType type) {
        Question q = id(quiz().addQuestion("Question", type, 1), id);
        id(q.addAnswerOption("Correct", true), id * 10);
        id(q.addAnswerOption("Wrong", false), id * 10 + 1);
        return q;
    }

    private SubmitQuizRequest request(Long q, Long... options) {
        return new SubmitQuizRequest(List.of(new SubmitQuizRequest.AnswerRequest(q, List.of(options))));
    }

    @Test
    void validatesAnswerAndScoresCorrect() {
        Question q = question(1, QuestionType.SINGLE_CHOICE);
        var answers = QuizGrader.validateAnswers(List.of(q), request(1L, 10L));

        assertEquals(Map.of(1L, Set.of(10L)), answers);
        assertEquals(100, QuizGrader.score(List.of(q), answers));
    }

    @Test
    void rejectsForeignQuestion() {
        assertThrows(BadRequestException.class,
                () -> QuizGrader.validateAnswers(List.of(question(1, QuestionType.SINGLE_CHOICE)), request(2L, 20L)));
    }

    @Test
    void rejectsDuplicateQuestion() {
        Question q = question(1, QuestionType.SINGLE_CHOICE);
        var answer = new SubmitQuizRequest.AnswerRequest(1L, List.of(10L));

        assertThrows(BadRequestException.class,
                () -> QuizGrader.validateAnswers(List.of(q), new SubmitQuizRequest(List.of(answer, answer))));
    }

    @Test
    void rejectsDuplicateOptions() {
        assertThrows(BadRequestException.class,
                () -> QuizGrader.validateAnswers(List.of(question(1, QuestionType.MULTIPLE_CHOICE)),
                request(1L, 10L, 10L)));
    }

    @Test
    void singleChoiceRejectsMultipleOptions() {
        assertThrows(BadRequestException.class,
                () -> QuizGrader.validateAnswers(List.of(question(1, QuestionType.SINGLE_CHOICE)),
                request(1L, 10L, 11L)));
    }

    @Test
    void rejectsOptionFromOtherQuestion() {
        assertThrows(BadRequestException.class,
                () -> QuizGrader.validateAnswers(List.of(question(1, QuestionType.MULTIPLE_CHOICE)), request(1L, 20L)));
    }

    @Test
    void multipleChoiceRequiresExactSet() {
        Question q = question(1, QuestionType.MULTIPLE_CHOICE);
        id(q.addAnswerOption("Also correct", true), 12);

        assertEquals(100, QuizGrader.score(List.of(q), Map.of(1L, Set.of(10L, 12L))));
        assertEquals(0, QuizGrader.score(List.of(q), Map.of(1L, Set.of(10L))));
        assertEquals(0, QuizGrader.score(List.of(q), Map.of(1L, Set.of(10L, 11L, 12L))));
    }

    @Test
    void omittedAnswersAndFloorRounding() {
        var qs = List.of(question(1, QuestionType.SINGLE_CHOICE), question(2, QuestionType.SINGLE_CHOICE),
                question(3, QuestionType.SINGLE_CHOICE));

        assertEquals(66, QuizGrader.score(qs, Map.of(1L, Set.of(10L), 2L, Set.of(20L))));
        assertEquals(0, QuizGrader.score(qs, Map.of()));
        assertEquals(0, QuizGrader.percentage(0, 0));
        assertEquals(33, QuizGrader.percentage(1, 3));
    }
}

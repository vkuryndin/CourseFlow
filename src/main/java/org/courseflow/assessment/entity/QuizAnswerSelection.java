package org.courseflow.assessment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "quiz_answer_selections",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_quiz_answer_selection",
            columnNames = {"quiz_submission_id", "question_id", "answer_option_id"}))
public class QuizAnswerSelection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_submission_id", nullable = false)
  private QuizSubmission quizSubmission;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "answer_option_id", nullable = false)
  private AnswerOption answerOption;

  protected QuizAnswerSelection() {}

  public QuizAnswerSelection(
      QuizSubmission quizSubmission, Question question, AnswerOption answerOption) {
    this.quizSubmission = quizSubmission;
    this.question = question;
    this.answerOption = answerOption;
  }

  public Long getId() {
    return id;
  }

  public QuizSubmission getQuizSubmission() {
    return quizSubmission;
  }

  public Question getQuestion() {
    return question;
  }

  public AnswerOption getAnswerOption() {
    return answerOption;
  }
}

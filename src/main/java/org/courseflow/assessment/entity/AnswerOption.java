package org.courseflow.assessment.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "answer_options")
public class AnswerOption {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(nullable = false, columnDefinition = "text")
  private String text;

  @Column(name = "is_correct", nullable = false)
  private boolean correct;

  protected AnswerOption() {}

  public AnswerOption(Question question, String text, boolean correct) {
    this.question = question;
    this.text = text;
    this.correct = correct;
  }

  public Long getId() {
    return id;
  }

  public Question getQuestion() {
    return question;
  }

  public String getText() {
    return text;
  }

  public boolean isCorrect() {
    return correct;
  }

  public void update(String text, boolean correct) {
    this.text = text;
    this.correct = correct;
  }
}

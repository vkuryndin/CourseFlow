package org.courseflow.assessment.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false, columnDefinition = "bigint default 0")
  private long version;

  public long getVersion() {
    return version;
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(nullable = false, columnDefinition = "text")
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private QuestionType type;

  @Column(nullable = false)
  private Integer position;

  @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  @OrderBy("id ASC")
  private List<AnswerOption> answerOptions = new ArrayList<>();

  protected Question() {}

  public Question(Quiz quiz, String text, QuestionType type, Integer position) {
    this.quiz = quiz;
    this.text = text;
    this.type = type;
    this.position = position;
  }

  public Long getId() {
    return id;
  }

  public Quiz getQuiz() {
    return quiz;
  }

  public String getText() {
    return text;
  }

  public QuestionType getType() {
    return type;
  }

  public Integer getPosition() {
    return position;
  }

  public List<AnswerOption> getAnswerOptions() {
    return List.copyOf(answerOptions);
  }

  public void update(String text, QuestionType type, Integer position) {
    this.text = text;
    this.type = type;
    this.position = position;
  }

  public AnswerOption addAnswerOption(String text, boolean correct) {
    AnswerOption option = new AnswerOption(this, text, correct);

    answerOptions.add(option);

    return option;
  }
}

package org.courseflow.assessment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.courseflow.identity.entity.User;

@Entity
@Table(
    name = "quiz_submissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_quiz_submissions_attempt",
            columnNames = {"quiz_id", "student_id", "attempt_number"}))
public class QuizSubmission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  private User student;

  @Column(name = "attempt_number", nullable = false)
  private Integer attemptNumber;

  @Column private Integer score;

  @Column(name = "taken_at", nullable = false, updatable = false)
  private Instant takenAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QuizSubmissionStatus status = QuizSubmissionStatus.IN_PROGRESS;

  @OneToMany(mappedBy = "quizSubmission", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  private List<QuizAnswerSelection> selections = new ArrayList<>();

  protected QuizSubmission() {}

  public QuizSubmission(Quiz quiz, User student, Integer attemptNumber, Instant takenAt) {
    if (attemptNumber == null || attemptNumber < 1) {
      throw new IllegalArgumentException("Attempt number must be at least 1");
    }

    this.quiz = quiz;
    this.student = student;
    this.attemptNumber = attemptNumber;
    this.takenAt = takenAt;
    this.expiresAt = takenAt.plus(quiz.getTimeLimit(), ChronoUnit.MINUTES);
  }

  public Long getId() {
    return id;
  }

  public Quiz getQuiz() {
    return quiz;
  }

  public User getStudent() {
    return student;
  }

  public Integer getAttemptNumber() {
    return attemptNumber;
  }

  public Integer getScore() {
    return score;
  }

  public Instant getTakenAt() {
    return takenAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public QuizSubmissionStatus getStatus() {
    return status;
  }

  public List<QuizAnswerSelection> getSelections() {
    return List.copyOf(selections);
  }

  public boolean isActive(Instant now) {
    return status == QuizSubmissionStatus.IN_PROGRESS && now.isBefore(expiresAt);
  }

  public QuizAnswerSelection addSelection(Question question, AnswerOption answerOption) {
    if (status != QuizSubmissionStatus.IN_PROGRESS) {
      throw new IllegalStateException("Quiz attempt is already finished");
    }

    QuizAnswerSelection selection = new QuizAnswerSelection(this, question, answerOption);

    selections.add(selection);

    return selection;
  }

  public void complete(Integer score, Instant now) {
    if (!isActive(now)) {
      throw new IllegalStateException("Quiz attempt is not active");
    }

    if (score == null || score < 0 || score > 100) {
      throw new IllegalArgumentException("Score must be between 0 and 100");
    }

    this.score = score;
    this.finishedAt = now;
    this.status = QuizSubmissionStatus.SUBMITTED;
  }

  public void expire(Instant now) {
    if (status == QuizSubmissionStatus.IN_PROGRESS && !now.isBefore(expiresAt)) {

      this.finishedAt = expiresAt;
      this.status = QuizSubmissionStatus.EXPIRED;
    }
  }
}

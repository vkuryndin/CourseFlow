package org.courseflow.learning.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.courseflow.identity.entity.User;

@Entity
@Table(
    name = "submissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_submissions_assignment_student",
            columnNames = {"assignment_id", "student_id"}))
public class Submission {

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
  @JoinColumn(name = "assignment_id", nullable = false)
  private Assignment assignment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  private User student;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SubmissionStatus status = SubmissionStatus.SUBMITTED;

  @Column private Integer score;

  @Column(columnDefinition = "text")
  private String feedback;

  protected Submission() {}

  public Submission(Assignment assignment, User student, String content, Instant now) {
    this.submittedAt = now;
    this.assignment = assignment;
    this.student = student;
    this.content = content;
  }

  public Long getId() {
    return id;
  }

  public Assignment getAssignment() {
    return assignment;
  }

  public User getStudent() {
    return student;
  }

  public String getContent() {
    return content;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public SubmissionStatus getStatus() {
    return status;
  }

  public Integer getScore() {
    return score;
  }

  public String getFeedback() {
    return feedback;
  }

  public void grade(Integer score, String feedback) {
    if (score == null || score < 0 || score > assignment.getMaxScore()) {
      throw new IllegalArgumentException("Score must be between 0 and assignment max score");
    }

    this.score = score;
    this.feedback = feedback;
    this.status = SubmissionStatus.GRADED;
  }
}

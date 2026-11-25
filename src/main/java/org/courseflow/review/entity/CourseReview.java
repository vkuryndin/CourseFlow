package org.courseflow.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.courseflow.catalog.entity.Course;
import org.courseflow.identity.entity.User;
import org.hibernate.annotations.Check;

@Entity
@Table(
    name = "course_reviews",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_course_reviews_course_student",
            columnNames = {"course_id", "student_id"}))
@Check(name = "ck_course_reviews_rating", constraints = "rating between 1 and 5")
public class CourseReview {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  private User student;

  @Column(nullable = false)
  private Integer rating;

  @Column(length = 2000)
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CourseReview() {}

  public CourseReview(Course course, User student, Integer rating, String comment, Instant now) {
    this.createdAt = now;
    this.course = course;
    this.student = student;
    update(rating, comment);
  }

  public Long getId() {
    return id;
  }

  public Course getCourse() {
    return course;
  }

  public User getStudent() {
    return student;
  }

  public Integer getRating() {
    return rating;
  }

  public String getComment() {
    return comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void update(Integer rating, String comment) {
    if (rating == null || rating < 1 || rating > 5) {
      throw new IllegalArgumentException("Rating must be between 1 and 5");
    }

    this.rating = rating;
    this.comment = comment;
  }
}

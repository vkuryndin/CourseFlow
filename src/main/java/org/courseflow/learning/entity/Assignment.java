
package org.courseflow.learning.entity;

import jakarta.persistence.*;
import org.courseflow.content.entity.Lesson;
import org.courseflow.identity.entity.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;

    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY)
    private List<Submission> submissions = new ArrayList<>();

    protected Assignment() {
    }

    public Assignment(
            Lesson lesson,
            String title,
            String description,
            Instant dueAt,
            Integer maxScore
    ) {
        this.lesson = lesson;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.maxScore = maxScore;
    }

    public Long getId() {
        return id;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public List<Submission> getSubmissions() {
        return List.copyOf(submissions);
    }

    public void update(
            String title,
            String description,
            Instant dueAt,
            Integer maxScore
    ) {
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.maxScore = maxScore;
    }

    public Submission addSubmission(
            User student,
            String content
    ) {
        Submission submission = new Submission(
                this,
                student,
                content
        );

        submissions.add(submission);

        return submission;
    }

    public void updateDetails(
            String title,
            String description,
            Instant dueAt,
            Integer maxScore
    ) {
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.maxScore = maxScore;
    }
}

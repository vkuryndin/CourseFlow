
package org.courseflow.assessment.entity;

import jakarta.persistence.*;
import org.courseflow.content.entity.Module;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false, unique = true)
    private Module module;

    @Column(nullable = false, length = 200)
    private String title;

    // Time limit in minutes
    @Column(name = "time_limit", nullable = false)
    private Integer timeLimit;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 2;

    @OneToMany(
            mappedBy = "quiz",
            fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST
    )
    @OrderBy("position ASC, id ASC")
    private List<Question> questions = new ArrayList<>();

    protected Quiz() {
    }

    public Quiz(
            Module module,
            String title,
            Integer timeLimit
    ) {
        this.module = module;
        this.title = title;
        setTimeLimit(timeLimit);
    }

    public Long getId() {
        return id;
    }

    public Module getModule() {
        return module;
    }

    public String getTitle() {
        return title;
    }

    public Integer getTimeLimit() {
        return timeLimit;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public List<Question> getQuestions() {
        return List.copyOf(questions);
    }

    public void update(
            String title,
            Integer timeLimit,
            Integer maxAttempts
    ) {
        if (timeLimit == null || timeLimit < 1) {
            throw new IllegalArgumentException(
                    "Time limit must be at least 1 minute"
            );
        }

        if (maxAttempts == null || maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Max attempts must be at least 1"
            );
        }

        this.title = title;
        this.timeLimit = timeLimit;
        this.maxAttempts = maxAttempts;
    }

    private void setTimeLimit(Integer timeLimit) {
        if (timeLimit == null || timeLimit < 1) {
            throw new IllegalArgumentException(
                    "Time limit must be at least 1 minute"
            );
        }

        this.timeLimit = timeLimit;
    }

    public Question addQuestion(
            String text,
            QuestionType type,
            Integer position
    ) {
        Question question = new Question(
                this,
                text,
                type,
                position
        );

        questions.add(question);

        return question;
    }
}

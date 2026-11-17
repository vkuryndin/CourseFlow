
package org.courseflow.content.entity;

import jakarta.persistence.*;
import org.courseflow.learning.entity.Assignment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "video_url", length = 2048)
    private String videoUrl;

    @Column(nullable = false)
    private Integer position;

    @OneToMany(
            mappedBy = "lesson",
            fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST
    )
    @OrderBy("startsAt ASC, id ASC")
    private List<LessonSession> sessions = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<Assignment> assignments = new ArrayList<>();

    protected Lesson() {
    }

    public Lesson(
            Module module,
            String title,
            String content,
            String videoUrl,
            Integer position
    ) {
        this.module = module;
        this.title = title;
        this.content = content;
        this.videoUrl = videoUrl;
        this.position = position;
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

    public String getContent() {
        return content;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public Integer getPosition() {
        return position;
    }

    public List<LessonSession> getSessions() {
        return List.copyOf(sessions);
    }

    public List<Assignment> getAssignments() {
        return List.copyOf(assignments);
    }

    public void update(
            String title,
            String content,
            String videoUrl,
            Integer position
    ) {
        this.title = title;
        this.content = content;
        this.videoUrl = videoUrl;
        this.position = position;
    }

    public LessonSession addSession(
            Instant startsAt,
            Instant endsAt,
            LessonSessionFormat format,
            String meetingUrl
    ) {
        LessonSession session = new LessonSession(
                this,
                startsAt,
                endsAt,
                format,
                meetingUrl
        );

        sessions.add(session);

        return session;
    }

    public Assignment addAssignment(
            String title,
            String description,
            Instant dueAt,
            Integer maxScore
    ) {
        Assignment assignment = new Assignment(
                this,
                title,
                description,
                dueAt,
                maxScore
        );

        assignments.add(assignment);

        return assignment;
    }
}

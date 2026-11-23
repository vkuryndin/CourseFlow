
package org.courseflow.content.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lesson_sessions")
public class LessonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonSessionFormat format;

    @Column(name = "meeting_url", length = 2048)
    private String meetingUrl;

    protected LessonSession() {
    }

    public LessonSession(
            Lesson lesson,
            Instant startsAt,
            Instant endsAt,
            LessonSessionFormat format,
            String meetingUrl
    ) {
        this.lesson = lesson;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.format = format;
        this.meetingUrl = meetingUrl;
    }

    public Long getId() {
        return id;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public LessonSessionFormat getFormat() {
        return format;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public void update(
            Instant startsAt,
            Instant endsAt,
            LessonSessionFormat format,
            String meetingUrl
    ) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.format = format;
        this.meetingUrl = meetingUrl;
    }
    public void updateDetails(
            Instant startsAt,
            Instant endsAt,
            LessonSessionFormat format,
            String meetingUrl
    ) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.format = format;
        this.meetingUrl = meetingUrl;
    }

    public void changeTime(
            Instant startsAt,
            Instant endsAt
    ) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}

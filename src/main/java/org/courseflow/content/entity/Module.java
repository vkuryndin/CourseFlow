
package org.courseflow.content.entity;

import jakarta.persistence.*;
import org.courseflow.catalog.entity.Course;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modules")
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Integer position;

    @OneToMany(
            mappedBy = "module",
            fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST
    )
    @OrderBy("position ASC, id ASC")
    private List<Lesson> lessons = new ArrayList<>();

    protected Module() {
    }

    public Module(
            Course course,
            String title,
            String description,
            Integer position
    ) {
        this.course = course;
        this.title = title;
        this.description = description;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPosition() {
        return position;
    }

    public List<Lesson> getLessons() {
        return List.copyOf(lessons);
    }

    public void update(
            String title,
            String description,
            Integer position
    ) {
        this.title = title;
        this.description = description;
        this.position = position;
    }

    public Lesson addLesson(
            String title,
            String content,
            String videoUrl,
            Integer position
    ) {
        Lesson lesson = new Lesson(
                this,
                title,
                content,
                videoUrl,
                position
        );

        lessons.add(lesson);

        return lesson;
    }
}

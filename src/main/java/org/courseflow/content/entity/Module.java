package org.courseflow.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import org.courseflow.catalog.entity.Course;

@Entity
@Table(name = "modules")
public class Module {

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
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 2000)
  private String description;

  @Column(nullable = false)
  private Integer position;

  @OneToMany(mappedBy = "module", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  @OrderBy("position ASC, id ASC")
  private List<Lesson> lessons = new ArrayList<>();

  protected Module() {}

  public Module(Course course, String title, String description, Integer position) {
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

  public Lesson addLesson(String title, String content, String videoUrl, Integer position) {
    Lesson lesson = new Lesson(this, title, content, videoUrl, position);

    lessons.add(lesson);

    return lesson;
  }

  public void updateDetails(String title, String description) {
    this.title = title;
    this.description = description;
  }

  public void changePosition(Integer position) {
    this.position = position;
  }

  public void removeLesson(Lesson lesson) {
    this.lessons.remove(lesson);
  }
}

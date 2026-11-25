package org.courseflow.testing;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.entity.Course;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.Module;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.learning.entity.Assignment;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain domain objects: no persistence or Spring application context. */
public final class Fixtures {

  private Fixtures() {}

  public static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  public static <T> T id(T entity, long id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  public static User student() {
    return id(new User("Student", "student@example.test", Set.of(UserRole.STUDENT), NOW), 10);
  }

  public static User teacher() {
    return id(new User("Teacher", "teacher@example.test", Set.of(UserRole.TEACHER), NOW), 20);
  }

  public static Category category() {
    return id(new Category("Java", "Programming"), 30);
  }

  public static Course course() {
    return id(
        new Course("ORM", "Hibernate", category(), teacher(), 30, LocalDate.of(2026, 1, 1)), 40);
  }

  public static Module module() {
    return id(course().addModule("JPA", "Mapping", 1), 50);
  }

  public static Lesson lesson() {
    return id(module().addLesson("Entities", "Content", null, 1), 60);
  }

  public static Assignment assignment() {
    return id(new Assignment(lesson(), "Task", "Implement", NOW.plusSeconds(3600), 100), 70);
  }

  public static Quiz quiz() {
    return id(new Quiz(module(), "Quiz", 10), 80);
  }
}

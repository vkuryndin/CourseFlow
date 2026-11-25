package org.courseflow.demo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.courseflow.assessment.entity.QuestionType;
import tools.jackson.databind.json.JsonMapper;

/** Учебные материалы, отделённые от алгоритма инициализации. */
public final class DemoContent {
  private DemoContent() {}

  public record CourseData(
      String category,
      String title,
      String description,
      List<String> tags,
      List<ModuleData> modules) {}

  public record ModuleData(
      String title,
      String description,
      String assignment,
      String solution,
      List<LessonData> lessons,
      List<QuestionData> questions) {}

  public record LessonData(String title, String content) {}

  public record QuestionData(String text, QuestionType type, List<OptionData> options) {}

  public record OptionData(String text, boolean correct) {}

  public static List<CourseData> load() {
    JsonMapper mapper = JsonMapper.builder().build();
    List<CourseData> courses = new ArrayList<>();
    for (int number = 1; number <= 16; number++) {
      String path = String.format(Locale.ROOT, "/demo/course-%02d.json", number);
      try (InputStream stream = DemoContent.class.getResourceAsStream(path)) {
        if (stream == null) throw new IllegalStateException("Не найден ресурс " + path);
        CourseData course = mapper.readValue(stream, CourseData.class);
        validate(course);
        courses.add(course);
      } catch (IOException exception) {
        throw new IllegalStateException("Не удалось прочитать " + path, exception);
      }
    }
    return List.copyOf(courses);
  }

  private static void validate(CourseData course) {
    require(course.title(), 200);
    require(course.description(), 5000);
    require(course.category(), 100);
    if (course.tags().isEmpty() || course.modules().size() != 3) invalid();
    course.tags().forEach(tag -> require(tag, 50));
    for (ModuleData module : course.modules()) {
      require(module.title(), 200);
      require(module.description(), 2000);
      require(module.assignment(), 20000);
      require(module.solution(), 20000);
      if (module.lessons().size() != 3 || module.questions().size() != 10) invalid();
      for (LessonData lesson : module.lessons()) {
        require(lesson.title(), 200);
        require(lesson.content(), 20000);
      }
      for (QuestionData question : module.questions()) {
        require(question.text(), 5000);
        if (question.type() == null || question.options().size() != 4) invalid();
        long correct = question.options().stream().filter(OptionData::correct).count();
        if (correct != (question.type() == QuestionType.SINGLE_CHOICE ? 1 : 2)) invalid();
        if (question.options().stream().map(OptionData::text).distinct().count() != 4) invalid();
        question.options().forEach(option -> require(option.text(), 2000));
      }
    }
  }

  private static void require(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) invalid();
  }

  private static void invalid() {
    throw new IllegalStateException("Некорректные демонстрационные учебные материалы");
  }
}

package org.courseflow.catalog.dto;

import java.time.LocalDate;
import org.courseflow.catalog.entity.Course;

public record CourseResponse(
    Long id,
    long version,
    String title,
    String description,
    Long categoryId,
    Long teacherId,
    Integer duration,
    LocalDate startDate,
    String status) {

  public static CourseResponse from(Course course) {
    return new CourseResponse(
        course.getId(),
        course.getVersion(),
        course.getTitle(),
        course.getDescription(),
        course.getCategory().getId(),
        course.getTeacher().getId(),
        course.getDuration(),
        course.getStartDate(),
        course.getStatus().name());
  }
}

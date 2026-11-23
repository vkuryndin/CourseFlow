
package org.courseflow.catalog.dto;

import org.courseflow.catalog.entity.Course;

import java.time.LocalDate;

public record CourseResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        Long teacherId,
        Integer duration,
        LocalDate startDate,
        String status
) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCategory().getId(),
                course.getTeacher().getId(),
                course.getDuration(),
                course.getStartDate(),
                course.getStatus().name()
        );
    }
}

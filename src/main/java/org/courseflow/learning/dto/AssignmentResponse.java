
package org.courseflow.learning.dto;

import org.courseflow.learning.entity.Assignment;

import java.time.Instant;

public record AssignmentResponse(
        Long id,
        long version,
        Long lessonId,
        String title,
        String description,
        Instant dueAt,
        Integer maxScore
) {
    public static AssignmentResponse from(
            Assignment assignment
    ) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getVersion(),
                assignment.getLesson().getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getDueAt(),
                assignment.getMaxScore()
        );
    }
}

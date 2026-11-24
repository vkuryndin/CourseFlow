
package org.courseflow.learning.dto;

import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.entity.EnrollmentStatus;

import java.util.List;

public record GradebookResponse(
        Long courseId,
        List<Column> columns,
        PageResponse<StudentRow> students
) {
    public enum AssessmentType {
        ASSIGNMENT,
        QUIZ
    }

    public enum ReviewStatus {
        NOT_SUBMITTED,
        SUBMITTED,
        GRADED
    }

    public record Column(
            Long id,
            AssessmentType type,
            String title,
            Long moduleId,
            String moduleTitle,
            Integer maxScore
    ) {
    }

    public record StudentRow(
            Long studentId,
            String studentName,
            String studentStatus,
            EnrollmentStatus enrollmentStatus,
            List<Cell> results
    ) {
    }

    public record Cell(
            Long assessmentId,
            AssessmentType type,
            String status,
            Integer score,
            Long attemptsUsed
    ) {
    }
}

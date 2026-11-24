package org.courseflow.learning.dto;

import org.courseflow.learning.entity.SubmissionStatus;
import java.time.Instant;

public record MySubmissionResponse(
        Long submissionId, Long assignmentId, String assignmentTitle,
        Long courseId, String courseTitle, String content, Instant submittedAt,
        SubmissionStatus status, Integer score, Integer maxScore, String feedback
) {
}

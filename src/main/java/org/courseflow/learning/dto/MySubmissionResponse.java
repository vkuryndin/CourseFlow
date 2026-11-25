package org.courseflow.learning.dto;

import java.time.Instant;
import org.courseflow.learning.entity.SubmissionStatus;

public record MySubmissionResponse(
    Long submissionId,
    Long assignmentId,
    String assignmentTitle,
    Long courseId,
    String courseTitle,
    String content,
    Instant submittedAt,
    SubmissionStatus status,
    Integer score,
    Integer maxScore,
    String feedback) {}

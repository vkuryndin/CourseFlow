package org.courseflow.assessment.dto;

import java.time.Instant;

public record MyQuizAttemptResponse(
        Long attemptId, Long quizId, String quizTitle, Long courseId, String courseTitle,
        Integer attemptNumber, String status, Integer score,
        Instant takenAt, Instant expiresAt, Instant finishedAt
) {
}

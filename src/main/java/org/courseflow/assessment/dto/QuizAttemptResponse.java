
package org.courseflow.assessment.dto;

import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;

import java.time.Instant;

public record QuizAttemptResponse(
        Long id,
        Long quizId,
        Long studentId,
        Integer attemptNumber,
        QuizSubmissionStatus status,
        Integer score,
        Instant takenAt,
        Instant expiresAt,
        Instant finishedAt
) {
    public static QuizAttemptResponse at(QuizSubmission attempt, Instant now) {
        QuizAttemptResponse stored = from(attempt);
        if (stored.status() == QuizSubmissionStatus.IN_PROGRESS && !now.isBefore(stored.expiresAt())) {
            return new QuizAttemptResponse(stored.id(), stored.quizId(), stored.studentId(),
                    stored.attemptNumber(), QuizSubmissionStatus.EXPIRED, stored.score(),
                    stored.takenAt(), stored.expiresAt(), stored.expiresAt());
        }
        return stored;
    }

    public static QuizAttemptResponse from(
            QuizSubmission attempt
    ) {
        return new QuizAttemptResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getStudent().getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getTakenAt(),
                attempt.getExpiresAt(),
                attempt.getFinishedAt()
        );
    }
}

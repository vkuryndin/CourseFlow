package org.courseflow.learning.service;

import java.time.Instant;
import org.courseflow.learning.entity.SubmissionStatus;

/** Read-model status rules shared by progress and gradebook. */
final class AssessmentStatuses {
    private AssessmentStatuses() {}

    static String assignment(SubmissionStatus status, Instant dueAt, Instant now) {
        return status != null ? status.name() : (dueAt.isAfter(now) ? "NOT_SUBMITTED" : "OVERDUE");
    }

    static String quiz(Integer bestScore, long attemptsUsed, long activeAttempts) {
        if (bestScore != null) return "COMPLETED";
        if (attemptsUsed == 0) return "NOT_STARTED";
        return activeAttempts > 0 ? "IN_PROGRESS" : "EXPIRED";
    }

    // The same aggregation is used in both read models (alias s and parameter now).
    static final String ACTIVE_ATTEMPT_COUNT = """
            SUM(CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.IN_PROGRESS
                       AND s.expiresAt > :now THEN 1 ELSE 0 END)
            """;
}

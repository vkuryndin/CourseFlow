package org.courseflow.assessment.dto;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.quiz;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.junit.jupiter.api.Test;

class QuizAttemptResponseTest {

    @Test
    void readOnlyProjectionReflectsExpiryWithoutMutatingEntity() {
        var attempt = id(new QuizSubmission(quiz(), student(), 1, NOW), 90);
        var before = QuizAttemptResponse.at(attempt, attempt.getExpiresAt().minusNanos(1));

        assertEquals(QuizSubmissionStatus.IN_PROGRESS, before.status());
        var after = QuizAttemptResponse.at(attempt, attempt.getExpiresAt());
        assertEquals(QuizSubmissionStatus.EXPIRED, after.status());
        assertEquals(attempt.getExpiresAt(), after.finishedAt());
        assertEquals(QuizSubmissionStatus.IN_PROGRESS, attempt.getStatus());
        assertNull(attempt.getFinishedAt());
    }

    @Test
    void completedAttemptDoesNotBecomeExpired() {
        var attempt = id(new QuizSubmission(quiz(), student(), 1, NOW), 90);
        attempt.complete(80, NOW);

        var response = QuizAttemptResponse.at(attempt, attempt.getExpiresAt());

        assertEquals(QuizSubmissionStatus.SUBMITTED, response.status());
        assertEquals(80, response.score());
    }
}

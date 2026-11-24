
package org.courseflow.learning.dto;

import org.courseflow.learning.entity.Submission;
import org.courseflow.learning.entity.SubmissionStatus;

import java.time.Instant;

public record SubmissionResponse(
        Long id,
        long version,
        Long assignmentId,
        Long studentId,
        String content,
        Instant submittedAt,
        SubmissionStatus status,
        Integer score,
        String feedback
) {
    public static SubmissionResponse from(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getVersion(),
                submission.getAssignment().getId(),
                submission.getStudent().getId(),
                submission.getContent(),
                submission.getSubmittedAt(),
                submission.getStatus(),
                submission.getScore(),
                submission.getFeedback()
        );
    }
}

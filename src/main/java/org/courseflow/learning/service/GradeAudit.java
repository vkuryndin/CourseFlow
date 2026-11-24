package org.courseflow.learning.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Best-effort operational audit of committed grading operations (not a database history). */
@Component
public class GradeAudit {
    private static final Logger log = LoggerFactory.getLogger(GradeAudit.class);

    public void afterSuccessfulCommit(Long assignmentId, Long submissionId, Long graderId,
                                      Integer previousScore, int newScore, boolean feedbackChanged,
                                      boolean regrade, long version, Instant gradedAt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Grading audit requires an active transaction");
        }
        String requestId = MDC.get("requestId");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("event={} assignmentId={} submissionId={} graderId={} previousScore={} newScore={} "
                                + "feedbackChanged={} version={} gradedAt={} auditRequestId={}",
                        regrade ? "submission_regraded" : "submission_graded", assignmentId, submissionId,
                        graderId, previousScore, newScore, feedbackChanged, version, gradedAt, requestId);
            }
        });
    }
}

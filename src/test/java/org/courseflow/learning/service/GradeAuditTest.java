package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GradeAuditTest {

  @Test
  void auditRequiresActiveTransaction() {
    assertThrows(
        IllegalStateException.class,
        () -> new GradeAudit().afterSuccessfulCommit(1L, 2L, 3L, null, 80, true, false, 0, NOW));
  }

  @Test
  void committedGradeIsActuallyLoggedAndRollbackIsNot() {
    Logger logger = (Logger) LoggerFactory.getLogger(GradeAudit.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    String previousRequestId = org.slf4j.MDC.get("requestId");
    try {
      org.slf4j.MDC.put("requestId", "grade-audit-test");
      TransactionSynchronizationManager.setActualTransactionActive(true);
      TransactionSynchronizationManager.initSynchronization();
      new GradeAudit().afterSuccessfulCommit(1L, 2L, 3L, 70, 80, true, true, 2, NOW);
      var callback = TransactionSynchronizationManager.getSynchronizations().get(0);
      assertTrue(appender.list.isEmpty(), "Audit must not be emitted before commit");
      callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
      assertTrue(appender.list.isEmpty(), "Rolled back grade must not be logged");
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.initSynchronization();
      new GradeAudit().afterSuccessfulCommit(1L, 2L, 3L, 70, 80, true, true, 2, NOW);
      var committed = TransactionSynchronizationManager.getSynchronizations();
      assertEquals(1, committed.size());
      committed.get(0).afterCommit();
      committed.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
      assertEquals(1, appender.list.size());
      String message = appender.list.get(0).getFormattedMessage();
      assertEquals(
          "event=submission_regraded assignmentId=1 submissionId=2 graderId=3 previousScore=70 newScore=80 feedbackChanged=true version=2 gradedAt="
              + NOW
              + " auditRequestId=grade-audit-test",
          message);
    } finally {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.clearSynchronization();
      }
      TransactionSynchronizationManager.setActualTransactionActive(false);
      if (previousRequestId == null) {
        org.slf4j.MDC.remove("requestId");
      } else {
        org.slf4j.MDC.put("requestId", previousRequestId);
      }
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void registersCallbackOnlyAndCanBeInvokedAfterCommit() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
    try {
      new GradeAudit().afterSuccessfulCommit(1L, 2L, 3L, null, 80, true, false, 0, NOW);
      var synchronizations = TransactionSynchronizationManager.getSynchronizations();
      assertEquals(1, synchronizations.size());
      assertDoesNotThrow(() -> synchronizations.get(0).afterCommit());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }
}

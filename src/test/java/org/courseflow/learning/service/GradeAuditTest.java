package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GradeAuditTest {

    @Test
    void auditRequiresActiveTransaction() {
        assertThrows(IllegalStateException.class,
                () -> new GradeAudit().afterSuccessfulCommit(1L, 2L, 3L, null, 80, true, false, 0, NOW));
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

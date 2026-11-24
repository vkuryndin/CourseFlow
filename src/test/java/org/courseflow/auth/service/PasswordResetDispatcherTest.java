package org.courseflow.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.courseflow.common.exception.RateLimitExceededException;
import org.courseflow.common.system.CourseFlowMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class PasswordResetDispatcherTest {
    private final PasswordResetService resets = mock(PasswordResetService.class);
    private final Executor executor = mock(Executor.class);
    private final CourseFlowMetrics metrics = mock(CourseFlowMetrics.class);
    private final PasswordResetDispatcher dispatcher = new PasswordResetDispatcher(resets, executor, metrics);

    @Test
    void dispatchesWithoutSynchronousAccountLookupAndRestoresMdc() {
        try {
            MDC.put("requestId", "request");
            dispatcher.enqueue("student@example.test");
            verifyNoInteractions(resets);
            verify(metrics).resetAccepted();
            var task = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(task.capture());
            MDC.put("requestId", "worker");
            doAnswer((i) -> {
                assertEquals("request", MDC.get("requestId"));
                return null;
            }).when(resets).requestReset("student@example.test");
            task.getValue().run();
            verify(resets).requestReset("student@example.test");
            assertEquals("worker", MDC.get("requestId"));
        } finally {
            MDC.remove("requestId");
        }
    }

    @Test
    void queueRejectionReturnsRetryAfterAndDoesNotInvokeService() {
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));

        assertEquals(60, assertThrows(RateLimitExceededException.class,
                () -> dispatcher.enqueue("student@example.test")).getRetryAfterSeconds());
        verify(metrics).resetRejected();
        verifyNoInteractions(resets);
    }

    @Test
    void workerFailureRecordedWithoutEscapingRunnable() {
        dispatcher.enqueue("student@example.test");
        var task = ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(task.capture());
        doThrow(new IllegalStateException("failure")).when(resets).requestReset(anyString());
        assertDoesNotThrow(task.getValue()::run);
        verify(metrics).resetProcessingFailed();
    }
}

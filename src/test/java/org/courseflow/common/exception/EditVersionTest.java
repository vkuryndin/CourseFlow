package org.courseflow.common.exception;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class EditVersionTest {

    @Test
    void acceptsMatchingVersion() {
        assertDoesNotThrow(() -> EditVersion.require(3L, 3));
    }

    @Test
    void requiresVersion() {
        assertThrows(BadRequestException.class, () -> EditVersion.require(null, 0));
    }

    @Test
    void rejectsStaleAndFutureVersions() {
        assertThrows(OptimisticLockingFailureException.class, () -> EditVersion.require(2L, 3));
        assertThrows(OptimisticLockingFailureException.class, () -> EditVersion.require(4L, 3));
    }

    @Test
    void retryAfterIsPreserved() {
        assertEquals(10, new RateLimitExceededException(10).getRetryAfterSeconds());
    }
}

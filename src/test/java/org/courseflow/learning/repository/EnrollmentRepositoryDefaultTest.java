package org.courseflow.learning.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.courseflow.learning.entity.EnrollmentStatus;
import org.junit.jupiter.api.Test;

class EnrollmentRepositoryDefaultTest {
    private final EnrollmentRepository repository = mock(EnrollmentRepository.class, CALLS_REAL_METHODS);

    @Test
    void activeEnrollmentDelegatesToStatusQuery() {
        when(repository.existsByStudent_IdAndCourse_IdAndStatus(10L, 40L, EnrollmentStatus.ACTIVE)).thenReturn(true);
        assertTrue(repository.hasActiveEnrollment(10L, 40L));
        assertFalse(repository.hasActiveEnrollment(11L, 40L));
    }
}

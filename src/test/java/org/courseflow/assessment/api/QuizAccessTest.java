package org.courseflow.assessment.api;

import org.courseflow.assessment.repository.QuizRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAccessTest {
    @Test
    void checksExistenceThroughOwningRepository() {
        var repository = mock(QuizRepository.class);
        when(repository.existsByModule_Id(10L)).thenReturn(true);
        var access = new QuizAccess(repository);
        assertTrue(access.hasQuiz(10L));
        assertFalse(access.hasQuiz(11L));
        verify(repository).existsByModule_Id(10L);
        verify(repository).existsByModule_Id(11L);
    }
}

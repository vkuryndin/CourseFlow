package org.courseflow.identity.api;

import static org.courseflow.testing.Fixtures.student;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;

class UserDirectoryCoverageTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserDirectory directory = new UserDirectory(repository);

    @Test
    void requireUserReturnsRepositoryEntity() {
        var user = student();
        when(repository.findById(10L)).thenReturn(Optional.of(user));
        assertSame(user, directory.requireUser(10L));
    }

    @Test
    void requireStudentReturnsEntity() {
        var user = student();
        when(repository.findById(10L)).thenReturn(Optional.of(user));
        assertSame(user, directory.requireStudentReference(10L));
    }

    @Test
    void teacherReferenceUsesWriteLock() {
        var user = teacher();
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(user));
        assertSame(user, directory.lockTeacherReference(20L));
    }

    @Test
    void emailExistenceDelegates() {
        when(repository.existsByEmail("student@example.test")).thenReturn(true);
        assertTrue(directory.emailInUse("student@example.test"));
        assertFalse(directory.emailInUse("other@example.test"));
    }

    @Test
    void systemAdminExistenceDelegates() {
        when(repository.existsBySystemAdminTrue()).thenReturn(true, false);
        assertTrue(directory.systemAdminExists());
        assertFalse(directory.systemAdminExists());
    }
}

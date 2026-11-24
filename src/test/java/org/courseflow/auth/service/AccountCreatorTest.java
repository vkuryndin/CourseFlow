package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AccountCreatorTest {
    private final UserDirectory users = mock(UserDirectory.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final AccountCreator creator = new AccountCreator(users, accounts, CLOCK, encoder, tx);

    private void setup() {
        when(tx.getTransaction(any(TransactionDefinition.class))).thenAnswer((i) -> new SimpleTransactionStatus());
        when(encoder.encode("password")).thenReturn("hash");
    }

    @Test
    void rejectsExistingEmailBeforeHashing() {
        when(users.emailInUse("student@example.test")).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> creator.create("Student", " STUDENT@EXAMPLE.TEST ", Set.of(UserRole.STUDENT), "password",
                false, User::getId));
        verifyNoInteractions(encoder, accounts, tx);
    }

    @Test
    void checksEmailAgainInsideTransaction() {
        setup();
        when(users.emailInUse("student@example.test")).thenReturn(false, true);

        assertThrows(BusinessConflictException.class,
                () -> creator.create("Student", "student@example.test", Set.of(UserRole.STUDENT), "password", false,
                User::getId));
        verifyNoInteractions(accounts);
    }

    @Test
    void createsUserAndAccountWithRequiredPasswordFlag() {
        setup();
        var user = student();
        when(users.createUser("Student", "student@example.test", Set.of(UserRole.STUDENT), NOW)).thenReturn(user);
        Long actual = creator.create(" Student ", " STUDENT@EXAMPLE.TEST ", Set.of(UserRole.STUDENT), "password",
                true, User::getId);

        assertEquals(10L, actual);
        var captor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(captor.capture());
        assertSame(user, captor.getValue().getUser());
        assertEquals("hash", captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().isPasswordChangeRequired());
    }
}

package org.courseflow.identity.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.courseflow.auth.service.AccountCreator;
import org.courseflow.auth.service.CredentialLifecycle;
import org.courseflow.catalog.service.CourseParticipation;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.dto.AdminCreateUserRequest;
import org.courseflow.identity.dto.UpdateUserRequest;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.ProfileRepository;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.service.EnrollmentAccess;
import org.junit.jupiter.api.Test;

class AdminUserServiceCoverageTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CredentialLifecycle credentials = mock(CredentialLifecycle.class);
    private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
    private final CourseParticipation courses = mock(CourseParticipation.class);
    private final AccountCreator creator = mock(AccountCreator.class);
    private final AdminUserService service = new AdminUserService(users, credentials, mock(ProfileRepository.class), enrollments, courses, creator, CLOCK);
    private final User admin = id(new User("Admin", "admin@example.test", Set.of(UserRole.ADMIN), NOW), 1);
    private final User user = student();

    private void setup(User target) {
        admin.markAsSystemAdmin();
        when(users.findSystemAdminForUpdate()).thenReturn(Optional.of(admin));
        when(users.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
    }

    @Test
    void systemAdminCannotLoseAdminRole() {
        setup(admin);
        assertThrows(BusinessConflictException.class, () -> service.updateRoles(1L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT), 0L)));
    }

    @Test
    void systemAdminCannotDeactivate() {
        setup(admin);
        assertThrows(BusinessConflictException.class, () -> service.deactivate(1L));
    }

    @Test
    void systemAdminCannotDelete() {
        setup(admin);
        assertThrows(BusinessConflictException.class, () -> service.deleteUser(1L));
    }

    @Test
    void deletedUserCannotBeModifiedOrActivated() {
        setup(user);
        user.deactivate();
        user.markDeleted(NOW);
        assertThrows(BusinessConflictException.class, () -> service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT), 0L)));
        assertThrows(BusinessConflictException.class, () -> service.updateDetails(10L, new UpdateUserRequest("Name", "other@example.test", 0L)));
        assertThrows(BusinessConflictException.class, () -> service.activate(10L));
        assertThrows(BusinessConflictException.class, () -> service.deactivate(10L));
    }

    @Test
    void missingBootstrapAdminPreventsMutation() {
        assertThrows(IllegalStateException.class, () -> service.deactivate(10L));
        verifyNoInteractions(credentials);
    }

    @Test
    void missingTargetUserRejected() {
        setup(admin);
        assertThrows(ResourceNotFoundException.class, () -> service.activate(10L));
    }

    @Test
    void nameOnlyUpdateDoesNotRevokeSessions() {
        setup(user);
        service.updateDetails(10L, new UpdateUserRequest(" New Name ", user.getEmail(), 0L));
        assertEquals("New Name", user.getDisplayName());
        verify(credentials, never()).revokeSessions(10L);
        verify(credentials, never()).invalidateResetTokens(10L);
    }

    @Test
    void adminCanLoseRoleWhenAnotherActiveAdminExists() {
        setup(user);
        user.changeRoles(Set.of(UserRole.ADMIN));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
        service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT), 0L));
        assertEquals(Set.of(UserRole.STUDENT), user.getRoles());
        verify(credentials).revokeSessions(10L);
    }

    @Test
    void createDefaultsToPasswordChange() {
        var request = new AdminCreateUserRequest("Student", "student@example.test", "Valid-pass-1", Set.of(UserRole.STUDENT), null);
        service.create(request);
        verify(creator).create(eq(request.displayName()), eq(request.email()), eq(request.roles()), eq(request.password()), eq(true), org.mockito.ArgumentMatchers.<Function<User, UserResponse>>any());
    }

    @Test
    void createAllowsExplicitPasswordChangeOptOut() {
        var request = new AdminCreateUserRequest("Student", "student@example.test", "Valid-pass-1", Set.of(UserRole.STUDENT), false);
        service.create(request);
        verify(creator).create(eq(request.displayName()), eq(request.email()), eq(request.roles()), eq(request.password()), eq(false), org.mockito.ArgumentMatchers.<Function<User, UserResponse>>any());
    }
}

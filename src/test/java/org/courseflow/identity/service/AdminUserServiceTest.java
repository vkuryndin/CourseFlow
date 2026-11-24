package org.courseflow.identity.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.courseflow.auth.service.AccountCreator;
import org.courseflow.auth.service.CredentialLifecycle;
import org.courseflow.catalog.service.CourseParticipation;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.identity.dto.UpdateUserRequest;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.entity.Profile;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.ProfileRepository;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.service.EnrollmentAccess;
import org.junit.jupiter.api.Test;

class AdminUserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CredentialLifecycle credentials = mock(CredentialLifecycle.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
    private final CourseParticipation courses = mock(CourseParticipation.class);
    private final AdminUserService service = new AdminUserService(users, credentials, profiles, enrollments, courses,
            mock(AccountCreator.class), CLOCK);
    private final User user = student();

    private void setup() {
        User admin = id(new User("Admin", "admin@example.test", Set.of(UserRole.ADMIN), NOW), 1);
        admin.markAsSystemAdmin();
        when(users.findSystemAdminForUpdate()).thenReturn(Optional.of(admin));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
    }

    @Test
    void cannotRemoveStudentRoleWithActiveEnrollment() {
        setup();
        when(enrollments.hasActiveEnrollments(10L)).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.TEACHER), 0L)));
        assertEquals(Set.of(UserRole.STUDENT), user.getRoles());
        verify(credentials, never()).revokeSessions(anyLong());
    }

    @Test
    void cannotRemoveTeacherRoleWithAssignedCourse() {
        setup();
        user.changeRoles(Set.of(UserRole.TEACHER));
        when(courses.teachesAnyCourse(10L)).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT), 0L)));
    }

    @Test
    void roleChangeRevokesSessionsButNoOpDoesNot() {
        setup();
        service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT), 0L));

        verify(credentials, never()).revokeSessions(10L);
        service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT, UserRole.TEACHER), 0L));
        verify(credentials).revokeSessions(10L);
        assertTrue(user.getRoles().contains(UserRole.TEACHER));
    }

    @Test
    void emailChangeInvalidatesTokensAndSessions() {
        setup();
        service.updateDetails(10L, new UpdateUserRequest(" New ", " NEW@EXAMPLE.TEST ", 0L));

        assertEquals("New", user.getDisplayName());
        assertEquals("new@example.test", user.getEmail());
        verify(credentials).invalidateResetTokens(10L);
        verify(credentials).revokeSessions(10L);
    }

    @Test
    void duplicateEmailDoesNotModifyUser() {
        setup();
        when(users.existsByEmail("new@example.test")).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> service.updateDetails(10L, new UpdateUserRequest("New", "new@example.test", 0L)));
        assertEquals("student@example.test", user.getEmail());
    }

    @Test
    void activeCoursePreventsTeacherDeactivation() {
        setup();
        when(courses.teachesActiveCourse(10L)).thenReturn(true);

        assertThrows(BusinessConflictException.class, () -> service.deactivate(10L));
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void deactivateAndActivate() {
        setup();
        service.deactivate(10L);

        assertEquals(UserStatus.INACTIVE, user.getStatus());
        verify(credentials).revokeSessions(10L);
        service.activate(10L);
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void cannotDisableLastAdmin() {
        setup();
        user.changeRoles(Set.of(UserRole.ADMIN));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThrows(BusinessConflictException.class, () -> service.deactivate(10L));
        assertThrows(BusinessConflictException.class,
                () -> service.updateRoles(10L, new UpdateUserRolesRequest(Set.of(UserRole.STUDENT), 0L)));
    }

    @Test
    void deleteRequiresInactiveUserAndNoEducationalAssignments() {
        setup();

        assertThrows(BusinessConflictException.class, () -> service.deleteUser(10L));
        user.deactivate();
        when(enrollments.hasActiveEnrollments(10L)).thenReturn(true, false);
        when(courses.teachesAnyCourse(10L)).thenReturn(true);
        assertThrows(BusinessConflictException.class, () -> service.deleteUser(10L));
        assertThrows(BusinessConflictException.class, () -> service.deleteUser(10L));
        verify(credentials, never()).deleteCredentials(anyLong());
    }

    @Test
    void deletionAnonymizesHistoryAndIsIdempotent() {
        setup();
        user.deactivate();
        Profile p = new Profile(user);
        p.update("Bio", "https://example.test");
        when(profiles.findByUser_Id(10L)).thenReturn(Optional.of(p));
        service.deleteUser(10L);
        service.deleteUser(10L);

        assertEquals(UserStatus.DELETED, user.getStatus());
        assertEquals(10L, user.getId());
        assertNull(p.getBio());
        assertNull(p.getAvatarUrl());
        verify(credentials).deleteCredentials(10L);
    }
}

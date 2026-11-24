package org.courseflow.identity.service;

import org.courseflow.auth.service.CredentialLifecycle;
import java.time.Clock;
import org.courseflow.auth.service.AccountCreator;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.catalog.service.CourseParticipation;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.dto.AdminCreateUserRequest;
import org.courseflow.identity.dto.UpdateUserRequest;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.entity.Profile;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.ProfileRepository;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.service.EnrollmentAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminUserService {

    private final AccountCreator accountCreator;
    private final Clock clock;

    private final UserRepository userRepository;
    private final CredentialLifecycle credentialLifecycle;
    private final ProfileRepository profileRepository;
    private final EnrollmentAccess enrollmentAccess;
    private final CourseParticipation courseParticipation;

    public AdminUserService(
            UserRepository userRepository,
            CredentialLifecycle credentialLifecycle,
            ProfileRepository profileRepository,
            EnrollmentAccess enrollmentAccess,
            CourseParticipation courseParticipation,
            AccountCreator accountCreator,
            Clock clock
    ) {
        this.accountCreator = accountCreator;
        this.clock = clock;
        this.userRepository = userRepository;
        this.credentialLifecycle = credentialLifecycle;
        this.profileRepository = profileRepository;
        this.enrollmentAccess = enrollmentAccess;
        this.courseParticipation = courseParticipation;
    }

    public UserResponse create(AdminCreateUserRequest request) {
        return accountCreator.create(request.displayName(), request.email(),
                request.roles(), request.password(),
                !Boolean.FALSE.equals(request.passwordChangeRequired()), UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(
            Long userId,
            UpdateUserRolesRequest request
    ) {
        lockSystemAdmin();

        User user = findUserForUpdate(userId);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot change roles of a deleted user"
            );
        }

        Set<UserRole> requestedRoles =
                Set.copyOf(request.roles());

        if (user.getRoles().equals(requestedRoles)) {
            EditVersion.require(request.version(), user.getVersion());
            return UserResponse.from(user);
        }

        if (user.isSystemAdmin()
                && !requestedRoles.contains(UserRole.ADMIN)) {
            throw new BusinessConflictException(
                    "Cannot remove ADMIN role "
                            + "from system administrator"
            );
        }

        boolean removingAdmin =
                user.getStatus() == UserStatus.ACTIVE
                        && user.getRoles().contains(UserRole.ADMIN)
                        && !requestedRoles.contains(UserRole.ADMIN);

        if (removingAdmin) {
            ensureAnotherActiveAdminExists();
        }

        if (user.getRoles().contains(UserRole.TEACHER)
                && !requestedRoles.contains(UserRole.TEACHER)
                && courseParticipation.teachesAnyCourse(userId)) {
            throw new BusinessConflictException(
                    "Reassign all courses before removing TEACHER role"
            );
        }

        if (user.getRoles().contains(UserRole.STUDENT)
                && !requestedRoles.contains(UserRole.STUDENT)
                && enrollmentAccess.hasActiveEnrollments(userId)) {
            throw new BusinessConflictException(
                    "Withdraw active enrollments before removing STUDENT role"
            );
        }

        EditVersion.require(request.version(), user.getVersion());
        user.changeRoles(requestedRoles);
        revokeSessions(userId);

        userRepository.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateDetails(
            Long userId,
            UpdateUserRequest request
    ) {
        lockSystemAdmin();
        User user = findUserForUpdate(userId);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot update a deleted user"
            );
        }

        String newName = request.displayName().trim();
        String newEmail = EmailAddresses.normalize(request.email());

        boolean emailChanged =
                !Objects.equals(user.getEmail(), newEmail);

        if (emailChanged
                && userRepository.existsByEmail(newEmail)) {
            throw new BusinessConflictException(
                    "Email is already registered"
            );
        }

        EditVersion.require(request.version(), user.getVersion());

        user.updateDetails(newName, newEmail);

        if (emailChanged) {
            invalidateResetTokens(userId);
            revokeSessions(userId);
        }

        userRepository.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deactivate(Long userId) {
        lockSystemAdmin();

        User user = findUserForUpdate(userId);

        if (user.isSystemAdmin()) {
            throw new BusinessConflictException(
                    "Cannot deactivate system administrator"
            );
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot deactivate a deleted user"
            );
        }

        if (user.getStatus() == UserStatus.ACTIVE
                && user.getRoles().contains(UserRole.ADMIN)) {
            ensureAnotherActiveAdminExists();
        }

        // Course creation and reassignment lock the target teacher, too.
        if (user.getStatus() == UserStatus.ACTIVE
                && courseParticipation.teachesActiveCourse(userId)) {
            throw new BusinessConflictException(
                    "Reassign or archive active courses before deactivating their teacher");
        }

        user.deactivate();
        invalidateResetTokens(userId);
        revokeSessions(userId);

        userRepository.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse activate(Long userId) {
        lockSystemAdmin();
        User user = findUserForUpdate(userId);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot reactivate a deleted user"
            );
        }

        user.activate();

        userRepository.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long userId) {

        // Keep administrative operations serialized.
        lockSystemAdmin();

        User user = findUserForUpdate(userId);

        if (user.isSystemAdmin()) {
            throw new BusinessConflictException(
                    "Cannot delete system administrator"
            );
        }

        // DELETE is idempotent for an already archived user.
        if (user.getStatus() == UserStatus.DELETED) {
            return;
        }

        if (user.getStatus() != UserStatus.INACTIVE) {
            throw new BusinessConflictException(
                    "Deactivate user before permanent deletion"
            );
        }

        // No active student enrollments may remain.
        boolean hasActiveEnrollments =
                enrollmentAccess.hasActiveEnrollments(userId);

        if (hasActiveEnrollments) {
            throw new BusinessConflictException(
                    "User has active enrollments. "
                            + "Withdraw them before deletion"
            );
        }

        // All courses, including archived, must be reassigned.
        if (courseParticipation.teachesAnyCourse(userId)) {
            throw new BusinessConflictException(
                    "User still teaches courses. "
                            + "Reassign them before deletion"
            );
        }

        // Remove credentials and their dependent records through the Auth module.
        credentialLifecycle.deleteCredentials(userId);

        // Keep profile relation, remove personal content.
        profileRepository.findByUser_Id(userId)
                .ifPresent(Profile::anonymize);

        // Keep User ID for educational history.
        user.markDeleted(clock.instant());
    }

    private User findUserForUpdate(Long userId) {
        // All administrative mutations lock the system administrator first.
        // Coordinate with login/password operations before changing User.
        // A deleted user no longer has an account, but its history remains.
        credentialLifecycle.lockAccountIfPresent(userId);
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId
                        )
                );
    }

    private void lockSystemAdmin() {
        userRepository.findSystemAdminForUpdate()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "System administrator is missing"
                        )
                );
    }

    private void ensureAnotherActiveAdminExists() {
        long activeAdmins =
                userRepository.countByRoleAndStatus(
                        UserRole.ADMIN,
                        UserStatus.ACTIVE
                );

        if (activeAdmins <= 1) {
            throw new BusinessConflictException(
                    "Cannot disable the last active ADMIN"
            );
        }
    }

    private void invalidateResetTokens(Long userId) {
        credentialLifecycle.invalidateResetTokens(userId);
    }

    private void revokeSessions(Long userId) {
        credentialLifecycle.revokeSessions(userId);
    }

}

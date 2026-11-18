
package org.courseflow.identity.service;

import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BadRequestException;
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
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ProfileRepository profileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            UserRepository userRepository,
            UserAccountRepository userAccountRepository,
            AuthSessionRepository authSessionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            ProfileRepository profileRepository,
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userAccountRepository = userAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.profileRepository = profileRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(AdminCreateUserRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessConflictException(
                    "Email is already registered"
            );
        }

        if (request.password()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException(
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        User user = new User(
                request.displayName().trim(),
                email,
                request.roles()
        );

        User savedUser = userRepository.saveAndFlush(user);

        UserAccount account = new UserAccount(
                savedUser,
                passwordEncoder.encode(request.password())
        );

        userAccountRepository.saveAndFlush(account);

        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse updateRoles(
            Long userId,
            UpdateUserRolesRequest request
    ) {
        lockSystemAdmin();

        User user = findUser(userId);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot change roles of a deleted user"
            );
        }

        Set<UserRole> requestedRoles =
                Set.copyOf(request.roles());

        if (user.getRoles().equals(requestedRoles)) {
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

        user.changeRoles(requestedRoles);
        revokeSessions(userId);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateDetails(
            Long userId,
            UpdateUserRequest request
    ) {
        User user = findUser(userId);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot update a deleted user"
            );
        }

        String newName = request.displayName().trim();
        String newEmail = normalizeEmail(request.email());

        boolean emailChanged =
                !Objects.equals(user.getEmail(), newEmail);

        if (emailChanged
                && userRepository.existsByEmail(newEmail)) {
            throw new BusinessConflictException(
                    "Email is already registered"
            );
        }

        user.updateDetails(newName, newEmail);

        if (emailChanged) {
            revokeSessions(userId);
        }

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deactivate(Long userId) {
        lockSystemAdmin();

        User user = findUser(userId);

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

        user.deactivate();
        revokeSessions(userId);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse activate(Long userId) {
        User user = findUser(userId);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessConflictException(
                    "Cannot reactivate a deleted user"
            );
        }

        user.activate();

        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long userId) {

        // Keep administrative operations serialized.
        lockSystemAdmin();

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId
                        )
                );

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
                enrollmentRepository.existsByStudent_IdAndStatus(
                        userId,
                        EnrollmentStatus.ACTIVE
                );

        if (hasActiveEnrollments) {
            throw new BusinessConflictException(
                    "User has active enrollments. "
                            + "Withdraw them before deletion"
            );
        }

        // All courses, including archived, must be reassigned.
        if (courseRepository.existsByTeacher_Id(userId)) {
            throw new BusinessConflictException(
                    "User still teaches courses. "
                            + "Reassign them before deletion"
            );
        }

        UserAccount account =
                userAccountRepository.findByUser_Id(userId)
                        .orElseThrow(() ->
                                new BusinessConflictException(
                                        "User account is missing"
                                )
                        );

        // Remove credentials and their dependent records.
        authSessionRepository.deleteAllByAccountId(
                account.getId()
        );

        passwordResetTokenRepository.deleteAllByAccountId(
                account.getId()
        );

        userAccountRepository.delete(account);

        // Keep profile relation, remove personal content.
        profileRepository.findByUser_Id(userId)
                .ifPresent(Profile::anonymize);

        // Keep User ID for educational history.
        user.markDeleted();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
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

    private void revokeSessions(Long userId) {
        authSessionRepository
                .findAllByUserAccount_User_IdAndRevokedFalse(userId)
                .forEach(AuthSession::revoke);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

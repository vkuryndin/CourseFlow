package org.courseflow.identity.entity;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdentityDomainTest {

    @Test
    void newUserIsActiveAndRolesAreDefensiveCopy() {
        Set<UserRole> roles = new HashSet<>(Set.of(UserRole.STUDENT));
        User user = new User("Alice", "alice@example.test", roles, NOW);
        roles.clear();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(Set.of(UserRole.STUDENT), user.getRoles());
        assertEquals(NOW, user.getCreatedAt());
        assertThrows(UnsupportedOperationException.class, () -> user.getRoles().clear());
    }

    @Test
    void deactivateAndReactivate() {
        User user = student();
        user.deactivate();

        assertEquals(UserStatus.INACTIVE, user.getStatus());
        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void activeUserCannotBeDeleted() {
        User user = student();

        assertThrows(IllegalStateException.class, () -> user.markDeleted(NOW));
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void deletionAnonymizesAndPreventsFurtherChanges() {
        User user = student();
        user.deactivate();
        user.markDeleted(NOW);

        assertEquals(UserStatus.DELETED, user.getStatus());
        assertEquals(NOW, user.getDeletedAt());
        assertNull(user.getEmail());
        assertEquals("Deleted user", user.getDisplayName());
        assertTrue(user.getRoles().isEmpty());
        assertThrows(IllegalStateException.class, user::activate);
        assertThrows(IllegalStateException.class, user::deactivate);
        assertThrows(IllegalStateException.class, () -> user.changeRoles(Set.of(UserRole.STUDENT)));
        assertThrows(IllegalStateException.class, () -> user.updateDetails("Name", "x@example.test"));
    }

    @Test
    void systemAdminMustHaveAdminRole() {
        User user = student();

        assertThrows(IllegalStateException.class, user::markAsSystemAdmin);
        user.changeRoles(Set.of(UserRole.ADMIN));
        user.markAsSystemAdmin();
        assertTrue(user.isSystemAdmin());
        assertThrows(IllegalStateException.class, user::deactivate);
        assertThrows(IllegalStateException.class, () -> user.markDeleted(NOW));
        assertThrows(IllegalStateException.class, () -> user.changeRoles(Set.of(UserRole.STUDENT)));
    }

    @Test
    void rolesMustNotBeEmptyOrNull() {
        User user = student();

        assertThrows(IllegalArgumentException.class, () -> user.changeRoles(null));
        assertThrows(IllegalArgumentException.class, () -> user.changeRoles(Set.of()));
        Set<UserRole> invalid = new HashSet<>();
        invalid.add(null);
        assertThrows(IllegalArgumentException.class, () -> user.changeRoles(invalid));
    }

    @Test
    void updateDetailsAndMultipleRoles() {
        User user = student();
        user.updateDetails("New", "new@example.test");
        user.changeRoles(Set.of(UserRole.STUDENT, UserRole.TEACHER));

        assertEquals("New", user.getDisplayName());
        assertEquals("new@example.test", user.getEmail());
        assertEquals(Set.of(UserRole.STUDENT, UserRole.TEACHER), user.getRoles());
    }

    @Test
    void profileUpdateAndAnonymize() {
        User user = student();
        Profile profile = new Profile(user);
        profile.update("Bio", "https://example.test/avatar");

        assertSame(user, profile.getUser());
        assertEquals("Bio", profile.getBio());
        assertEquals("https://example.test/avatar", profile.getAvatarUrl());
        profile.anonymize();
        assertNull(profile.getBio());
        assertNull(profile.getAvatarUrl());
    }
}

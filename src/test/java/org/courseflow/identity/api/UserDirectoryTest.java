package org.courseflow.identity.api;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;

class UserDirectoryTest {
  private final UserRepository users = mock(UserRepository.class);
  private final UserDirectory directory = new UserDirectory(users);

  @Test
  void missingUserAndTeacherRejected() {
    assertThrows(ResourceNotFoundException.class, () -> directory.requireUser(1L));
    assertThrows(ResourceNotFoundException.class, () -> directory.requireStudentReference(1L));
    assertThrows(ResourceNotFoundException.class, () -> directory.lockTeacherReference(1L));
  }

  @Test
  void systemAdminCreationSetsRoleAndFlag() {
    when(users.saveAndFlush(any(User.class))).thenAnswer((i) -> id(i.getArgument(0), 1));
    User admin = directory.createSystemAdmin("admin@example.test", NOW);

    assertTrue(admin.isSystemAdmin());
    assertEquals(Set.of(UserRole.ADMIN), admin.getRoles());
    assertEquals(NOW, admin.getCreatedAt());
  }

  @Test
  void normalUserCreationRetainsRequestedRoles() {
    when(users.saveAndFlush(any(User.class))).thenAnswer((i) -> id(i.getArgument(0), 1));
    User user =
        directory.createUser("Student", "student@example.test", Set.of(UserRole.STUDENT), NOW);

    assertFalse(user.isSystemAdmin());
    assertEquals(Set.of(UserRole.STUDENT), user.getRoles());
  }
}

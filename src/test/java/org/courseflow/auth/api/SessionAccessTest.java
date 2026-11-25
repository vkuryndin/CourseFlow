package org.courseflow.auth.api;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.junit.jupiter.api.Test;

class SessionAccessTest {

  @Test
  void rolesAreSortedAndStartupClearsSessions() {
    AuthSessionRepository repo = mock(AuthSessionRepository.class);
    var service = new SessionAccess(repo);
    UUID id = UUID.randomUUID();
    when(repo.findValidSessionRoles(id, 10L, UserStatus.ACTIVE, NOW, true))
        .thenReturn(List.of(UserRole.TEACHER, UserRole.STUDENT));

    assertEquals(List.of("STUDENT", "TEACHER"), service.activeRoles(id, 10L, NOW, true));
    service.invalidateAllOnStartup();
    verify(repo).deleteAllInBatch();
  }
}

package org.courseflow.auth.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.function.Function;
import org.courseflow.auth.api.AccountCreator;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

class RegistrationServiceTest {
  private final AccountCreator creator = mock(AccountCreator.class);
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final AuthService service =
      new AuthService(
          accounts,
          sessions,
          mock(PasswordEncoder.class),
          mock(JwtService.class),
          CLOCK,
          mock(PlatformTransactionManager.class),
          creator);

  @Test
  void registrationAlwaysCreatesStudentWithoutTemporaryPassword() {
    var request = new RegistrationRequest("Student", "student@example.test", "Valid-pass-1");
    when(creator.create(
            eq(request.displayName()),
            eq(request.email()),
            eq(Set.of(UserRole.STUDENT)),
            eq(request.password()),
            eq(false),
            org.mockito.ArgumentMatchers.<Function<User, RegistrationResponse>>any()))
        .thenAnswer(i -> i.<Function<User, RegistrationResponse>>getArgument(5).apply(student()));
    var response = service.register(request);
    assertEquals(10L, response.id());
    assertEquals(Set.of(UserRole.STUDENT), response.roles());
    assertEquals("student@example.test", response.email());
    verifyNoInteractions(accounts, sessions);
  }
}

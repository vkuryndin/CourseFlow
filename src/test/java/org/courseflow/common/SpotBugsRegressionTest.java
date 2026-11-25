package org.courseflow.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.service.LogoutService;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.InvalidSessionException;
import org.courseflow.common.security.CurrentUser;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SpotBugsRegressionTest {
  @Test
  void pageContentIsAnIndependentReadOnlySnapshot() {
    var source = new ArrayList<>(List.of("first"));
    var page = new PageResponse<>(source, 0, 10, 1, 1, true, true);
    source.add("second");
    assertEquals(List.of("first"), page.content());
    assertThrows(UnsupportedOperationException.class, () -> page.content().clear());
  }

  @Test
  void quizRequestPreservesNullsForValidationAndCopiesNestedLists() {
    var options = new ArrayList<Long>();
    options.add(null);
    var answer = new SubmitQuizRequest.AnswerRequest(1L, options);
    var answers = new ArrayList<>(List.of(answer));
    var request = new SubmitQuizRequest(answers);
    options.clear();
    answers.clear();
    assertEquals(1, request.answers().size());
    assertEquals(1, answer.optionIds().size());
    assertNull(answer.optionIds().get(0));
    assertThrows(UnsupportedOperationException.class, () -> answer.optionIds().add(2L));
    assertThrows(UnsupportedOperationException.class, () -> request.answers().clear());
    assertNull(new SubmitQuizRequest(null).answers());
    assertNull(new SubmitQuizRequest.AnswerRequest(1L, null).optionIds());
  }

  @Test
  void rolesAreCopiedWithoutDiscardingInvalidElements() {
    var roles = new LinkedHashSet<UserRole>();
    roles.add(UserRole.STUDENT);
    roles.add(null);
    var request = new UpdateUserRolesRequest(roles, 0L);
    roles.clear();
    assertEquals(2, request.roles().size());
    assertTrue(request.roles().contains(UserRole.STUDENT));
    assertTrue(request.roles().contains(null));
    assertThrows(UnsupportedOperationException.class, () -> request.roles().clear());
    assertNull(new UpdateUserRolesRequest(null, 0L).roles());
  }

  @Test
  void logoutRejectsMissingAndMalformedSessionIdsBeforeRepositoryAccess() {
    var repository = mock(AuthSessionRepository.class);
    var service = new LogoutService(repository);
    var jwt = mock(Jwt.class);
    assertThrows(InvalidSessionException.class, () -> service.logout(jwt));
    when(jwt.getId()).thenReturn("not-a-uuid");
    assertThrows(InvalidSessionException.class, () -> service.logout(jwt));
    verifyNoInteractions(repository);
  }

  @Test
  void authorityWithoutStringDoesNotMatchRole() {
    GrantedAuthority authority = mock(GrantedAuthority.class);
    var authentication = new TestingAuthenticationToken("user", "credentials", List.of(authority));
    assertFalse(CurrentUser.isAdmin(authentication));
  }
}

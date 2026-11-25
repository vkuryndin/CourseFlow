package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CreateSubmissionRequest;
import org.courseflow.learning.dto.GradeSubmissionRequest;
import org.courseflow.learning.dto.SubmissionResponse;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class SubmissionControllerTest {
  private final SubmissionService submissionService = mock(SubmissionService.class);
  private final SubmissionController controller = new SubmissionController(submissionService);

  @Test
  void submitDelegatesAndReturnsContract() {
    Long assignmentId = 101L;
    CreateSubmissionRequest request = mock(CreateSubmissionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    when(request.content()).thenReturn("submitted work");
    SubmissionResponse expected = mock(SubmissionResponse.class);
    when(submissionService.submit(assignmentId, 42L, "submitted work")).thenReturn(expected);

    var actual = controller.submit(assignmentId, request, jwt);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals(
        "/api/assignments/101/submissions/me", actual.getHeaders().getLocation().toString());
    verify(submissionService).submit(assignmentId, 42L, "submitted work");
  }

  @Test
  void getMySubmissionDelegatesAndReturnsContract() {
    Long assignmentId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    SubmissionResponse expected = mock(SubmissionResponse.class);
    when(submissionService.getMySubmission(assignmentId, 42L)).thenReturn(expected);

    var actual = controller.getMySubmission(assignmentId, jwt);

    assertSame(expected, actual);
    verify(submissionService).getMySubmission(assignmentId, 42L);
  }

  @Test
  void findByAssignmentDelegatesAndReturnsContract() {
    Long assignmentId = 101L;
    SubmissionStatus status = null;
    int page = 1;
    int size = 15;
    String sortBy = "id";
    String direction = "desc";
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    PageResponse<SubmissionResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(submissionService.findByAssignment(
            assignmentId, 42L, true, status, page, size, sortBy, direction))
        .thenReturn(expected);

    var actual =
        controller.findByAssignment(
            assignmentId, status, page, size, sortBy, direction, jwt, authentication);

    assertSame(expected, actual);
    verify(submissionService)
        .findByAssignment(assignmentId, 42L, true, status, page, size, sortBy, direction);
  }

  @Test
  void gradeDelegatesAndReturnsContract() {
    Long assignmentId = 101L;
    Long submissionId = 102L;
    GradeSubmissionRequest request = mock(GradeSubmissionRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "42", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SubmissionResponse expected = mock(SubmissionResponse.class);
    when(submissionService.grade(assignmentId, submissionId, request, 42L, true))
        .thenReturn(expected);

    var actual = controller.grade(assignmentId, submissionId, request, jwt, authentication);

    assertSame(expected, actual);
    verify(submissionService).grade(assignmentId, submissionId, request, 42L, true);
  }
}

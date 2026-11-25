package org.courseflow.learning.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.MyAssignmentResponse;
import org.courseflow.learning.dto.MyQuizAttemptResponse;
import org.courseflow.learning.dto.MySubmissionResponse;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.service.StudentLearningService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class StudentLearningControllerTest {
  private final StudentLearningService studentLearningService = mock(StudentLearningService.class);
  private final StudentLearningController controller =
      new StudentLearningController(studentLearningService);

  @Test
  void getAssignmentsDelegatesAndReturnsContract() {
    Long courseId = 101L;
    MyAssignmentResponse.Status status = null;
    int page = 1;
    int size = 15;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    PageResponse<MyAssignmentResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(studentLearningService.getAssignments(42L, courseId, status, page, size))
        .thenReturn(expected);

    var actual = controller.getAssignments(courseId, status, page, size, jwt);

    assertSame(expected, actual);
    verify(studentLearningService).getAssignments(42L, courseId, status, page, size);
  }

  @Test
  void getSubmissionsDelegatesAndReturnsContract() {
    Long courseId = 101L;
    SubmissionStatus status = null;
    int page = 1;
    int size = 15;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    PageResponse<MySubmissionResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(studentLearningService.getSubmissions(42L, courseId, status, page, size))
        .thenReturn(expected);

    var actual = controller.getSubmissions(courseId, status, page, size, jwt);

    assertSame(expected, actual);
    verify(studentLearningService).getSubmissions(42L, courseId, status, page, size);
  }

  @Test
  void getQuizAttemptsDelegatesAndReturnsContract() {
    Long courseId = 101L;
    QuizSubmissionStatus status = null;
    int page = 1;
    int size = 15;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    PageResponse<MyQuizAttemptResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(studentLearningService.getQuizAttempts(42L, courseId, status, page, size))
        .thenReturn(expected);

    var actual = controller.getQuizAttempts(courseId, status, page, size, jwt);

    assertSame(expected, actual);
    verify(studentLearningService).getQuizAttempts(42L, courseId, status, page, size);
  }
}

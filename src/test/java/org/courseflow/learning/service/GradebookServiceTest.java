package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.learning.dto.GradebookResponse.AssessmentType;
import org.courseflow.learning.dto.GradebookResponse.ReviewStatus;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.repository.GradebookQueryRepository;
import org.courseflow.learning.repository.GradebookQueryRepository.AssignmentColumn;
import org.courseflow.learning.repository.GradebookQueryRepository.AssignmentResult;
import org.courseflow.learning.repository.GradebookQueryRepository.ColumnRows;
import org.courseflow.learning.repository.GradebookQueryRepository.QuizColumn;
import org.courseflow.learning.repository.GradebookQueryRepository.QuizResult;
import org.courseflow.learning.repository.GradebookQueryRepository.StudentProjection;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GradebookServiceTest {
  private final CourseGuard guard = mock(CourseGuard.class);
  private final ContentStructure content = mock(ContentStructure.class);
  private final GradebookQueryRepository queries = mock(GradebookQueryRepository.class);
  private final GradebookService service = new GradebookService(guard, content, queries, CLOCK);

  @Test
  void assemblesRowsWithIndependentAssessmentIdsAndMissingResults() {
    when(guard.find(40L)).thenReturn(course());
    when(queries.findStudents(40L, null, null, null, null, 0, 20, "studentId", "asc", NOW))
        .thenReturn(
            new JpqlPages.Result<>(
                List.of(
                    new StudentProjection(10L, "Alice", UserStatus.ACTIVE, EnrollmentStatus.ACTIVE),
                    new StudentProjection(11L, "Bob", UserStatus.ACTIVE, EnrollmentStatus.ACTIVE)),
                2,
                0,
                20));
    when(queries.countColumns(40L, null, AssessmentType.ASSIGNMENT)).thenReturn(2L);
    when(queries.countColumns(40L, null, AssessmentType.QUIZ)).thenReturn(1L);
    when(queries.loadColumns(40L, null, 0, 3, 2, 1))
        .thenReturn(
            new ColumnRows(
                List.of(
                    new AssignmentColumn(70L, "Past", 50L, "JPA", 100, NOW),
                    new AssignmentColumn(71L, "Future", 50L, "JPA", 50, NOW.plusSeconds(1))),
                List.of(new QuizColumn(70L, "Quiz", 50L, "JPA"))));
    when(queries.findAssignmentResults(List.of(10L, 11L), List.of(70L, 71L)))
        .thenReturn(List.of(new AssignmentResult(10L, 70L, SubmissionStatus.GRADED, 90)));
    when(queries.findQuizResults(List.of(10L, 11L), List.of(70L), NOW))
        .thenReturn(List.of(new QuizResult(10L, 70L, 2L, 80, 0L)));

    var result =
        service.getGradebook(
            40L, 20L, false, null, null, null, null, null, 0, 20, "studentId", "asc", 0, 3);

    assertEquals(3L, result.totalColumns());
    assertEquals(1L, result.totalColumnPages());
    assertEquals(100, result.columns().get(2).maxScore());
    var alice = result.students().content().get(0).results();
    assertEquals(90, alice.get(0).score());
    assertEquals("NOT_SUBMITTED", alice.get(1).status());
    assertEquals("COMPLETED", alice.get(2).status());
    assertEquals(80, alice.get(2).score());
    var bob = result.students().content().get(1).results();
    assertEquals("OVERDUE", bob.get(0).status());
    assertEquals("NOT_STARTED", bob.get(2).status());
    assertNull(bob.get(2).score());
    assertEquals(0L, bob.get(2).attemptsUsed());
  }

  @Test
  void quizOnlyPageDoesNotCountAssignmentColumns() {
    when(guard.find(40L)).thenReturn(course());
    when(queries.findStudents(40L, "bob", null, null, 50L, 0, 20, "studentName", "desc", NOW))
        .thenReturn(new JpqlPages.Result<>(List.of(), 0, 0, 20));
    when(queries.countColumns(40L, 50L, AssessmentType.QUIZ)).thenReturn(5L);
    when(queries.loadColumns(40L, 50L, 2, 2, 0, 5))
        .thenReturn(new ColumnRows(List.of(), List.of()));

    var result =
        service.getGradebook(
            40L,
            99L,
            true,
            "bob",
            null,
            null,
            50L,
            AssessmentType.QUIZ,
            0,
            20,
            "studentName",
            "desc",
            1,
            2);

    assertEquals(3L, result.totalColumnPages());
    assertTrue(result.students().content().isEmpty());
    verify(content).requireModuleInCourse(40L, 50L);
    verify(queries, org.mockito.Mockito.never()).countColumns(40L, 50L, AssessmentType.ASSIGNMENT);
  }

  @Test
  void rejectsForeignTeacherAndQuizReviewFilter() {
    when(guard.find(40L)).thenReturn(course());
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.getGradebook(
                40L, 99L, false, null, null, null, null, null, 0, 20, "studentId", "asc", 0, 10));
    assertThrows(
        BadRequestException.class,
        () ->
            service.getGradebook(
                40L,
                20L,
                false,
                null,
                null,
                ReviewStatus.GRADED,
                null,
                AssessmentType.QUIZ,
                0,
                20,
                "studentId",
                "asc",
                0,
                10));
    verifyNoInteractions(queries);
  }
}

package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.repository.ProgressQueryRepository;
import org.courseflow.learning.repository.ProgressQueryRepository.AssignmentAggregate;
import org.courseflow.learning.repository.ProgressQueryRepository.AssignmentRow;
import org.courseflow.learning.repository.ProgressQueryRepository.CourseRow;
import org.courseflow.learning.repository.ProgressQueryRepository.QuizAggregate;
import org.courseflow.learning.repository.ProgressQueryRepository.QuizRow;
import org.junit.jupiter.api.Test;

class ProgressServiceTest {
  private final ProgressQueryRepository queries = mock(ProgressQueryRepository.class);
  private final ProgressService service = new ProgressService(queries, CLOCK);

  @Test
  void summaryUsesCompleteCourseTotalsAndDoesNotLoadDetails() {
    when(queries.findCourses(10L, null, null, null, 0, 20, "courseTitle", "asc"))
        .thenReturn(
            new JpqlPages.Result<>(
                List.of(
                    new CourseRow(40L, "ORM", CourseStatus.ACTIVE, EnrollmentStatus.ACTIVE),
                    new CourseRow(41L, "Empty", CourseStatus.ARCHIVED, EnrollmentStatus.WITHDRAWN)),
                2,
                0,
                20));
    when(queries.aggregateAssignments(10L, List.of(40L, 41L), NOW))
        .thenReturn(List.of(new AssignmentAggregate(40L, 3L, 2L, 1L, 80.0, 1L)));
    when(queries.aggregateQuizzes(10L, List.of(40L, 41L)))
        .thenReturn(List.of(new QuizAggregate(40L, 2L, 1L, 100L)));

    var result = service.getMyProgress(10L, null, null, 0, 20, "courseTitle", "asc", false, 0, 10);

    var first = result.content().get(0);
    assertEquals(5, first.totalAssessments());
    assertEquals(3, first.completedAssessments());
    assertEquals(60, first.progressPercent());
    assertEquals(90, first.performancePercent());
    assertEquals(1L, first.totalUpcomingDeadlines());
    assertFalse(first.detailsIncluded());
    assertTrue(first.assignments().isEmpty());
    assertTrue(first.quizzes().isEmpty());
    assertNull(result.content().get(1).performancePercent());
    assertEquals(0, result.content().get(1).progressPercent());
    verify(queries).findCourses(10L, null, null, null, 0, 20, "courseTitle", "asc");
    verify(queries).aggregateAssignments(10L, List.of(40L, 41L), NOW);
    verify(queries).aggregateQuizzes(10L, List.of(40L, 41L));
    verifyNoMoreInteractions(queries);
  }

  @Test
  void detailsPreserveTotalsAndMapAssessmentStatuses() {
    var ids = List.of(40L);
    when(queries.findCourses(10L, 40L, null, null, 0, 20, "courseId", "desc"))
        .thenReturn(
            new JpqlPages.Result<>(
                List.of(new CourseRow(40L, "ORM", CourseStatus.ACTIVE, EnrollmentStatus.ACTIVE)),
                1,
                0,
                20));
    when(queries.aggregateAssignments(10L, ids, NOW))
        .thenReturn(List.of(new AssignmentAggregate(40L, 8L, 2L, 1L, 70.0, 3L)));
    when(queries.aggregateQuizzes(10L, ids))
        .thenReturn(List.of(new QuizAggregate(40L, 4L, 0L, null)));
    var future = new AssignmentRow(40L, 72L, "Future", 100, null, null, NOW.plusSeconds(10));
    when(queries.findAssignments(10L, ids, NOW, 1, 3, false))
        .thenReturn(
            List.of(
                new AssignmentRow(40L, 70L, "Old", 100, null, null, NOW),
                new AssignmentRow(40L, 71L, "Graded", 100, 70, SubmissionStatus.GRADED, NOW),
                future));
    when(queries.findAssignments(10L, ids, NOW, 1, 3, true)).thenReturn(List.of(future));
    when(queries.findQuizzes(10L, ids, NOW, 1, 3))
        .thenReturn(
            List.of(
                new QuizRow(40L, 80L, "Live", null, 1L, 3, 1L),
                new QuizRow(40L, 81L, "Expired", null, 1L, 3, 0L)));

    var row =
        service
            .getMyProgress(10L, 40L, null, 0, 20, "courseId", "desc", true, 1, 3)
            .content()
            .get(0);

    assertTrue(row.detailsIncluded());
    assertEquals(12, row.totalAssessments());
    assertEquals(17, row.progressPercent());
    assertEquals(70, row.performancePercent());
    assertEquals(
        List.of("OVERDUE", "GRADED", "NOT_SUBMITTED"),
        row.assignments().stream().map(a -> a.status()).toList());
    assertEquals(
        List.of("IN_PROGRESS", "EXPIRED"), row.quizzes().stream().map(q -> q.status()).toList());
    assertEquals(72L, row.upcomingDeadlines().get(0).assignmentId());
    assertEquals(1, row.detailPage());
  }

  @Test
  void dashboardFiltersActiveCoursesAndHandlesNoResults() {
    when(queries.findCourses(
            10L, null, EnrollmentStatus.ACTIVE, CourseStatus.ACTIVE, 0, 5, "courseTitle", "asc"))
        .thenReturn(new JpqlPages.Result<>(List.of(), 0, 0, 5));
    assertTrue(service.getDashboardProgress(10L, 5, NOW).content().isEmpty());
    verify(queries)
        .findCourses(
            10L, null, EnrollmentStatus.ACTIVE, CourseStatus.ACTIVE, 0, 5, "courseTitle", "asc");
  }

  @Test
  void rejectsDetailsWithoutCourseAndInvalidPagination() {
    assertThrows(
        BadRequestException.class,
        () -> service.getMyProgress(10L, null, null, 0, 20, "courseId", "asc", true, 0, 10));
    assertThrows(
        BadRequestException.class,
        () -> service.getMyProgress(10L, 0L, null, 0, 20, "courseId", "asc", false, 0, 10));
    assertThrows(
        BadRequestException.class,
        () -> service.getMyProgress(10L, 40L, null, 0, 20, "courseId", "asc", false, -1, 10));
    verifyNoInteractions(queries);
  }
}

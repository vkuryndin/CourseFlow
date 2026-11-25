package org.courseflow.learning.repository;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.QueryMocks.count;
import static org.courseflow.testing.QueryMocks.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.ProgressQueryRepository.AssignmentAggregate;
import org.courseflow.learning.repository.ProgressQueryRepository.AssignmentRow;
import org.courseflow.learning.repository.ProgressQueryRepository.CourseRow;
import org.courseflow.learning.repository.ProgressQueryRepository.QuizAggregate;
import org.courseflow.learning.repository.ProgressQueryRepository.QuizRow;
import org.junit.jupiter.api.Test;

class ProgressQueryRepositoryTest {
  private final EntityManager em = mock(EntityManager.class);
  private final ProgressQueryRepository repository = new ProgressQueryRepository(em);

  @Test
  void coursePageBindsAllOptionalFilters() {
    var row = new CourseRow(40L, "ORM", CourseStatus.ACTIVE, EnrollmentStatus.ACTIVE);
    var q =
        query(
            em,
            CourseRow.class,
            List.of(row),
            "studentId",
            "courseId",
            "enrollmentStatus",
            "courseStatus");
    count(em, 11, "studentId", "courseId", "enrollmentStatus", "courseStatus");

    var result =
        repository.findCourses(
            10L, 40L, EnrollmentStatus.ACTIVE, CourseStatus.ACTIVE, 1, 10, "courseTitle", "desc");

    assertEquals(List.of(row), result.rows());
    assertEquals(11L, result.total());
    verify(q).setParameter("studentId", 10L);
    verify(q).setParameter("courseId", 40L);
    verify(q).setParameter("enrollmentStatus", EnrollmentStatus.ACTIVE);
    verify(q).setParameter("courseStatus", CourseStatus.ACTIVE);
    verify(q).setFirstResult(10);
    verify(q).setMaxResults(10);
    var dataJpql = org.mockito.ArgumentCaptor.forClass(String.class);
    var countJpql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(em).createQuery(dataJpql.capture(), eq(CourseRow.class));
    verify(em).createQuery(countJpql.capture(), eq(Long.class));
    for (String fragment :
        List.of(
            "e.student.id = :studentId",
            "c.id = :courseId",
            "e.status = :enrollmentStatus",
            "c.status = :courseStatus")) {
      assertTrue(dataJpql.getValue().contains(fragment), fragment);
      assertTrue(countJpql.getValue().contains(fragment), fragment);
    }
    assertTrue(dataJpql.getValue().contains("ORDER BY c.title desc"));
  }

  @Test
  void coursePageWithoutFiltersAndInvalidSort() {
    query(em, CourseRow.class, List.of(), "studentId");
    count(em, 0, "studentId");
    assertTrue(
        repository.findCourses(10L, null, null, null, 0, 20, "courseId", "asc").rows().isEmpty());
    assertThrows(
        BadRequestException.class,
        () -> repository.findCourses(10L, null, null, null, 0, 20, "invalid", "asc"));
  }

  @Test
  void emptyCourseIdsDoNotQueryDatabase() {
    assertTrue(repository.aggregateAssignments(10L, List.of(), NOW).isEmpty());
    assertTrue(repository.aggregateQuizzes(10L, List.of()).isEmpty());
    assertTrue(repository.findAssignments(10L, List.of(), NOW, 0, 10, true).isEmpty());
    assertTrue(repository.findQuizzes(10L, List.of(), NOW, 0, 10).isEmpty());
    verifyNoInteractions(em);
  }

  @Test
  void aggregatesBindCourseIdsAndStudent() {
    var a = new AssignmentAggregate(40L, 3L, 2L, 1L, 90.0, 1L);
    var q = new QuizAggregate(40L, 2L, 1L, 80L);
    var assignments = query(em, AssignmentAggregate.class, List.of(a));
    var quizzes = query(em, QuizAggregate.class, List.of(q));
    assertEquals(List.of(a), repository.aggregateAssignments(10L, List.of(40L), NOW));
    assertEquals(List.of(q), repository.aggregateQuizzes(10L, List.of(40L)));
    verify(assignments).setParameter("now", NOW);
    verify(quizzes).setParameter("studentId", 10L);
    verify(quizzes).setParameter("courseIds", List.of(40L));
    verify(em).createQuery(contains("MAX(s.score)"), eq(QuizAggregate.class));
  }

  @Test
  void deadlinesOnlyFilterAndDetailPagination() {
    var row = new AssignmentRow(40L, 70L, "Task", 100, null, null, NOW.plusSeconds(1));
    var q = query(em, AssignmentRow.class, List.of(row));
    assertEquals(List.of(row), repository.findAssignments(10L, List.of(40L), NOW, 2, 5, true));
    verify(q).setParameter("now", NOW);
    verify(q).setFirstResult(10);
    verify(q).setMaxResults(5);
    verify(em).createQuery(contains("s.id IS NULL AND a.dueAt > :now"), eq(AssignmentRow.class));
  }

  @Test
  void allAssignmentsAndQuizDetails() {
    var a = query(em, AssignmentRow.class, List.of());
    var q = query(em, QuizRow.class, List.of());
    repository.findAssignments(10L, List.of(40L), NOW, 0, 5, false);
    repository.findQuizzes(10L, List.of(40L), NOW, 1, 5);
    verify(a, org.mockito.Mockito.never()).setParameter("now", NOW);
    verify(q).setParameter("now", NOW);
    verify(q).setFirstResult(5);
    verify(q).setMaxResults(5);
  }
}

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
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.learning.dto.GradebookResponse.AssessmentType;
import org.courseflow.learning.dto.GradebookResponse.ReviewStatus;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.repository.GradebookQueryRepository.AssignmentColumn;
import org.courseflow.learning.repository.GradebookQueryRepository.AssignmentResult;
import org.courseflow.learning.repository.GradebookQueryRepository.QuizColumn;
import org.courseflow.learning.repository.GradebookQueryRepository.QuizResult;
import org.courseflow.learning.repository.GradebookQueryRepository.StudentProjection;
import org.junit.jupiter.api.Test;

class GradebookQueryRepositoryTest {
  private final EntityManager em = mock(EntityManager.class);
  private final GradebookQueryRepository repository = new GradebookQueryRepository(em);

  @Test
  void overdueFilterBindsSearchModuleAndDeadline() {
    var row = new StudentProjection(10L, "Alice", UserStatus.ACTIVE, EnrollmentStatus.ACTIVE);
    var data =
        query(
            em,
            StudentProjection.class,
            List.of(row),
            "courseId",
            "search",
            "enrollmentStatus",
            "moduleId",
            "now");
    var total = count(em, 1, "courseId", "search", "enrollmentStatus", "moduleId", "now");

    var page =
        repository.findStudents(
            40L,
            " ALICE ",
            EnrollmentStatus.ACTIVE,
            ReviewStatus.OVERDUE,
            50L,
            0,
            20,
            "studentName",
            "desc",
            NOW);

    assertEquals(List.of(row), page.rows());
    verify(data).setParameter("search", "alice");
    verify(data).setParameter("moduleId", 50L);
    verify(total).setParameter("now", NOW);
    verify(em).createQuery(contains("a.dueAt <= :now"), eq(StudentProjection.class));
  }

  @Test
  void submittedFilterBindsSubmissionStatus() {
    var data = query(em, StudentProjection.class, List.of(), "courseId", "submissionStatus");
    count(em, 0, "courseId", "submissionStatus");

    repository.findStudents(
        40L, " ", null, ReviewStatus.SUBMITTED, null, 0, 20, "enrollmentStatus", "asc", NOW);

    verify(data).setParameter("submissionStatus", SubmissionStatus.SUBMITTED);
    verify(em).createQuery(contains("s.status = :submissionStatus"), eq(StudentProjection.class));
  }

  @Test
  void notSubmittedFilterDoesNotRequireDeadline() {
    query(em, StudentProjection.class, List.of(), "courseId");
    count(em, 0, "courseId");
    repository.findStudents(
        40L, null, null, ReviewStatus.NOT_SUBMITTED, null, 0, 20, "studentId", "asc", NOW);
    verify(em).createQuery(contains("AND NOT EXISTS"), eq(StudentProjection.class));
  }

  @Test
  void noReviewFilterAndUnsupportedSort() {
    query(em, StudentProjection.class, List.of(), "courseId");
    count(em, 0, "courseId");
    assertTrue(
        repository
            .findStudents(40L, null, null, null, null, 0, 20, "studentId", "asc", NOW)
            .rows()
            .isEmpty());
    assertThrows(
        BadRequestException.class,
        () -> repository.findStudents(40L, null, null, null, null, 0, 20, "invalid", "asc", NOW));
  }

  @Test
  void countsBothAssessmentTypesWithOptionalModule() {
    var query = count(em, 4);
    assertEquals(4L, repository.countColumns(40L, 50L, AssessmentType.ASSIGNMENT));
    assertEquals(4L, repository.countColumns(40L, null, AssessmentType.QUIZ));
    verify(query).setParameter("moduleId", 50L);
    verify(em).createQuery(contains("FROM Assignment a"), eq(Long.class));
    verify(em).createQuery(contains("FROM Quiz a"), eq(Long.class));
  }

  @Test
  void columnPageCrossesAssignmentQuizBoundary() {
    var assignment = new AssignmentColumn(70L, "Task", 50L, "JPA", 100, NOW);
    var quiz = new QuizColumn(80L, "Quiz", 50L, "JPA");
    var a = query(em, AssignmentColumn.class, List.of(assignment));
    var q = query(em, QuizColumn.class, List.of(quiz));

    var rows = repository.loadColumns(40L, 50L, 2, 2, 3, 4);

    assertEquals(List.of(assignment), rows.assignments());
    assertEquals(List.of(quiz), rows.quizzes());
    verify(a).setFirstResult(2);
    verify(q).setFirstResult(0);
    verify(q).setMaxResults(1);
    verify(q).setParameter("moduleId", 50L);
  }

  @Test
  void quizOnlyPageOffsetsPastAssignmentCount() {
    var q = query(em, QuizColumn.class, List.of());
    assertTrue(repository.loadColumns(40L, null, 5, 2, 3, 4).assignments().isEmpty());
    verify(q).setFirstResult(2);
    verify(q).setMaxResults(2);
  }

  @Test
  void assignmentOnlyPageAndEmptyPageDoNotQueryQuizzes() {
    var assignment = new AssignmentColumn(70L, "Task", 50L, "JPA", 100, NOW);
    query(em, AssignmentColumn.class, List.of(assignment));
    assertTrue(repository.loadColumns(40L, null, 0, 1, 3, 4).quizzes().isEmpty());
    assertTrue(repository.loadColumns(40L, null, 8, 1, 3, 4).assignments().isEmpty());
    verify(em, org.mockito.Mockito.never())
        .createQuery(org.mockito.Mockito.anyString(), eq(QuizColumn.class));
  }

  @Test
  void emptyResultIdsAvoidDatabaseCalls() {
    assertTrue(repository.findAssignmentResults(List.of(), List.of(70L)).isEmpty());
    assertTrue(repository.findAssignmentResults(List.of(10L), List.of()).isEmpty());
    assertTrue(repository.findQuizResults(List.of(), List.of(80L), NOW).isEmpty());
    assertTrue(repository.findQuizResults(List.of(10L), List.of(), NOW).isEmpty());
    verifyNoInteractions(em);
  }

  @Test
  void assessmentResultsBindOnlyRequestedStudentsAndColumns() {
    var assignment = new AssignmentResult(10L, 70L, SubmissionStatus.GRADED, 90);
    var quiz = new QuizResult(10L, 80L, 2L, 80, 1L);
    var a = query(em, AssignmentResult.class, List.of(assignment));
    var q = query(em, QuizResult.class, List.of(quiz));

    assertEquals(List.of(assignment), repository.findAssignmentResults(List.of(10L), List.of(70L)));
    assertEquals(List.of(quiz), repository.findQuizResults(List.of(10L), List.of(80L), NOW));
    verify(a).setParameter("studentIds", List.of(10L));
    verify(a).setParameter("assignmentIds", List.of(70L));
    verify(q).setParameter("quizIds", List.of(80L));
    verify(q).setParameter("now", NOW);
  }
}

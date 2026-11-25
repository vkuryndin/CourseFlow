package org.courseflow.learning.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.GradebookResponse.*;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class GradebookQueryRepository {
  public record StudentProjection(
      Long studentId,
      String name,
      org.courseflow.identity.entity.UserStatus status,
      EnrollmentStatus enrollmentStatus) {}

  public record AssignmentColumn(
      Long id, String title, Long moduleId, String moduleTitle, Integer maxScore, Instant dueAt) {}

  public record QuizColumn(Long id, String title, Long moduleId, String moduleTitle) {}

  public record AssignmentResult(
      Long studentId, Long assignmentId, SubmissionStatus status, Integer score) {}

  public record QuizResult(
      Long studentId, Long quizId, Long attemptsUsed, Integer bestScore, Long activeAttempts) {}

  public record ColumnRows(List<AssignmentColumn> assignments, List<QuizColumn> quizzes) {}

  private final EntityManager entityManager;

  public GradebookQueryRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public JpqlPages.Result<StudentProjection> findStudents(
      Long courseId,
      String q,
      EnrollmentStatus enrollmentStatus,
      ReviewStatus reviewStatus,
      Long moduleId,
      int page,
      int size,
      String sortBy,
      String direction,
      Instant now) {
    String orderBy = buildOrderBy(sortBy, direction);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("courseId", courseId);

    StringBuilder where =
        new StringBuilder(
            """
                FROM Enrollment e
                JOIN e.student u
                WHERE e.course.id = :courseId
                """);

    if (q != null && !q.isBlank()) {
      where.append(" AND LOCATE(:search, LOWER(u.displayName)) > 0");

      parameters.put("search", q.trim().toLowerCase(Locale.ROOT));
    }

    if (enrollmentStatus != null) {
      where.append(" AND e.status = :enrollmentStatus");

      parameters.put("enrollmentStatus", enrollmentStatus);
    }

    appendReviewFilter(where, parameters, reviewStatus, moduleId, now);

    var paged =
        JpqlPages.findPage(
            entityManager,
            StudentProjection.class,
            "SELECT u.id, u.displayName, u.status, e.status",
            "COUNT(e)",
            where.toString(),
            orderBy,
            parameters,
            page,
            size);
    return paged;
  }

  private String buildOrderBy(String sortBy, String direction) {
    String field =
        switch (sortBy) {
          case "studentId" -> "u.id";
          case "studentName" -> "u.displayName";
          case "enrollmentStatus" -> "e.status";
          default -> throw new BadRequestException("Unsupported sort field: " + sortBy);
        };

    return Pagination.orderBy(field, direction, "u.id");
  }

  private void appendReviewFilter(
      StringBuilder where,
      Map<String, Object> parameters,
      ReviewStatus reviewStatus,
      Long moduleId,
      Instant now) {
    if (reviewStatus == null) {
      return;
    }

    where.append(
        """
                 AND EXISTS (
                     SELECT a.id
                     FROM Assignment a
                     WHERE a.lesson.module.course.id = :courseId
                """);

    if (moduleId != null) {
      where.append(" AND a.lesson.module.id = :moduleId");

      parameters.put("moduleId", moduleId);
    }

    if (reviewStatus == ReviewStatus.NOT_SUBMITTED || reviewStatus == ReviewStatus.OVERDUE) {

      where.append(
          """
                     AND NOT EXISTS (
                         SELECT s.id
                         FROM Submission s
                         WHERE s.assignment.id = a.id
                           AND s.student.id = u.id
                     )
                    """);

      // NOT_SUBMITTED retains its previous meaning: no submission, regardless of deadline.
      if (reviewStatus == ReviewStatus.OVERDUE) {
        where.append(" AND a.dueAt <= :now");
        parameters.put("now", now);
      }
    } else {

      where.append(
          """
                     AND EXISTS (
                         SELECT s.id
                         FROM Submission s
                         WHERE s.assignment.id = a.id
                           AND s.student.id = u.id
                           AND s.status = :submissionStatus
                     )
                    """);

      parameters.put("submissionStatus", SubmissionStatus.valueOf(reviewStatus.name()));
    }

    where.append(")");
  }

  public long countColumns(Long courseId, Long moduleId, AssessmentType type) {
    String path = type == AssessmentType.ASSIGNMENT ? "a.lesson.module" : "a.module";
    String entity = type == AssessmentType.ASSIGNMENT ? "Assignment" : "Quiz";
    String jpql =
        "SELECT COUNT(a.id) FROM " + entity + " a WHERE " + path + ".course.id = :courseId";
    if (moduleId != null) jpql += " AND " + path + ".id = :moduleId";
    TypedQuery<Long> query =
        entityManager.createQuery(jpql, Long.class).setParameter("courseId", courseId);
    if (moduleId != null) query.setParameter("moduleId", moduleId);
    return query.getSingleResult();
  }

  public ColumnRows loadColumns(
      Long courseId, Long moduleId, int offset, int limit, long assignmentCount, long quizCount) {
    List<AssignmentColumn> assignments = List.of();
    List<QuizColumn> quizzes = List.of();

    if (offset < assignmentCount) {

      String jpql =
          """
                    SELECT a.id,
                           a.title,
                           a.lesson.module.id,
                           a.lesson.module.title,
                           a.maxScore,
                           a.dueAt
                    FROM Assignment a
                    WHERE a.lesson.module.course.id = :courseId
                    """;

      if (moduleId != null) {
        jpql += " AND a.lesson.module.id = :moduleId";
      }

      jpql +=
          """
                     ORDER BY a.lesson.module.position,
                              a.lesson.module.id,
                              a.lesson.position,
                              a.id
                    """;

      TypedQuery<AssignmentColumn> query =
          entityManager
              .createQuery(jpql, AssignmentColumn.class)
              .setParameter("courseId", courseId);

      if (moduleId != null) {
        query.setParameter("moduleId", moduleId);
      }

      assignments = query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    int remaining = limit - assignments.size();
    int quizOffset = (int) Math.max(0L, offset - assignmentCount);
    if (remaining > 0 && quizOffset < quizCount) {

      String jpql =
          """
                    SELECT q.id,
                           q.title,
                           q.module.id,
                           q.module.title
                    FROM Quiz q
                    WHERE q.module.course.id = :courseId
                    """;

      if (moduleId != null) {
        jpql += " AND q.module.id = :moduleId";
      }

      jpql +=
          """
                     ORDER BY q.module.position,
                              q.module.id,
                              q.id
                    """;

      TypedQuery<QuizColumn> query =
          entityManager.createQuery(jpql, QuizColumn.class).setParameter("courseId", courseId);

      if (moduleId != null) {
        query.setParameter("moduleId", moduleId);
      }

      quizzes = query.setFirstResult(quizOffset).setMaxResults(remaining).getResultList();
    }

    return new ColumnRows(assignments, quizzes);
  }

  public List<AssignmentResult> findAssignmentResults(
      List<Long> studentIds, List<Long> assignmentIds) {
    if (studentIds.isEmpty() || assignmentIds.isEmpty()) return List.of();
    return entityManager
        .createQuery(
            """
                            SELECT s.student.id,
                                   s.assignment.id,
                                   s.status,
                                   s.score
                            FROM Submission s
                            WHERE s.student.id IN :studentIds
                              AND s.assignment.id IN :assignmentIds
                            """,
            AssignmentResult.class)
        .setParameter("studentIds", studentIds)
        .setParameter("assignmentIds", assignmentIds)
        .getResultList();
  }

  public List<QuizResult> findQuizResults(List<Long> studentIds, List<Long> quizIds, Instant now) {
    if (studentIds.isEmpty() || quizIds.isEmpty()) return List.of();
    return entityManager
        .createQuery(
            """
                            SELECT s.student.id,
                                   s.quiz.id,
                                   COUNT(s),
                                   MAX(s.score),
                                   """
                + AssessmentQueryExpressions.ACTIVE_ATTEMPT_COUNT
                + """

                            FROM QuizSubmission s
                            WHERE s.student.id IN :studentIds
                              AND s.quiz.id IN :quizIds
                            GROUP BY s.student.id, s.quiz.id
                            """,
            QuizResult.class)
        .setParameter("studentIds", studentIds)
        .setParameter("quizIds", quizIds)
        .setParameter("now", now)
        .getResultList();
  }
}

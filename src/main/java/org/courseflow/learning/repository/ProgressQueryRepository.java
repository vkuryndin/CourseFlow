package org.courseflow.learning.repository;

import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class ProgressQueryRepository {
    public record CourseRow(Long courseId, String title, CourseStatus courseStatus,
                            EnrollmentStatus enrollmentStatus) {}
    public record AssignmentRow(Long courseId, Long assignmentId, String title, Integer maxScore,
                                Integer score, org.courseflow.learning.entity.SubmissionStatus status, Instant dueAt) {}
    public record QuizRow(Long courseId, Long quizId, String title, Integer bestScore,
                          Long attemptsUsed, Integer maxAttempts, Long activeAttempts) {}

    public record AssignmentAggregate(Long courseId, Long total, Long completed,
                                      Long evaluated, Double performanceSum, Long upcoming) {}
    public record QuizAggregate(Long courseId, Long total, Long evaluated, Long performanceSum) {}
    private final EntityManager entityManager;

    public ProgressQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public JpqlPages.Result<CourseRow> findCourses(Long studentId, Long courseId,
            EnrollmentStatus enrollmentStatus, CourseStatus courseStatus, int page, int size,
            String sortBy, String direction) {
        String orderBy = buildOrderBy(
                sortBy,
                direction
        );

        Map<String, Object> parameters =
                new HashMap<>();

        parameters.put("studentId", studentId);

        StringBuilder where = new StringBuilder("""
                FROM Enrollment e
                JOIN e.course c
                WHERE e.student.id = :studentId
                """);

        if (courseId != null) {
            where.append(" AND c.id = :courseId");
            parameters.put("courseId", courseId);
        }

        if (enrollmentStatus != null) {
            where.append(
                    " AND e.status = :enrollmentStatus"
            );

            parameters.put(
                    "enrollmentStatus",
                    enrollmentStatus
            );
        }

        if (courseStatus != null) {
            where.append(" AND c.status = :courseStatus");
            parameters.put("courseStatus", courseStatus);
        }

        var paged = JpqlPages.findPage(entityManager, CourseRow.class,
                "SELECT c.id, c.title, c.status, e.status", "COUNT(e)", where.toString(), orderBy, parameters, page, size);
        return paged;
    }

    public List<AssignmentAggregate> aggregateAssignments(Long studentId, List<Long> courseIds, Instant now) {
        if (courseIds.isEmpty()) return List.of();
        return entityManager.createQuery("""
                    SELECT a.lesson.module.course.id, COUNT(a.id),
                           SUM(CASE WHEN s.status IN (org.courseflow.learning.entity.SubmissionStatus.SUBMITTED,
                                                     org.courseflow.learning.entity.SubmissionStatus.GRADED) THEN 1 ELSE 0 END),
                           SUM(CASE WHEN s.status = org.courseflow.learning.entity.SubmissionStatus.GRADED
                                         AND s.score IS NOT NULL THEN 1 ELSE 0 END),
                           SUM(CASE WHEN s.status = org.courseflow.learning.entity.SubmissionStatus.GRADED
                                         AND s.score IS NOT NULL THEN CAST(s.score AS Double) * 100.0D / a.maxScore ELSE 0.0D END),
                           SUM(CASE WHEN s.id IS NULL AND a.dueAt > :now THEN 1 ELSE 0 END)
                    FROM Assignment a
                    LEFT JOIN Submission s ON s.assignment.id = a.id AND s.student.id = :studentId
                    WHERE a.lesson.module.course.id IN :courseIds
                    GROUP BY a.lesson.module.course.id
                    """, AssignmentAggregate.class)
                    .setParameter("studentId", studentId).setParameter("courseIds", courseIds)
                    .setParameter("now", now).getResultList();
    }

    public List<QuizAggregate> aggregateQuizzes(Long studentId, List<Long> courseIds) {
        if (courseIds.isEmpty()) return List.of();
        // Aggregate best score per quiz first, so multiple attempts do not inflate progress.
        return entityManager.createQuery("""
                    SELECT x.cid, COUNT(x.qid), COUNT(x.best), SUM(x.best)
                    FROM (SELECT q.module.course.id AS cid, q.id AS qid, MAX(s.score) AS best
                          FROM Quiz q LEFT JOIN QuizSubmission s
                            ON s.quiz.id = q.id AND s.student.id = :studentId
                          WHERE q.module.course.id IN :courseIds
                          GROUP BY q.module.course.id, q.id) x
                    GROUP BY x.cid
                    """, QuizAggregate.class)
                    .setParameter("studentId", studentId).setParameter("courseIds", courseIds).getResultList();
    }

    public List<AssignmentRow> findAssignments(Long studentId, List<Long> courseIds, Instant now,
            int detailPage, int detailSize, boolean deadlinesOnly) {
        if (courseIds.isEmpty()) return List.of();
        TypedQuery<AssignmentRow> query = entityManager.createQuery(
                        """
                        SELECT a.lesson.module.course.id,
                               a.id,
                               a.title,
                               a.maxScore,
                               s.score,
                               s.status,
                               a.dueAt
                        FROM Assignment a
                        LEFT JOIN Submission s
                            ON s.assignment.id = a.id
                           AND s.student.id = :studentId
                        WHERE a.lesson.module.course.id
                            IN :courseIds
                        """ + (deadlinesOnly ? " AND s.id IS NULL AND a.dueAt > :now " : "") + """
                        ORDER BY a.lesson.module.course.id,
                                 a.dueAt,
                                 a.id
                        """,
                        AssignmentRow.class
                )
                .setParameter("studentId", studentId)
                .setParameter("courseIds", courseIds);
        if (deadlinesOnly) query.setParameter("now", now);
        return query.setFirstResult(detailPage * detailSize)
                .setMaxResults(detailSize).getResultList();

    }

    public List<QuizRow> findQuizzes(Long studentId, List<Long> courseIds, Instant now,
            int detailPage, int detailSize) {
        if (courseIds.isEmpty()) return List.of();
        return entityManager.createQuery(
                        """
                        SELECT q.module.course.id,
                               q.id,
                               q.title,
                               MAX(s.score),
                               COUNT(s.id),
                               q.maxAttempts,
                               """ + AssessmentQueryExpressions.ACTIVE_ATTEMPT_COUNT + """

                        FROM Quiz q
                        LEFT JOIN QuizSubmission s
                            ON s.quiz.id = q.id
                           AND s.student.id = :studentId
                        WHERE q.module.course.id
                            IN :courseIds
                        GROUP BY q.module.course.id,
                                 q.id,
                                 q.title,
                                 q.maxAttempts
                        ORDER BY q.module.course.id,
                                 q.id
                        """,
                        QuizRow.class
                )
                .setParameter("studentId", studentId)
                .setParameter("courseIds", courseIds)
                .setParameter("now", now)
                .setFirstResult(detailPage * detailSize)
                .setMaxResults(detailSize)
                .getResultList();

    }

    private String buildOrderBy(
            String sortBy,
            String direction
    ) {
        String field = switch (sortBy) {
            case "courseId" -> "c.id";
            case "courseTitle" -> "c.title";
            default -> throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        };

        return Pagination.orderBy(field, direction, "c.id");
    }
}

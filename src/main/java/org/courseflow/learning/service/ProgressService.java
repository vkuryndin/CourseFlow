package org.courseflow.learning.service;

import java.time.Clock;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.CourseProgressResponse;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;
import org.courseflow.learning.dto.CourseProgressResponse.AssignmentProgress;
import org.courseflow.learning.dto.CourseProgressResponse.QuizProgress;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProgressService {
    public record CourseRow(Long courseId, String title, CourseStatus courseStatus,
                            EnrollmentStatus enrollmentStatus) {}
    public record AssignmentRow(Long courseId, Long assignmentId, String title, Integer maxScore,
                                Integer score, org.courseflow.learning.entity.SubmissionStatus status, Instant dueAt) {}
    public record QuizRow(Long courseId, Long quizId, String title, Integer bestScore,
                          Long attemptsUsed, Integer maxAttempts, Long activeAttempts) {}


    public record AssignmentAggregate(Long courseId, Long total, Long completed,
                                      Long evaluated, Double performanceSum, Long upcoming) {}
    public record QuizAggregate(Long courseId, Long total, Long evaluated, Long performanceSum) {}

    private final Clock clock;

    private final EntityManager entityManager;

    public ProgressService(
            EntityManager entityManager,
            Clock clock
    ) {
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<CourseProgressSummaryResponse> getDashboardProgress(Long studentId, int size, Instant now) {
        return loadSummaries(studentId, null, EnrollmentStatus.ACTIVE, CourseStatus.ACTIVE,
                0, size, "courseTitle", "asc", now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<CourseProgressResponse> getMyProgress(
            Long studentId, Long courseId, EnrollmentStatus enrollmentStatus,
            int page, int size, String sortBy, String direction,
            boolean includeDetails, int detailPage, int detailSize
    ) {
        Pagination.validate(detailPage, detailSize);
        if (includeDetails && courseId == null) {
            throw new BadRequestException("courseId is required when includeDetails is true");
        }
        Instant now = clock.instant();
        var summaries = loadSummaries(studentId, courseId, enrollmentStatus, null, page, size, sortBy, direction, now);
        List<CourseProgressResponse> result = new ArrayList<>();
        for (var summary : summaries.content()) {
            List<AssignmentProgress> assignments = List.of();
            List<QuizProgress> quizzes = List.of();
            List<AssignmentProgress> deadlines = List.of();
            if (includeDetails) {
                List<Long> ids = List.of(summary.courseId());
                assignments = loadAssignments(studentId, ids, now, detailPage, detailSize, false)
                        .getOrDefault(summary.courseId(), List.of());
                quizzes = loadQuizzes(studentId, ids, now, detailPage, detailSize)
                        .getOrDefault(summary.courseId(), List.of());
                if (summary.totalUpcomingDeadlines() > 0) {
                    deadlines = loadAssignments(studentId, ids, now, detailPage, detailSize, true)
                            .getOrDefault(summary.courseId(), List.of());
                }
            }
            result.add(new CourseProgressResponse(summary.courseId(), summary.courseTitle(),
                    summary.courseStatus(), summary.enrollmentStatus(), summary.totalAssessments(),
                    summary.completedAssessments(), summary.progressPercent(), summary.performancePercent(),
                    assignments, quizzes, deadlines, includeDetails, detailPage, detailSize,
                    summary.totalAssignments(), summary.totalQuizzes(), summary.totalUpcomingDeadlines()));
        }
        return new PageResponse<>(result, summaries.page(), summaries.size(), summaries.totalElements(),
                summaries.totalPages(), summaries.first(), summaries.last());
    }

    private PageResponse<CourseProgressSummaryResponse> loadSummaries(
            Long studentId, Long courseId, EnrollmentStatus enrollmentStatus, CourseStatus courseStatus,
            int page, int size, String sortBy, String direction, Instant now
    ) {
        Pagination.validate(page, size);

        if (courseId != null && courseId < 1) {
            throw new BadRequestException(
                    "Invalid courseId"
            );
        }

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

        TypedQuery<CourseRow> courseQuery =
                entityManager.createQuery(
                        """
                        SELECT c.id,
                               c.title,
                               c.status,
                               e.status
                        """
                                + where
                                + orderBy,
                        CourseRow.class
                );

        TypedQuery<Long> countQuery =
                entityManager.createQuery(
                        "SELECT COUNT(e) " + where,
                        Long.class
                );

        parameters.forEach((name, value) -> {
            courseQuery.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        List<CourseRow> courses = courseQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        long total = countQuery.getSingleResult();

        List<Long> courseIds = courses.stream()
                .map(row -> row.courseId())
                .toList();

        Map<Long, AssignmentAggregate> assignments = new HashMap<>();
        Map<Long, QuizAggregate> quizzes = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (var row : entityManager.createQuery("""
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
                    .setParameter("now", now).getResultList()) {
                assignments.put(row.courseId(), row);
            }
            // Hibernate HQL derived root: first find the best score per quiz,
            // then aggregate by course so multiple attempts never inflate progress.
            for (var row : entityManager.createQuery("""
                    SELECT x.cid, COUNT(x.qid), COUNT(x.best), SUM(x.best)
                    FROM (SELECT q.module.course.id AS cid, q.id AS qid, MAX(s.score) AS best
                          FROM Quiz q LEFT JOIN QuizSubmission s
                            ON s.quiz.id = q.id AND s.student.id = :studentId
                          WHERE q.module.course.id IN :courseIds
                          GROUP BY q.module.course.id, q.id) x
                    GROUP BY x.cid
                    """, QuizAggregate.class)
                    .setParameter("studentId", studentId).setParameter("courseIds", courseIds).getResultList()) {
                quizzes.put(row.courseId(), row);
            }
        }
        List<CourseProgressSummaryResponse> result = new ArrayList<>();
        for (CourseRow row : courses) {
            var a = assignments.get(row.courseId());
            var q = quizzes.get(row.courseId());
            long assignmentCount = a == null ? 0 : a.total();
            long quizCount = q == null ? 0 : q.total();
            long completed = (a == null ? 0 : a.completed()) + (q == null ? 0 : q.evaluated());
            long evaluated = (a == null ? 0 : a.evaluated()) + (q == null ? 0 : q.evaluated());
            double performance = (a == null ? 0.0 : a.performanceSum())
                    + (q == null || q.performanceSum() == null ? 0.0 : q.performanceSum());
            long totalAssessments = assignmentCount + quizCount;
            long deadlines = a != null && row.courseStatus() == CourseStatus.ACTIVE
                    && row.enrollmentStatus() == EnrollmentStatus.ACTIVE ? a.upcoming() : 0;
            result.add(new CourseProgressSummaryResponse(row.courseId(), row.title(), row.courseStatus(),
                    row.enrollmentStatus(), Math.toIntExact(totalAssessments), Math.toIntExact(completed),
                    totalAssessments == 0 ? 0 : (int) Math.round(completed * 100.0 / totalAssessments),
                    evaluated == 0 ? null : (int) Math.round(performance / evaluated),
                    assignmentCount, quizCount, deadlines));
        }
        return PageResponse.from(new PageImpl<>(result, PageRequest.of(page, size), total));
    }

    private Map<Long, List<AssignmentProgress>>
    loadAssignments(
            Long studentId,
            List<Long> courseIds,
            Instant now, int detailPage, int detailSize, boolean deadlinesOnly
    ) {
        Map<Long, List<AssignmentProgress>> result =
                new HashMap<>();

        if (courseIds.isEmpty()) {
            return result;
        }

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
        List<AssignmentRow> rows = query.setFirstResult(detailPage * detailSize)
                .setMaxResults(detailSize).getResultList();

        for (AssignmentRow row : rows) {

            Long courseId = row.courseId();

            String status = AssessmentStatuses.assignment(row.status(), row.dueAt(), now);

            AssignmentProgress assignment =
                    new AssignmentProgress(
                            row.assignmentId(),
                            row.title(),
                            row.maxScore(),
                            row.score(),
                            status,
                            row.dueAt()
                    );

            result.computeIfAbsent(
                    courseId,
                    key -> new ArrayList<>()
            ).add(assignment);
        }

        return result;
    }

    private Map<Long, List<QuizProgress>>
    loadQuizzes(
            Long studentId,
            List<Long> courseIds,
            Instant now, int detailPage, int detailSize
    ) {
        Map<Long, List<QuizProgress>> result =
                new HashMap<>();

        if (courseIds.isEmpty()) {
            return result;
        }

        List<QuizRow> rows = entityManager.createQuery(
                        """
                        SELECT q.module.course.id,
                               q.id,
                               q.title,
                               MAX(s.score),
                               COUNT(s.id),
                               q.maxAttempts,
                               """ + AssessmentStatuses.ACTIVE_ATTEMPT_COUNT + """

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

        for (QuizRow row : rows) {

            Long courseId = row.courseId();
            Integer bestScore = row.bestScore();
            Long attemptsUsed = row.attemptsUsed();

            String status = AssessmentStatuses.quiz(bestScore, attemptsUsed, row.activeAttempts());

            QuizProgress quiz = new QuizProgress(
                    row.quizId(),
                    row.title(),
                    bestScore,
                    attemptsUsed,
                    row.maxAttempts(),
                    status
            );

            result.computeIfAbsent(
                    courseId,
                    key -> new ArrayList<>()
            ).add(quiz);
        }

        return result;
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

        String normalizedDirection = Pagination.direction(direction);

        String orderBy = " ORDER BY "
                + field
                + " "
                + normalizedDirection;

        if (!sortBy.equals("courseId")) {
            orderBy += ", c.id ASC";
        }

        return orderBy;
    }
}

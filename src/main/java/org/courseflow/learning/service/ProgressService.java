package org.courseflow.learning.service;

import java.time.Clock;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.CourseProgressResponse;
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
    public PageResponse<CourseProgressResponse> getMyProgress(
            Long studentId,
            Long courseId,
            EnrollmentStatus enrollmentStatus,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        return getMyProgress(studentId, courseId, enrollmentStatus, null, page, size, sortBy, direction);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<CourseProgressResponse> getMyProgress(
            Long studentId, Long courseId, EnrollmentStatus enrollmentStatus,
            CourseStatus courseStatus, int page, int size, String sortBy, String direction
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

        Instant now = clock.instant();

        Map<Long, List<AssignmentProgress>> assignments =
                loadAssignments(studentId, courseIds, now);

        Map<Long, List<QuizProgress>> quizzes =
                loadQuizzes(studentId, courseIds, now);

        List<CourseProgressResponse> result =
                new ArrayList<>();

        for (CourseRow row : courses) {

            Long id = row.courseId();

            result.add(buildProgress(
                    id,
                    row.title(),
                    row.courseStatus(),
                    row.enrollmentStatus(),
                    assignments.getOrDefault(
                            id,
                            List.of()
                    ),
                    quizzes.getOrDefault(
                            id,
                            List.of()
                    ),
                    now
            ));
        }

        return PageResponse.from(
                new PageImpl<>(
                        result,
                        PageRequest.of(page, size),
                        total
                )
        );
    }

    private Map<Long, List<AssignmentProgress>>
    loadAssignments(
            Long studentId,
            List<Long> courseIds,
            Instant now
    ) {
        Map<Long, List<AssignmentProgress>> result =
                new HashMap<>();

        if (courseIds.isEmpty()) {
            return result;
        }

        List<AssignmentRow> rows = entityManager.createQuery(
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
                        ORDER BY a.lesson.module.course.id,
                                 a.dueAt,
                                 a.id
                        """,
                        AssignmentRow.class
                )
                .setParameter("studentId", studentId)
                .setParameter("courseIds", courseIds)
                .getResultList();

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
            Instant now
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

    private CourseProgressResponse buildProgress(
            Long courseId,
            String courseTitle,
            CourseStatus courseStatus,
            EnrollmentStatus enrollmentStatus,
            List<AssignmentProgress> assignments,
            List<QuizProgress> quizzes,
            Instant now
    ) {
        int totalAssessments =
                assignments.size() + quizzes.size();

        int completedAssessments = 0;
        int evaluatedAssessments = 0;

        double performanceSum = 0.0;

        for (AssignmentProgress assignment : assignments) {

            if ("SUBMITTED".equals(assignment.status()) || "GRADED".equals(assignment.status())) {
                completedAssessments++;
            }

            if ("GRADED".equals(assignment.status())
                    && assignment.score() != null) {

                performanceSum +=
                        assignment.score() * 100.0
                                / assignment.maxScore();

                evaluatedAssessments++;
            }
        }

        for (QuizProgress quiz : quizzes) {

            if (quiz.bestScore() != null) {

                completedAssessments++;

                performanceSum += quiz.bestScore();

                evaluatedAssessments++;
            }
        }

        int progressPercent = totalAssessments == 0
                ? 0
                : (int) Math.round(
                completedAssessments * 100.0
                        / totalAssessments
        );

        Integer performancePercent =
                evaluatedAssessments == 0
                        ? null
                        : (int) Math.round(
                        performanceSum
                                / evaluatedAssessments
                );

        List<AssignmentProgress> upcomingDeadlines =
                List.of();

        if (courseStatus == CourseStatus.ACTIVE
                && enrollmentStatus
                == EnrollmentStatus.ACTIVE) {

            // loadAssignments orders each course by dueAt and assignment id.
            upcomingDeadlines = assignments.stream()
                    .filter(assignment ->
                            "NOT_SUBMITTED".equals(
                                    assignment.status()
                            )
                    )
                    .filter(assignment ->
                            assignment.dueAt().isAfter(now)
                    )
                    .toList();
        }

        return new CourseProgressResponse(
                courseId,
                courseTitle,
                courseStatus,
                enrollmentStatus,
                totalAssessments,
                completedAssessments,
                progressPercent,
                performancePercent,
                assignments,
                quizzes,
                upcomingDeadlines
        );
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


package org.courseflow.learning.service;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProgressService {

    private final EntityManager entityManager;

    public ProgressService(
            EntityManager entityManager
    ) {
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
        if (page < 0 || size < 1 || size > 100
                || page > Integer.MAX_VALUE / size) {

            throw new BadRequestException(
                    "Invalid pagination parameters"
            );
        }

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

        TypedQuery<Object[]> courseQuery =
                entityManager.createQuery(
                        """
                        SELECT c.id,
                               c.title,
                               c.status,
                               e.status
                        """
                                + where
                                + orderBy,
                        Object[].class
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

        List<Object[]> courses = courseQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        long total = countQuery.getSingleResult();

        List<Long> courseIds = courses.stream()
                .map(row -> (Long) row[0])
                .toList();

        Map<Long, List<AssignmentProgress>> assignments =
                loadAssignments(studentId, courseIds);

        Map<Long, List<QuizProgress>> quizzes =
                loadQuizzes(studentId, courseIds);

        Instant now = Instant.now();

        List<CourseProgressResponse> result =
                new ArrayList<>();

        for (Object[] row : courses) {

            Long id = (Long) row[0];

            result.add(buildProgress(
                    id,
                    (String) row[1],
                    (CourseStatus) row[2],
                    (EnrollmentStatus) row[3],
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
            List<Long> courseIds
    ) {
        Map<Long, List<AssignmentProgress>> result =
                new HashMap<>();

        if (courseIds.isEmpty()) {
            return result;
        }

        List<Object[]> rows = entityManager.createQuery(
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
                        Object[].class
                )
                .setParameter("studentId", studentId)
                .setParameter("courseIds", courseIds)
                .getResultList();

        for (Object[] row : rows) {

            Long courseId = (Long) row[0];

            String status = row[5] == null
                    ? "NOT_SUBMITTED"
                    : row[5].toString();

            AssignmentProgress assignment =
                    new AssignmentProgress(
                            (Long) row[1],
                            (String) row[2],
                            (Integer) row[3],
                            (Integer) row[4],
                            status,
                            (Instant) row[6]
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
            List<Long> courseIds
    ) {
        Map<Long, List<QuizProgress>> result =
                new HashMap<>();

        if (courseIds.isEmpty()) {
            return result;
        }

        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT q.module.course.id,
                               q.id,
                               q.title,
                               MAX(s.score),
                               COUNT(s.id),
                               q.maxAttempts
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
                        Object[].class
                )
                .setParameter("studentId", studentId)
                .setParameter("courseIds", courseIds)
                .getResultList();

        for (Object[] row : rows) {

            Long courseId = (Long) row[0];
            Integer bestScore = (Integer) row[3];
            Long attemptsUsed = (Long) row[4];

            String status;

            if (bestScore != null) {
                status = "COMPLETED";
            } else if (attemptsUsed == 0) {
                status = "NOT_STARTED";
            } else {
                status = "NOT_COMPLETED";
            }

            QuizProgress quiz = new QuizProgress(
                    (Long) row[1],
                    (String) row[2],
                    bestScore,
                    attemptsUsed,
                    (Integer) row[5],
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

            if (!"NOT_SUBMITTED".equals(
                    assignment.status()
            )) {
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

            upcomingDeadlines = assignments.stream()
                    .filter(assignment ->
                            "NOT_SUBMITTED".equals(
                                    assignment.status()
                            )
                    )
                    .filter(assignment ->
                            assignment.dueAt().isAfter(now)
                    )
                    .sorted(
                            Comparator.comparing(
                                    AssignmentProgress::dueAt
                            ).thenComparing(
                                    AssignmentProgress::assignmentId
                            )
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

        String normalizedDirection =
                direction.toLowerCase(Locale.ROOT);

        if (!normalizedDirection.equals("asc")
                && !normalizedDirection.equals("desc")) {

            throw new BadRequestException(
                    "Direction must be asc or desc"
            );
        }

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

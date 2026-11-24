package org.courseflow.learning.service;

import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.learning.dto.GradebookResponse;
import org.courseflow.learning.dto.GradebookResponse.*;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GradebookService {

    private final CourseRepository courseRepository;
    private final EntityManager entityManager;

    public GradebookService(
            CourseRepository courseRepository,
            EntityManager entityManager
    ) {
        this.courseRepository = courseRepository;
        this.entityManager = entityManager;
    }

    private record CellKey(
            Long studentId,
            Long assessmentId,
            AssessmentType type
    ) {
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GradebookResponse getGradebook(
            Long courseId,
            Long currentUserId,
            boolean isAdmin,
            String q,
            EnrollmentStatus enrollmentStatus,
            ReviewStatus reviewStatus,
            Long moduleId,
            AssessmentType type,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        requireCourseAccess(
                courseId,
                currentUserId,
                isAdmin
        );

        Pagination.validate(page, size);

        if (type == AssessmentType.QUIZ
                && reviewStatus != null) {
            throw new BadRequestException(
                    "Review status applies only to assignments"
            );
        }

        if (moduleId != null) {
            requireModule(courseId, moduleId);
        }

        String orderBy = buildOrderBy(
                sortBy,
                direction
        );

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("courseId", courseId);

        StringBuilder where = new StringBuilder("""
                FROM Enrollment e
                JOIN e.student u
                WHERE e.course.id = :courseId
                """);

        if (q != null && !q.isBlank()) {
            where.append(
                    " AND LOCATE(:search, LOWER(u.displayName)) > 0"
            );

            parameters.put(
                    "search",
                    q.trim().toLowerCase(Locale.ROOT)
            );
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

        appendReviewFilter(
                where,
                parameters,
                reviewStatus,
                moduleId
        );

        TypedQuery<Object[]> studentQuery =
                entityManager.createQuery(
                        """
                        SELECT u.id,
                               u.displayName,
                               u.status,
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
            studentQuery.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        List<Object[]> studentRows = studentQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        long total = countQuery.getSingleResult();

        List<Column> columns = loadColumns(
                courseId,
                moduleId,
                type
        );

        List<Long> studentIds = studentRows
                .stream()
                .map(row -> (Long) row[0])
                .toList();

        Map<CellKey, Cell> cells = loadResults(
                studentIds,
                columns
        );

        List<StudentRow> result = new ArrayList<>();

        for (Object[] row : studentRows) {

            Long studentId = (Long) row[0];

            List<Cell> studentResults = columns
                    .stream()
                    .map(column -> cells.getOrDefault(
                            new CellKey(
                                    studentId,
                                    column.id(),
                                    column.type()
                            ),
                            emptyCell(column)
                    ))
                    .toList();

            result.add(new StudentRow(
                    studentId,
                    (String) row[1],
                    row[2].toString(),
                    (EnrollmentStatus) row[3],
                    studentResults
            ));
        }

        PageResponse<StudentRow> students =
                PageResponse.from(
                        new PageImpl<>(
                                result,
                                PageRequest.of(page, size),
                                total
                        )
                );

        return new GradebookResponse(
                courseId,
                columns,
                students
        );
    }

    private void requireCourseAccess(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Teacher can view only own course gradebook");
    }

    private void requireModule(
            Long courseId,
            Long moduleId
    ) {
        Long count = entityManager.createQuery(
                        """
                        SELECT COUNT(m)
                        FROM Module m
                        WHERE m.id = :moduleId
                          AND m.course.id = :courseId
                        """,
                        Long.class
                )
                .setParameter("moduleId", moduleId)
                .setParameter("courseId", courseId)
                .getSingleResult();

        if (count == 0) {
            throw new ResourceNotFoundException(
                    "Module not found in course: " + moduleId
            );
        }
    }

    private String buildOrderBy(
            String sortBy,
            String direction
    ) {
        String field = switch (sortBy) {
            case "studentId" -> "u.id";
            case "studentName" -> "u.displayName";
            case "enrollmentStatus" -> "e.status";
            default -> throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        };

        String normalizedDirection = Pagination.direction(direction);

        String orderBy = " ORDER BY "
                + field
                + " "
                + normalizedDirection;

        if (!sortBy.equals("studentId")) {
            orderBy += ", u.id ASC";
        }

        return orderBy;
    }

    private void appendReviewFilter(
            StringBuilder where,
            Map<String, Object> parameters,
            ReviewStatus reviewStatus,
            Long moduleId
    ) {
        if (reviewStatus == null) {
            return;
        }

        where.append("""
                 AND EXISTS (
                     SELECT a.id
                     FROM Assignment a
                     WHERE a.lesson.module.course.id = :courseId
                """);

        if (moduleId != null) {
            where.append(
                    " AND a.lesson.module.id = :moduleId"
            );

            parameters.put("moduleId", moduleId);
        }

        if (reviewStatus == ReviewStatus.NOT_SUBMITTED) {

            where.append("""
                     AND NOT EXISTS (
                         SELECT s.id
                         FROM Submission s
                         WHERE s.assignment.id = a.id
                           AND s.student.id = u.id
                     )
                    """);

        } else {

            where.append("""
                     AND EXISTS (
                         SELECT s.id
                         FROM Submission s
                         WHERE s.assignment.id = a.id
                           AND s.student.id = u.id
                           AND s.status = :submissionStatus
                     )
                    """);

            parameters.put(
                    "submissionStatus",
                    SubmissionStatus.valueOf(
                            reviewStatus.name()
                    )
            );
        }

        where.append(")");
    }

    private List<Column> loadColumns(
            Long courseId,
            Long moduleId,
            AssessmentType type
    ) {
        List<Column> columns = new ArrayList<>();

        if (type == null
                || type == AssessmentType.ASSIGNMENT) {

            String jpql = """
                    SELECT a.id,
                           a.title,
                           a.lesson.module.id,
                           a.lesson.module.title,
                           a.maxScore
                    FROM Assignment a
                    WHERE a.lesson.module.course.id = :courseId
                    """;

            if (moduleId != null) {
                jpql += " AND a.lesson.module.id = :moduleId";
            }

            jpql += """
                     ORDER BY a.lesson.module.position,
                              a.lesson.module.id,
                              a.lesson.position,
                              a.id
                    """;

            TypedQuery<Object[]> query =
                    entityManager.createQuery(
                            jpql,
                            Object[].class
                    ).setParameter("courseId", courseId);

            if (moduleId != null) {
                query.setParameter("moduleId", moduleId);
            }

            for (Object[] row : query.getResultList()) {

                columns.add(new Column(
                        (Long) row[0],
                        AssessmentType.ASSIGNMENT,
                        (String) row[1],
                        (Long) row[2],
                        (String) row[3],
                        (Integer) row[4]
                ));
            }
        }

        if (type == null
                || type == AssessmentType.QUIZ) {

            String jpql = """
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

            jpql += """
                     ORDER BY q.module.position,
                              q.module.id,
                              q.id
                    """;

            TypedQuery<Object[]> query =
                    entityManager.createQuery(
                            jpql,
                            Object[].class
                    ).setParameter("courseId", courseId);

            if (moduleId != null) {
                query.setParameter("moduleId", moduleId);
            }

            for (Object[] row : query.getResultList()) {

                columns.add(new Column(
                        (Long) row[0],
                        AssessmentType.QUIZ,
                        (String) row[1],
                        (Long) row[2],
                        (String) row[3],
                        100
                ));
            }
        }

        return columns;
    }

    private Map<CellKey, Cell> loadResults(
            List<Long> studentIds,
            List<Column> columns
    ) {
        Map<CellKey, Cell> cells = new HashMap<>();

        if (studentIds.isEmpty()) {
            return cells;
        }

        List<Long> assignmentIds = columns
                .stream()
                .filter(column ->
                        column.type() == AssessmentType.ASSIGNMENT
                )
                .map(Column::id)
                .toList();

        List<Long> quizIds = columns
                .stream()
                .filter(column ->
                        column.type() == AssessmentType.QUIZ
                )
                .map(Column::id)
                .toList();

        if (!assignmentIds.isEmpty()) {

            List<Object[]> rows = entityManager.createQuery(
                            """
                            SELECT s.student.id,
                                   s.assignment.id,
                                   s.status,
                                   s.score
                            FROM Submission s
                            WHERE s.student.id IN :studentIds
                              AND s.assignment.id IN :assignmentIds
                            """,
                            Object[].class
                    )
                    .setParameter("studentIds", studentIds)
                    .setParameter("assignmentIds", assignmentIds)
                    .getResultList();

            for (Object[] row : rows) {

                Long studentId = (Long) row[0];
                Long assignmentId = (Long) row[1];

                cells.put(
                        new CellKey(
                                studentId,
                                assignmentId,
                                AssessmentType.ASSIGNMENT
                        ),
                        new Cell(
                                assignmentId,
                                AssessmentType.ASSIGNMENT,
                                row[2].toString(),
                                (Integer) row[3],
                                null
                        )
                );
            }
        }

        if (!quizIds.isEmpty()) {

            List<Object[]> rows = entityManager.createQuery(
                            """
                            SELECT s.student.id,
                                   s.quiz.id,
                                   COUNT(s),
                                   MAX(s.score),
                                   SUM(CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.IN_PROGRESS AND s.expiresAt > :now THEN 1 ELSE 0 END)
                            FROM QuizSubmission s
                            WHERE s.student.id IN :studentIds
                              AND s.quiz.id IN :quizIds
                            GROUP BY s.student.id, s.quiz.id
                            """,
                            Object[].class
                    )
                    .setParameter("studentIds", studentIds)
                    .setParameter("quizIds", quizIds)
                    .setParameter("now", Instant.now())
                    .getResultList();

            for (Object[] row : rows) {

                Long studentId = (Long) row[0];
                Long quizId = (Long) row[1];
                Long attemptsUsed = (Long) row[2];
                Integer bestScore = (Integer) row[3];

                String status = bestScore == null
                        ? (((Number) row[4]).longValue() > 0 ? "IN_PROGRESS" : "EXPIRED")
                        : "COMPLETED";

                cells.put(
                        new CellKey(
                                studentId,
                                quizId,
                                AssessmentType.QUIZ
                        ),
                        new Cell(
                                quizId,
                                AssessmentType.QUIZ,
                                status,
                                bestScore,
                                attemptsUsed
                        )
                );
            }
        }

        return cells;
    }

    private Cell emptyCell(Column column) {

        boolean isQuiz =
                column.type() == AssessmentType.QUIZ;

        return new Cell(
                column.id(),
                column.type(),
                isQuiz ? "NOT_STARTED" : "NOT_SUBMITTED",
                null,
                isQuiz ? 0L : null
        );
    }
}

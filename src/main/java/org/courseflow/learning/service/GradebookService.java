package org.courseflow.learning.service;

import java.time.Clock;
import java.time.Instant;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import org.courseflow.content.repository.ModuleRepository;
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
    public record StudentProjection(Long studentId, String name, org.courseflow.identity.entity.UserStatus status,
                                    EnrollmentStatus enrollmentStatus) {}
    public record AssignmentColumn(Long id, String title, Long moduleId, String moduleTitle, Integer maxScore, Instant dueAt) {}
    public record QuizColumn(Long id, String title, Long moduleId, String moduleTitle) {}
    public record AssignmentResult(Long studentId, Long assignmentId, SubmissionStatus status, Integer score) {}
    public record QuizResult(Long studentId, Long quizId, Long attemptsUsed, Integer bestScore, Long activeAttempts) {}


    private final Clock clock;

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final EntityManager entityManager;

    public GradebookService(
            CourseRepository courseRepository,
            ModuleRepository moduleRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.clock = clock;
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
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
            String direction,
            int columnPage, int columnSize
    ) {
        requireCourseAccess(
                courseId,
                currentUserId,
                isAdmin
        );

        Pagination.validate(page, size);
        Pagination.validate(columnPage, columnSize);

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

        TypedQuery<StudentProjection> studentQuery =
                entityManager.createQuery(
                        """
                        SELECT u.id,
                               u.displayName,
                               u.status,
                               e.status
                        """
                                + where
                                + orderBy,
                        StudentProjection.class
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

        List<StudentProjection> studentRows = studentQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        long total = countQuery.getSingleResult();

        Map<Long, Instant> assignmentDeadlines = new HashMap<>();
        long assignmentCount = type == AssessmentType.QUIZ ? 0 : countColumns(courseId, moduleId, AssessmentType.ASSIGNMENT);
        long quizCount = type == AssessmentType.ASSIGNMENT ? 0 : countColumns(courseId, moduleId, AssessmentType.QUIZ);
        long totalColumns = assignmentCount + quizCount;
        List<Column> columns = loadColumns(courseId, moduleId, assignmentDeadlines,
                columnPage * columnSize, columnSize, assignmentCount, quizCount);
        Instant now = clock.instant();

        List<Long> studentIds = studentRows
                .stream()
                .map(StudentProjection::studentId)
                .toList();

        Map<CellKey, Cell> cells = loadResults(studentIds, columns, now);

        List<StudentRow> result = new ArrayList<>();

        for (StudentProjection row : studentRows) {

            Long studentId = row.studentId();

            List<Cell> studentResults = columns
                    .stream()
                    .map(column -> {
                        Cell cell = cells.get(new CellKey(studentId, column.id(), column.type()));
                        return cell == null ? emptyCell(column, assignmentDeadlines, now) : cell;
                    })
                    .toList();

            result.add(new StudentRow(
                    studentId,
                    row.name(),
                    row.status().name(),
                    row.enrollmentStatus(),
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
                students, columnPage, columnSize, totalColumns,
                totalColumns / columnSize + (totalColumns % columnSize == 0 ? 0 : 1)
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
        if (!moduleRepository.existsByIdAndCourse_Id(moduleId, courseId)) {
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

    private long countColumns(Long courseId, Long moduleId, AssessmentType type) {
        String path = type == AssessmentType.ASSIGNMENT ? "a.lesson.module" : "a.module";
        String entity = type == AssessmentType.ASSIGNMENT ? "Assignment" : "Quiz";
        String jpql = "SELECT COUNT(a.id) FROM " + entity + " a WHERE " + path + ".course.id = :courseId";
        if (moduleId != null) jpql += " AND " + path + ".id = :moduleId";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class).setParameter("courseId", courseId);
        if (moduleId != null) query.setParameter("moduleId", moduleId);
        return query.getSingleResult();
    }

    private List<Column> loadColumns(
            Long courseId,
            Long moduleId,
            Map<Long, Instant> assignmentDeadlines, int offset, int limit,
            long assignmentCount, long quizCount
    ) {
        List<Column> columns = new ArrayList<>();

        if (offset < assignmentCount) {

            String jpql = """
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

            jpql += """
                     ORDER BY a.lesson.module.position,
                              a.lesson.module.id,
                              a.lesson.position,
                              a.id
                    """;

            TypedQuery<AssignmentColumn> query =
                    entityManager.createQuery(
                            jpql,
                            AssignmentColumn.class
                    ).setParameter("courseId", courseId);

            if (moduleId != null) {
                query.setParameter("moduleId", moduleId);
            }

            for (AssignmentColumn row : query.setFirstResult(offset).setMaxResults(limit).getResultList()) {
                assignmentDeadlines.put(row.id(), row.dueAt());

                columns.add(new Column(
                        row.id(),
                        AssessmentType.ASSIGNMENT,
                        row.title(),
                        row.moduleId(),
                        row.moduleTitle(),
                        row.maxScore()
                ));
            }
        }

        int remaining = limit - columns.size();
        int quizOffset = (int) Math.max(0L, offset - assignmentCount);
        if (remaining > 0 && quizOffset < quizCount) {

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

            TypedQuery<QuizColumn> query =
                    entityManager.createQuery(
                            jpql,
                            QuizColumn.class
                    ).setParameter("courseId", courseId);

            if (moduleId != null) {
                query.setParameter("moduleId", moduleId);
            }

            for (QuizColumn row : query.setFirstResult(quizOffset).setMaxResults(remaining).getResultList()) {

                columns.add(new Column(
                        row.id(),
                        AssessmentType.QUIZ,
                        row.title(),
                        row.moduleId(),
                        row.moduleTitle(),
                        100
                ));
            }
        }

        return columns;
    }

    private Map<CellKey, Cell> loadResults(
            List<Long> studentIds,
            List<Column> columns,
            Instant now
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

            List<AssignmentResult> rows = entityManager.createQuery(
                            """
                            SELECT s.student.id,
                                   s.assignment.id,
                                   s.status,
                                   s.score
                            FROM Submission s
                            WHERE s.student.id IN :studentIds
                              AND s.assignment.id IN :assignmentIds
                            """,
                            AssignmentResult.class
                    )
                    .setParameter("studentIds", studentIds)
                    .setParameter("assignmentIds", assignmentIds)
                    .getResultList();

            for (AssignmentResult row : rows) {

                Long studentId = row.studentId();
                Long assignmentId = row.assignmentId();

                cells.put(
                        new CellKey(
                                studentId,
                                assignmentId,
                                AssessmentType.ASSIGNMENT
                        ),
                        new Cell(
                                assignmentId,
                                AssessmentType.ASSIGNMENT,
                                row.status().toString(),
                                row.score(),
                                null
                        )
                );
            }
        }

        if (!quizIds.isEmpty()) {

            List<QuizResult> rows = entityManager.createQuery(
                            """
                            SELECT s.student.id,
                                   s.quiz.id,
                                   COUNT(s),
                                   MAX(s.score),
                                   """ + AssessmentStatuses.ACTIVE_ATTEMPT_COUNT + """

                            FROM QuizSubmission s
                            WHERE s.student.id IN :studentIds
                              AND s.quiz.id IN :quizIds
                            GROUP BY s.student.id, s.quiz.id
                            """,
                            QuizResult.class
                    )
                    .setParameter("studentIds", studentIds)
                    .setParameter("quizIds", quizIds)
                    .setParameter("now", now)
                    .getResultList();

            for (QuizResult row : rows) {

                Long studentId = row.studentId();
                Long quizId = row.quizId();
                Long attemptsUsed = row.attemptsUsed();
                Integer bestScore = row.bestScore();

                String status = AssessmentStatuses.quiz(bestScore, attemptsUsed, row.activeAttempts());

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

    private Cell emptyCell(Column column, Map<Long, Instant> assignmentDeadlines, Instant now) {

        boolean isQuiz =
                column.type() == AssessmentType.QUIZ;

        return new Cell(
                column.id(),
                column.type(),
                isQuiz ? "NOT_STARTED" : AssessmentStatuses.assignment(null, assignmentDeadlines.get(column.id()), now),
                null,
                isQuiz ? 0L : null
        );
    }
}

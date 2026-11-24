package org.courseflow.assessment.service;

import org.courseflow.common.api.JpqlPages;
import java.time.Clock;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Isolation;
import org.courseflow.assessment.dto.QuizAttemptResponse;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import org.courseflow.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import org.courseflow.assessment.dto.QuizAttemptDetailResponse;
import org.courseflow.common.api.PageResponse;

@Service
public class QuizAttemptQueryService {
    private final Clock clock;
    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final CourseGuard courseGuard;
    private final EntityManager entityManager;

    public QuizAttemptQueryService(QuizRepository quizRepository, QuizSubmissionRepository quizSubmissionRepository,
                                   CourseGuard courseGuard, EntityManager entityManager, Clock clock) {
        this.quizRepository = quizRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.courseGuard = courseGuard;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuizAttemptResponse getMyAttempt(Long quizId, Long attemptId, Long studentId) {
        findCourseForQuizRead(quizId);
        return QuizAttemptResponse.at(findOwnedAttemptRead(quizId, attemptId, studentId), clock.instant());
    }

    private Course findCourseForQuizRead(Long quizId) {
        Long courseId = quizRepository.findCourseIdByQuizId(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        return courseGuard.find(courseId);
    }

    private QuizSubmission findOwnedAttemptRead(Long quizId, Long attemptId, Long studentId) {
        return quizSubmissionRepository.findByIdAndQuiz_IdAndStudent_Id(attemptId, quizId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt not found"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<QuizAttemptResponse> getMyAttempts(
            Long quizId,
            Long studentId,
            QuizSubmissionStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        findCourseForQuizRead(quizId);

        return findAttemptPage(
                quizId,
                studentId,
                status,
                page,
                size,
                sortBy,
                direction
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<QuizAttemptResponse> getQuizResults(
            Long quizId,
            Long currentUserId,
            boolean isAdmin,
            Long studentId,
            QuizSubmissionStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        requireResultsAccess(
                quizId,
                currentUserId,
                isAdmin
        );

        return findAttemptPage(
                quizId,
                studentId,
                status,
                page,
                size,
                sortBy,
                direction
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuizAttemptDetailResponse getMyResult(
            Long quizId,
            Long attemptId,
            Long studentId
    ) {
        findCourseForQuizRead(quizId);

        QuizSubmission attempt = findOwnedAttemptRead(
                quizId,
                attemptId,
                studentId
        );

        return buildAttemptDetail(attempt, clock.instant());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuizAttemptDetailResponse getResultForManagement(
            Long quizId,
            Long attemptId,
            Long currentUserId,
            boolean isAdmin
    ) {
        requireResultsAccess(
                quizId,
                currentUserId,
                isAdmin
        );

        QuizSubmission attempt = quizSubmissionRepository
                .findByIdAndQuiz_Id(attemptId, quizId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Quiz attempt not found"
                        )
                );

        if (!attempt.getQuiz().getId().equals(quizId)) {
            throw new ResourceNotFoundException(
                    "Quiz attempt not found"
            );
        }

        return buildAttemptDetail(attempt, clock.instant());
    }

    private void requireResultsAccess(
            Long quizId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = findCourseForQuizRead(quizId);

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Only course teacher or ADMIN can view Quiz results");
    }

    private PageResponse<QuizAttemptResponse> findAttemptPage(
            Long quizId,
            Long studentId,
            QuizSubmissionStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Pagination.validate(page, size);

        if (studentId != null && studentId < 1) {
            throw new BadRequestException(
                    "Invalid studentId"
            );
        }

        Instant now = clock.instant();
        String effectiveStatus = """
                CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.SUBMITTED THEN 'SUBMITTED'
                     WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.EXPIRED
                          OR s.expiresAt <= :now THEN 'EXPIRED'
                     ELSE 'IN_PROGRESS' END
                """;
        String effectiveFinishedAt = """
                CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.IN_PROGRESS
                          AND s.expiresAt <= :now THEN s.expiresAt ELSE s.finishedAt END
                """;
        String sortField = switch (sortBy) {
            case "id" -> "s.id";
            case "attemptNumber" -> "s.attemptNumber";
            case "takenAt" -> "s.takenAt";
            case "finishedAt" -> effectiveFinishedAt;
            case "score" -> "s.score";
            case "status" -> effectiveStatus;
            default -> throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        };

        String normalizedDirection = Pagination.direction(direction);

        String where = """
            FROM QuizSubmission s
            WHERE s.quiz.id = :quizId
            """;

        if (studentId != null) {
            where += " AND s.student.id = :studentId";
        }

        if (status != null) {
            where += " AND (" + effectiveStatus + ") = :status";
        }

        String orderBy = Pagination.orderBy(sortField, normalizedDirection, "s.id");

        Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("quizId", quizId);
        if (studentId != null) parameters.put("studentId", studentId);
        if (status != null) parameters.put("status", status.name());
        parameters.put("now", now);
        var result = JpqlPages.findPage(entityManager, QuizSubmission.class,
                "SELECT s", "COUNT(s)", where, orderBy, parameters, page, size);
        List<QuizAttemptResponse> items = result.rows().stream().map(attempt -> QuizAttemptResponse.at(attempt, now)).toList();
        return new JpqlPages.Result<>(items, result.total(), page, size).response();
    }

    private QuizAttemptDetailResponse buildAttemptDetail(
            QuizSubmission attempt, Instant now
    ) {
        List<QuizAttemptDetailResponse.SelectedAnswer> answers = entityManager.createQuery(
                        """
                        SELECT
                            q.id,
                            q.text,
                            a.id,
                            a.text
                        FROM QuizAnswerSelection s
                        JOIN s.question q
                        JOIN s.answerOption a
                        WHERE s.quizSubmission.id = :attemptId
                        ORDER BY q.position ASC, a.id ASC
                        """,
                        QuizAttemptDetailResponse.SelectedAnswer.class
                )
                .setParameter(
                        "attemptId",
                        attempt.getId()
                )
                .getResultList();

        return new QuizAttemptDetailResponse(
                QuizAttemptResponse.at(attempt, now),
                answers
        );
    }

}

package org.courseflow.assessment.service;

import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.identity.service.StudentAccess;
import java.time.Clock;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Isolation;
import org.courseflow.assessment.dto.QuizAttemptResponse;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.entity.AnswerOption;
import org.courseflow.assessment.entity.Question;
import org.courseflow.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.TypedQuery;
import org.courseflow.assessment.dto.QuizAttemptDetailResponse;
import org.courseflow.common.api.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@Service
public class QuizAttemptService {

    private final Clock clock;

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentAccess enrollmentAccess;
    private final StudentAccess studentAccess;
    private final QuestionRepository questionRepository;
    private final EntityManager entityManager;

    public QuizAttemptService(
            QuizRepository quizRepository,
            QuizSubmissionRepository quizSubmissionRepository,
            CourseRepository courseRepository,
            EnrollmentAccess enrollmentAccess,
            StudentAccess studentAccess,
            QuestionRepository questionRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.clock = clock;
        this.quizRepository = quizRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.courseRepository = courseRepository;
        this.enrollmentAccess = enrollmentAccess;
        this.studentAccess = studentAccess;
        this.questionRepository = questionRepository;
        this.entityManager = entityManager;
    }

    public sealed interface StartResult permits Started, Existing, LimitReached {}
    public record Started(QuizAttemptResponse attempt) implements StartResult {}
    public record Existing(QuizAttemptResponse attempt) implements StartResult {}
    public record LimitReached() implements StartResult {}

    @Transactional
    public StartResult start(
            Long quizId,
            Long studentId
    ) {
        Course course = lockCourseForQuizShared(quizId);
        User student = lockActiveStudent(studentId);

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Quiz not found: " + quizId
                        )
                );

        requireActiveEnrollment(
                course.getId(),
                studentId
        );

        CourseAccessPolicy.requireActive(course,
                "Cannot start Quiz in archived course");

        if (course.getTeacher().getId().equals(studentId)) {
            throw new AccessDeniedException(
                    "Teacher cannot take Quiz in own course"
            );
        }

        if (!hasQuestions(quizId)) {
            throw new BusinessConflictException(
                    "Quiz has no questions"
            );
        }

        Optional<QuizSubmission> latest =
                findLatestAttempt(quizId, studentId);
        Instant now = clock.instant();

        int usedAttempts = 0;

        if (latest.isPresent()) {
            QuizSubmission previous = latest.get();

            usedAttempts = previous.getAttemptNumber();

            if (previous.isActive(now)) {
                return new Existing(QuizAttemptResponse.from(previous));
            }

            if (previous.getStatus()
                    == QuizSubmissionStatus.IN_PROGRESS) {

                previous.expire(now);
            }
        }

        if (usedAttempts >= quiz.getMaxAttempts()) {
            return new LimitReached();
        }

        QuizSubmission attempt = new QuizSubmission(
                quiz,
                student,
                usedAttempts + 1,
                now
        );

        QuizSubmission saved =
                quizSubmissionRepository.saveAndFlush(attempt);

        return new Started(QuizAttemptResponse.from(saved));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public QuizAttemptResponse getMyAttempt(Long quizId, Long attemptId, Long studentId) {
        findCourseForQuizRead(quizId);
        return QuizAttemptResponse.at(findOwnedAttemptRead(quizId, attemptId, studentId), clock.instant());
    }

    private Course findCourseForQuizRead(Long quizId) {
        Long courseId = quizRepository.findCourseIdByQuizId(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
    }

    private QuizSubmission findOwnedAttemptRead(Long quizId, Long attemptId, Long studentId) {
        return quizSubmissionRepository.findByIdAndQuiz_IdAndStudent_Id(attemptId, quizId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt not found"));
    }

    private Course lockCourseForQuizShared(Long quizId) {
        Long courseId = quizRepository.findCourseIdByQuizId(quizId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Quiz not found: " + quizId
                        )
                );

        Course course = courseRepository
                .findByIdForShare(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );
        // The Quiz could have been deleted before we acquired the course lock.
        if (!quizRepository.existsById(quizId)) {
            throw new ResourceNotFoundException("Quiz not found: " + quizId);
        }
        return course;
    }

    private void requireActiveEnrollment(
            Long courseId,
            Long studentId
    ) {
        enrollmentAccess.requireActive(studentId, courseId, "Active enrollment is required to take Quiz");
    }

    private User lockActiveStudent(Long studentId) {
        return studentAccess.lockActive(studentId);
    }

    private Optional<QuizSubmission> findLatestAttempt(Long quizId, Long studentId) {
        return quizSubmissionRepository
                .findFirstByQuiz_IdAndStudent_IdOrderByAttemptNumberDesc(quizId, studentId);
    }

    private boolean hasQuestions(Long quizId) {
        return questionRepository.countByQuiz_Id(quizId) > 0;
    }

    @Transactional
    public Optional<QuizAttemptResponse> submit(
            Long quizId,
            Long attemptId,
            Long studentId,
            SubmitQuizRequest request
    ) {
        Course course = lockCourseForQuizShared(quizId);
        lockActiveStudent(studentId);

        QuizSubmission attempt = findOwnedAttempt(
                quizId,
                attemptId,
                studentId
        );

        requireActiveEnrollment(
                course.getId(),
                studentId
        );

        CourseAccessPolicy.requireActive(course,
                "Cannot submit Quiz in archived course");

        if (course.getTeacher().getId().equals(studentId)) {
            throw new AccessDeniedException(
                    "Teacher cannot take Quiz in own course"
            );
        }

        if (attempt.getStatus()
                == QuizSubmissionStatus.SUBMITTED) {

            throw new BusinessConflictException(
                    "Quiz attempt has already been submitted"
            );
        }

        Instant now = clock.instant();

        if (!attempt.isActive(now)) {
            attempt.expire(now);
            return Optional.empty();
        }

        List<Question> questions = questionRepository.findWithOptionsByQuizId(quizId);

        if (questions.isEmpty()) {
            throw new BusinessConflictException(
                    "Quiz has no questions"
            );
        }

        Map<Long, Set<Long>> selectedAnswers =
                QuizGrader.validateAnswers(questions, request);

        int score = QuizGrader.score(questions, selectedAnswers);

        // Check deadline again before saving the result.
        now = clock.instant();

        if (!attempt.isActive(now)) {
            attempt.expire(now);
            return Optional.empty();
        }

        for (Question question : questions) {

            Set<Long> selected = selectedAnswers.getOrDefault(
                    question.getId(),
                    Set.of()
            );

            for (AnswerOption option
                    : question.getAnswerOptions()) {

                if (selected.contains(option.getId())) {
                    entityManager.persist(
                            attempt.addSelection(question, option)
                    );
                }
            }
        }

        attempt.complete(score, now);

        entityManager.flush();

        return Optional.of(
                QuizAttemptResponse.from(attempt)
        );
    }

    private QuizSubmission findOwnedAttempt(Long quizId, Long attemptId, Long studentId) {
        return quizSubmissionRepository.findOwnedForUpdate(attemptId, quizId, studentId)
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

        String orderBy = " ORDER BY "
                + sortField
                + " "
                + normalizedDirection;

        if (!sortBy.equals("id")) {
            orderBy += ", s.id ASC";
        }

        TypedQuery<QuizSubmission> dataQuery =
                entityManager.createQuery(
                        "SELECT s " + where + orderBy,
                        QuizSubmission.class
                ).setParameter("quizId", quizId);

        TypedQuery<Long> countQuery =
                entityManager.createQuery(
                        "SELECT COUNT(s) " + where,
                        Long.class
                ).setParameter("quizId", quizId);

        if (studentId != null) {
            dataQuery.setParameter("studentId", studentId);
            countQuery.setParameter("studentId", studentId);
        }

        if (status != null) {
            dataQuery.setParameter("status", status.name());
            countQuery.setParameter("status", status.name());
            countQuery.setParameter("now", now);
        }

        if (status != null || sortBy.equals("status") || sortBy.equals("finishedAt")) {
            dataQuery.setParameter("now", now);
        }

        List<QuizAttemptResponse> items = dataQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(attempt -> QuizAttemptResponse.at(attempt, now))
                .toList();

        long total = countQuery.getSingleResult();

        return PageResponse.from(
                new PageImpl<>(
                        items,
                        PageRequest.of(page, size),
                        total
                )
        );
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

package org.courseflow.assessment.service;

import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
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
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.repository.EnrollmentRepository;
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

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final EntityManager entityManager;

    public QuizAttemptService(
            QuizRepository quizRepository,
            QuizSubmissionRepository quizSubmissionRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            QuestionRepository questionRepository,
            EntityManager entityManager
    ) {
        this.quizRepository = quizRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.entityManager = entityManager;
    }

    public record StartResult(
            QuizAttemptResponse attempt,
            boolean created
    ) {
    }

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
        Instant now = Instant.now();

        int usedAttempts = 0;

        if (latest.isPresent()) {
            QuizSubmission previous = latest.get();

            usedAttempts = previous.getAttemptNumber();

            if (previous.isActive(now)) {
                return new StartResult(
                        QuizAttemptResponse.from(previous),
                        false
                );
            }

            if (previous.getStatus()
                    == QuizSubmissionStatus.IN_PROGRESS) {

                previous.expire(now);
            }
        }

        if (usedAttempts >= quiz.getMaxAttempts()) {
            return new StartResult(null, false);
        }

        QuizSubmission attempt = new QuizSubmission(
                quiz,
                student,
                usedAttempts + 1,
                now
        );

        QuizSubmission saved =
                quizSubmissionRepository.saveAndFlush(attempt);

        return new StartResult(
                QuizAttemptResponse.from(saved),
                true
        );
    }

    @Transactional
    public QuizAttemptResponse getMyAttempt(
            Long quizId,
            Long attemptId,
            Long studentId
    ) {
        lockCourseForQuizShared(quizId);

        QuizSubmission attempt = findOwnedAttempt(
                quizId,
                attemptId,
                studentId
        );

        Instant now = Instant.now();

        if (attempt.getStatus()
                == QuizSubmissionStatus.IN_PROGRESS
                && !attempt.isActive(now)) {

            attempt.expire(now);
        }

        return QuizAttemptResponse.from(attempt);
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
        boolean active = enrollmentRepository.hasActiveEnrollment(studentId, courseId);

        if (!active) {
            throw new AccessDeniedException(
                    "Active enrollment is required to take Quiz"
            );
        }
    }

    private User lockActiveStudent(Long studentId) {
        User student = userRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + studentId));
        if (student.getStatus() != UserStatus.ACTIVE
                || !student.getRoles().contains(UserRole.STUDENT)) {
            throw new AccessDeniedException("Active STUDENT is required");
        }
        return student;
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

        Instant now = Instant.now();

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
        now = Instant.now();

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

    @Transactional
    public PageResponse<QuizAttemptResponse> getMyAttempts(
            Long quizId,
            Long studentId,
            QuizSubmissionStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        lockCourseForQuizShared(quizId);

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

    @Transactional
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

    @Transactional
    public QuizAttemptDetailResponse getMyResult(
            Long quizId,
            Long attemptId,
            Long studentId
    ) {
        lockCourseForQuizShared(quizId);

        QuizSubmission attempt = findOwnedAttempt(
                quizId,
                attemptId,
                studentId
        );

        attempt.expire(Instant.now());

        return buildAttemptDetail(attempt);
    }

    @Transactional
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
                .findForUpdate(attemptId, quizId)
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

        attempt.expire(Instant.now());

        return buildAttemptDetail(attempt);
    }

    private void requireResultsAccess(
            Long quizId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = lockCourseForQuizShared(quizId);

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

        String sortField = switch (sortBy) {
            case "id" -> "s.id";
            case "attemptNumber" -> "s.attemptNumber";
            case "takenAt" -> "s.takenAt";
            case "finishedAt" -> "s.finishedAt";
            case "score" -> "s.score";
            case "status" -> "s.status";
            default -> throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        };

        String normalizedDirection = Pagination.direction(direction);

        refreshExpiredAttempts(
                quizId,
                studentId
        );

        String where = """
            FROM QuizSubmission s
            WHERE s.quiz.id = :quizId
            """;

        if (studentId != null) {
            where += " AND s.student.id = :studentId";
        }

        if (status != null) {
            where += " AND s.status = :status";
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
            dataQuery.setParameter("status", status);
            countQuery.setParameter("status", status);
        }

        List<QuizAttemptResponse> items = dataQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(QuizAttemptResponse::from)
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

    private void refreshExpiredAttempts(Long quizId, Long studentId) {
        // Atomic conditional update: after waiting for a concurrent submit,
        // PostgreSQL rechecks the predicate and does not overwrite SUBMITTED.
        String jpql = """
            UPDATE QuizSubmission s
            SET s.status = :expired, s.finishedAt = s.expiresAt
            WHERE s.quiz.id = :quizId
              AND s.status = :inProgress
              AND s.expiresAt <= :now
            """;
        if (studentId != null) {
            jpql += " AND s.student.id = :studentId";
        }
        Query query = entityManager.createQuery(jpql)
                .setParameter("expired", QuizSubmissionStatus.EXPIRED)
                .setParameter("inProgress", QuizSubmissionStatus.IN_PROGRESS)
                .setParameter("quizId", quizId)
                .setParameter("now", Instant.now());
        if (studentId != null) {
            query.setParameter("studentId", studentId);
        }
        query.executeUpdate();
    }

    private QuizAttemptDetailResponse buildAttemptDetail(
            QuizSubmission attempt
    ) {
        List<Object[]> rows = entityManager.createQuery(
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
                        Object[].class
                )
                .setParameter(
                        "attemptId",
                        attempt.getId()
                )
                .getResultList();

        List<QuizAttemptDetailResponse.SelectedAnswer> answers =
                rows.stream()
                        .map(row ->
                                new QuizAttemptDetailResponse.SelectedAnswer(
                                        (Long) row[0],
                                        (String) row[1],
                                        (Long) row[2],
                                        (String) row[3]
                                )
                        )
                        .toList();

        return new QuizAttemptDetailResponse(
                QuizAttemptResponse.from(attempt),
                answers
        );
    }

}

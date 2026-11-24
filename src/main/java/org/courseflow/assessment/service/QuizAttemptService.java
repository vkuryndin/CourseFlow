
package org.courseflow.assessment.service;

import jakarta.persistence.EntityManager;

import org.courseflow.assessment.dto.QuizAttemptResponse;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;

import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;

import org.courseflow.identity.entity.User;
import org.courseflow.identity.repository.UserRepository;

import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.entity.AnswerOption;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.common.exception.BadRequestException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.TypedQuery;

import org.courseflow.assessment.dto.QuizAttemptDetailResponse;
import org.courseflow.common.api.PageResponse;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Locale;


@Service
public class QuizAttemptService {

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public QuizAttemptService(
            QuizRepository quizRepository,
            QuizSubmissionRepository quizSubmissionRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.quizRepository = quizRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
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
        Course course = lockCourseForQuiz(quizId);

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

        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Cannot start Quiz in archived course"
            );
        }

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

        Instant now = Instant.now();

        Optional<QuizSubmission> latest =
                findLatestAttempt(quizId, studentId);

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

        User student = userRepository.findById(studentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Student not found: " + studentId
                        )
                );

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
        lockCourseForQuiz(quizId);

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


    private Course lockCourseForQuiz(Long quizId) {
        Long courseId = entityManager.createQuery(
                        """
                        SELECT q.module.course.id
                        FROM Quiz q
                        WHERE q.id = :quizId
                        """,
                        Long.class
                )
                .setParameter("quizId", quizId)
                .getResultList()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Quiz not found: " + quizId
                        )
                );

        return courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );
    }

    private void requireActiveEnrollment(
            Long courseId,
            Long studentId
    ) {
        boolean active = enrollmentRepository
                .findByStudent_IdAndCourse_Id(
                        studentId,
                        courseId
                )
                .map(enrollment ->
                        enrollment.getStatus()
                                == EnrollmentStatus.ACTIVE
                )
                .orElse(false);

        if (!active) {
            throw new AccessDeniedException(
                    "Active enrollment is required to take Quiz"
            );
        }
    }

    private Optional<QuizSubmission> findLatestAttempt(
            Long quizId,
            Long studentId
    ) {
        return entityManager.createQuery(
                        """
                        SELECT s
                        FROM QuizSubmission s
                        WHERE s.quiz.id = :quizId
                          AND s.student.id = :studentId
                        ORDER BY s.attemptNumber DESC
                        """,
                        QuizSubmission.class
                )
                .setParameter("quizId", quizId)
                .setParameter("studentId", studentId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private boolean hasQuestions(Long quizId) {
        Long count = entityManager.createQuery(
                        """
                        SELECT COUNT(q)
                        FROM Question q
                        WHERE q.quiz.id = :quizId
                        """,
                        Long.class
                )
                .setParameter("quizId", quizId)
                .getSingleResult();

        return count > 0;
    }


    @Transactional
    public Optional<QuizAttemptResponse> submit(
            Long quizId,
            Long attemptId,
            Long studentId,
            SubmitQuizRequest request
    ) {
        Course course = lockCourseForQuiz(quizId);

        QuizSubmission attempt = findOwnedAttempt(
                quizId,
                attemptId,
                studentId
        );

        requireActiveEnrollment(
                course.getId(),
                studentId
        );

        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Cannot submit Quiz in archived course"
            );
        }

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

        List<Question> questions = entityManager.createQuery(
                        """
                        SELECT DISTINCT q
                        FROM Question q
                        LEFT JOIN FETCH q.answerOptions
                        WHERE q.quiz.id = :quizId
                        ORDER BY q.position ASC, q.id ASC
                        """,
                        Question.class
                )
                .setParameter("quizId", quizId)
                .getResultList();

        if (questions.isEmpty()) {
            throw new BusinessConflictException(
                    "Quiz has no questions"
            );
        }

        Map<Long, Set<Long>> selectedAnswers =
                validateAnswers(questions, request);

        int correctQuestions = 0;

        for (Question question : questions) {

            Set<Long> selected = selectedAnswers.getOrDefault(
                    question.getId(),
                    Set.of()
            );

            Set<Long> correct = new HashSet<>();

            for (AnswerOption option
                    : question.getAnswerOptions()) {

                if (option.isCorrect()) {
                    correct.add(option.getId());
                }
            }

            if (selected.equals(correct)) {
                correctQuestions++;
            }
        }

        int score = (int) Math.round(
                correctQuestions * 100.0 / questions.size()
        );

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

    private Map<Long, Set<Long>> validateAnswers(
            List<Question> questions,
            SubmitQuizRequest request
    ) {
        Map<Long, Question> questionsById =
                new HashMap<>();

        for (Question question : questions) {
            questionsById.put(
                    question.getId(),
                    question
            );
        }

        Map<Long, Set<Long>> selectedAnswers =
                new HashMap<>();

        for (SubmitQuizRequest.AnswerRequest answer
                : request.answers()) {

            Long questionId = answer.questionId();

            Question question =
                    questionsById.get(questionId);

            if (question == null) {
                throw new BadRequestException(
                        "Question does not belong to Quiz: "
                                + questionId
                );
            }

            if (selectedAnswers.containsKey(questionId)) {
                throw new BadRequestException(
                        "Duplicate question: " + questionId
                );
            }

            Set<Long> selected =
                    new HashSet<>(answer.optionIds());

            if (selected.size() != answer.optionIds().size()) {
                throw new BadRequestException(
                        "Duplicate answer options are not allowed"
                );
            }

            if (question.getType()
                    == QuestionType.SINGLE_CHOICE
                    && selected.size() > 1) {

                throw new BadRequestException(
                        "SINGLE_CHOICE accepts only one option"
                );
            }

            Set<Long> allowedOptions = new HashSet<>();

            for (AnswerOption option
                    : question.getAnswerOptions()) {

                allowedOptions.add(option.getId());
            }

            if (!allowedOptions.containsAll(selected)) {
                throw new BadRequestException(
                        "Answer option does not belong to question: "
                                + questionId
                );
            }

            selectedAnswers.put(
                    questionId,
                    selected
            );
        }

        return selectedAnswers;
    }

    private QuizSubmission findOwnedAttempt(
            Long quizId,
            Long attemptId,
            Long studentId
    ) {
        QuizSubmission attempt = quizSubmissionRepository
                .findById(attemptId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Quiz attempt not found"
                        )
                );

        if (!attempt.getQuiz().getId().equals(quizId)
                || !attempt.getStudent().getId().equals(studentId)) {

            throw new ResourceNotFoundException(
                    "Quiz attempt not found"
            );
        }

        return attempt;
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
        lockCourseForQuiz(quizId);

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
        lockCourseForQuiz(quizId);

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
                .findById(attemptId)
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
        Course course = lockCourseForQuiz(quizId);

        if (!isAdmin &&
                !course.getTeacher().getId()
                        .equals(currentUserId)) {

            throw new AccessDeniedException(
                    "Only course teacher or ADMIN can view Quiz results"
            );
        }
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
        if (page < 0 || size < 1 || size > 100
                || page > Integer.MAX_VALUE / size) {

            throw new BadRequestException(
                    "Invalid pagination parameters"
            );
        }

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

        String normalizedDirection =
                direction.toLowerCase(Locale.ROOT);

        if (!normalizedDirection.equals("asc")
                && !normalizedDirection.equals("desc")) {

            throw new BadRequestException(
                    "Direction must be asc or desc"
            );
        }

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

    private void refreshExpiredAttempts(
            Long quizId,
            Long studentId
    ) {
        Instant now = Instant.now();

        String jpql = """
            SELECT s
            FROM QuizSubmission s
            WHERE s.quiz.id = :quizId
              AND s.status = :status
              AND s.expiresAt <= :now
            """;

        if (studentId != null) {
            jpql += " AND s.student.id = :studentId";
        }

        TypedQuery<QuizSubmission> query =
                entityManager.createQuery(
                                jpql,
                                QuizSubmission.class
                        )
                        .setParameter("quizId", quizId)
                        .setParameter(
                                "status",
                                QuizSubmissionStatus.IN_PROGRESS
                        )
                        .setParameter("now", now);

        if (studentId != null) {
            query.setParameter("studentId", studentId);
        }

        List<QuizSubmission> expired =
                query.getResultList();

        for (QuizSubmission attempt : expired) {
            attempt.expire(now);
        }

        if (!expired.isEmpty()) {
            entityManager.flush();
        }
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

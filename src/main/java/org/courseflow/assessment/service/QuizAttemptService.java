package org.courseflow.assessment.service;

import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.identity.service.StudentAccess;
import java.time.Clock;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.assessment.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import org.courseflow.assessment.dto.QuizAttemptResponse;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.assessment.repository.QuizRepository;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseGuard;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuizAttemptService {

    private final Clock clock;

    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final CourseGuard courseGuard;
    private final EnrollmentAccess enrollmentAccess;
    private final StudentAccess studentAccess;
    private final QuestionRepository questionRepository;
    private final EntityManager entityManager;

    public QuizAttemptService(
            QuizRepository quizRepository,
            QuizSubmissionRepository quizSubmissionRepository,
            CourseGuard courseGuard,
            EnrollmentAccess enrollmentAccess,
            StudentAccess studentAccess,
            QuestionRepository questionRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.clock = clock;
        this.quizRepository = quizRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.courseGuard = courseGuard;
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

    private Course lockCourseForQuizShared(Long quizId) {
        Long courseId = quizRepository.findCourseIdByQuizId(quizId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Quiz not found: " + quizId
                        )
                );

        Course course = courseGuard.lockShared(courseId);
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

}

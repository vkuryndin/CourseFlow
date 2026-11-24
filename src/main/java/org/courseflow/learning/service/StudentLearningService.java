package org.courseflow.learning.service;

import java.time.Clock;
import org.courseflow.common.api.Pagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.courseflow.assessment.dto.MyQuizAttemptResponse;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.MyAssignmentResponse;
import org.courseflow.learning.dto.MySubmissionResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentLearningService {

    private final Clock clock;
    private static final String ASSIGNMENT_STATUS = """
            CASE WHEN s.status = org.courseflow.learning.entity.SubmissionStatus.GRADED THEN 'GRADED'
                 WHEN s.id IS NOT NULL THEN 'SUBMITTED'
                 WHEN a.dueAt <= :now THEN 'OVERDUE'
                 ELSE 'NOT_SUBMITTED' END
            """;

    private static final String QUIZ_STATUS = """
            CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.SUBMITTED THEN 'SUBMITTED'
                 WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.EXPIRED
                      OR s.expiresAt <= :now THEN 'EXPIRED'
                 ELSE 'IN_PROGRESS' END
            """;

    private final EntityManager entityManager;

    public StudentLearningService(EntityManager entityManager, Clock clock) {
        this.clock = clock;
        this.entityManager = entityManager;
    }

    // Current tasks are limited to active enrollments on active courses.
    public PageResponse<MyAssignmentResponse> getAssignments(
            Long studentId, Long courseId, MyAssignmentResponse.Status status, int page, int size) {
        validate(courseId, page, size);
        Map<String, Object> params = parameters(studentId, courseId);
        params.put("now", clock.instant());
        params.put("courseStatus", CourseStatus.ACTIVE);
        params.put("enrollmentStatus", EnrollmentStatus.ACTIVE);
        String from = """
                FROM Assignment a JOIN a.lesson l JOIN l.module m JOIN m.course c
                LEFT JOIN Submission s ON s.assignment.id = a.id AND s.student.id = :studentId
                WHERE c.status = :courseStatus AND EXISTS (
                    SELECT e.id FROM Enrollment e WHERE e.course.id = c.id
                        AND e.student.id = :studentId AND e.status = :enrollmentStatus)
                """ + courseFilter(courseId);
        if (status != null) {
            from += " AND (" + ASSIGNMENT_STATUS + ") = :status";
            params.put("status", status.name());
        }
        String select = """
                SELECT new org.courseflow.learning.dto.MyAssignmentResponse(
                    a.id, l.id, l.title, c.id, c.title, a.title, a.description, a.dueAt,
                    a.maxScore, s.id,
                """ + ASSIGNMENT_STATUS + ", s.score) ";
        return findPage(MyAssignmentResponse.class, select, "COUNT(a)", from,
                " ORDER BY a.dueAt ASC, a.id ASC", params, page, size);
    }

    // Historical submissions remain available after withdrawal or archiving.
    public PageResponse<MySubmissionResponse> getSubmissions(
            Long studentId, Long courseId, SubmissionStatus status, int page, int size) {
        validate(courseId, page, size);
        Map<String, Object> params = parameters(studentId, courseId);
        String from = """
                FROM Submission s JOIN s.assignment a JOIN a.lesson l JOIN l.module m JOIN m.course c
                WHERE s.student.id = :studentId
                """ + courseFilter(courseId);
        if (status != null) {
            from += " AND s.status = :status";
            params.put("status", status);
        }
        String select = """
                SELECT new org.courseflow.learning.dto.MySubmissionResponse(
                    s.id, a.id, a.title, c.id, c.title, s.content, s.submittedAt,
                    s.status, s.score, a.maxScore, s.feedback)
                """;
        return findPage(MySubmissionResponse.class, select, "COUNT(s)", from,
                " ORDER BY s.submittedAt DESC, s.id DESC", params, page, size);
    }

    // Expiration in this read-only view is calculated at a single instant.
    // Attempt mutation endpoints and the scheduled cleanup persist EXPIRED when required.
    public PageResponse<MyQuizAttemptResponse> getQuizAttempts(
            Long studentId, Long courseId, QuizSubmissionStatus status, int page, int size) {
        validate(courseId, page, size);
        Map<String, Object> params = parameters(studentId, courseId);
        params.put("now", clock.instant());
        String from = """
                FROM QuizSubmission s JOIN s.quiz q JOIN q.module m JOIN m.course c
                WHERE s.student.id = :studentId
                """ + courseFilter(courseId);
        if (status != null) {
            from += " AND (" + QUIZ_STATUS + ") = :status";
            params.put("status", status.name());
        }
        String select = """
                SELECT new org.courseflow.assessment.dto.MyQuizAttemptResponse(
                    s.id, q.id, q.title, c.id, c.title, s.attemptNumber,
                """ + QUIZ_STATUS + """
                    , s.score, s.takenAt, s.expiresAt,
                    CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.IN_PROGRESS
                              AND s.expiresAt <= :now THEN s.expiresAt ELSE s.finishedAt END)
                """;
        return findPage(MyQuizAttemptResponse.class, select, "COUNT(s)", from,
                " ORDER BY s.takenAt DESC, s.id DESC", params, page, size);
    }

    private void validate(Long courseId, int page, int size) {
        Pagination.validate(page, size);
        if (courseId != null && courseId < 1) {
            throw new BadRequestException("Invalid courseId");
        }
    }

    private Map<String, Object> parameters(Long studentId, Long courseId) {
        Map<String, Object> params = new HashMap<>();
        params.put("studentId", studentId);
        if (courseId != null) params.put("courseId", courseId);
        return params;
    }

    private String courseFilter(Long courseId) {
        return courseId == null ? "" : " AND c.id = :courseId";
    }

    private <T> PageResponse<T> findPage(Class<T> type, String select, String count,
                                         String from, String order, Map<String, Object> params,
                                         int page, int size) {
        TypedQuery<T> data = entityManager.createQuery(select + " " + from + order, type);
        TypedQuery<Long> total = entityManager.createQuery("SELECT " + count + " " + from, Long.class);
        // Some parameters (e.g. now) are needed only by the data projection.
        data.getParameters().forEach(p -> data.setParameter(p.getName(), params.get(p.getName())));
        total.getParameters().forEach(p -> total.setParameter(p.getName(), params.get(p.getName())));
        var rows = data.setFirstResult(page * size).setMaxResults(size).getResultList();
        return PageResponse.from(new PageImpl<>(rows, PageRequest.of(page, size), total.getSingleResult()));
    }
}

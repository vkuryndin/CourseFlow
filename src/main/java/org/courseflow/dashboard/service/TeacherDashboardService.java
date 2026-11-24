
package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;

import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.catalog.entity.CourseStatus;

import org.courseflow.dashboard.dto.TeacherDashboardResponse;
import org.courseflow.dashboard.dto.TeacherDashboardResponse.*;

import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.entity.SubmissionStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;
import java.util.List;

@Service
public class TeacherDashboardService {

    private static final int COURSE_LIMIT = 5;
    private static final int EVENT_LIMIT = 10;

    private final EntityManager entityManager;

    public TeacherDashboardService(
            EntityManager entityManager
    ) {
        this.entityManager = entityManager;
    }

    private record CourseCounts(
            long total,
            long active,
            long archived
    ) {
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TeacherDashboardResponse getDashboard(
            Long teacherId
    ) {
        Instant now = Instant.now();

        CourseCounts courseCounts =
                countCourses(teacherId);

        return new TeacherDashboardResponse(
                courseCounts.total(),
                courseCounts.active(),
                courseCounts.archived(),
                countActiveStudents(teacherId),
                countPendingSubmissions(teacherId),
                findCourses(teacherId),
                findPendingSubmissions(teacherId),
                findUpcomingSessions(teacherId, now),
                findRecentQuizResults(teacherId)
        );
    }

    private CourseCounts countCourses(Long teacherId) {

        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT c.status, COUNT(c)
                        FROM Course c
                        WHERE c.teacher.id = :teacherId
                        GROUP BY c.status
                        """,
                        Object[].class
                )
                .setParameter("teacherId", teacherId)
                .getResultList();

        long active = 0;
        long archived = 0;

        for (Object[] row : rows) {

            CourseStatus status = (CourseStatus) row[0];
            long count = (Long) row[1];

            if (status == CourseStatus.ACTIVE) {
                active = count;
            } else if (status == CourseStatus.ARCHIVED) {
                archived = count;
            }
        }

        return new CourseCounts(
                active + archived,
                active,
                archived
        );
    }

    private long countActiveStudents(Long teacherId) {

        return entityManager.createQuery(
                        """
                        SELECT COUNT(DISTINCT e.student.id)
                        FROM Enrollment e
                        WHERE e.course.teacher.id = :teacherId
                          AND e.course.status = :courseStatus
                          AND e.status = :enrollmentStatus
                        """,
                        Long.class
                )
                .setParameter("teacherId", teacherId)
                .setParameter(
                        "courseStatus",
                        CourseStatus.ACTIVE
                )
                .setParameter(
                        "enrollmentStatus",
                        EnrollmentStatus.ACTIVE
                )
                .getSingleResult();
    }

    private long countPendingSubmissions(Long teacherId) {

        return entityManager.createQuery(
                        """
                        SELECT COUNT(s)
                        FROM Submission s
                        JOIN s.assignment a
                        JOIN a.lesson l
                        JOIN l.module m
                        JOIN m.course c
                        WHERE c.teacher.id = :teacherId
                          AND c.status = :courseStatus
                          AND s.status = :submissionStatus
                        """,
                        Long.class
                )
                .setParameter("teacherId", teacherId)
                .setParameter(
                        "courseStatus",
                        CourseStatus.ACTIVE
                )
                .setParameter(
                        "submissionStatus",
                        SubmissionStatus.SUBMITTED
                )
                .getSingleResult();
    }

    private List<CourseSummary> findCourses(
            Long teacherId
    ) {
        return entityManager.createQuery(
                        """
                        SELECT c.id,
                               c.title,
                               c.status,
                               COUNT(e.id)
                        FROM Course c
                        LEFT JOIN Enrollment e
                            ON e.course.id = c.id
                           AND e.status = :enrollmentStatus
                        WHERE c.teacher.id = :teacherId
                        GROUP BY c.id,
                                 c.title,
                                 c.status
                        ORDER BY c.title ASC,
                                 c.id ASC
                        """,
                        CourseSummary.class
                )
                .setParameter("teacherId", teacherId)
                .setParameter(
                        "enrollmentStatus",
                        EnrollmentStatus.ACTIVE
                )
                .setMaxResults(COURSE_LIMIT)
                .getResultList();
    }

    private List<PendingSubmission> findPendingSubmissions(
            Long teacherId
    ) {
        return entityManager.createQuery(
                        """
                        SELECT s.id,
                               a.id,
                               l.id,
                               a.title,
                               c.id,
                               c.title,
                               u.id,
                               u.displayName,
                               s.submittedAt,
                               a.maxScore
                        FROM Submission s
                        JOIN s.assignment a
                        JOIN s.student u
                        JOIN a.lesson l
                        JOIN l.module m
                        JOIN m.course c
                        WHERE c.teacher.id = :teacherId
                          AND c.status = :courseStatus
                          AND s.status = :submissionStatus
                        ORDER BY s.submittedAt ASC,
                                 s.id ASC
                        """,
                        PendingSubmission.class
                )
                .setParameter("teacherId", teacherId)
                .setParameter(
                        "courseStatus",
                        CourseStatus.ACTIVE
                )
                .setParameter(
                        "submissionStatus",
                        SubmissionStatus.SUBMITTED
                )
                .setMaxResults(EVENT_LIMIT)
                .getResultList();
    }

    private List<UpcomingSession> findUpcomingSessions(
            Long teacherId,
            Instant now
    ) {
        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT s.id,
                               c.id,
                               c.title,
                               l.id,
                               l.title,
                               s.startsAt,
                               s.endsAt,
                               s.format,
                               s.meetingUrl
                        FROM LessonSession s
                        JOIN s.lesson l
                        JOIN l.module m
                        JOIN m.course c
                        WHERE c.teacher.id = :teacherId
                          AND c.status = :courseStatus
                          AND s.startsAt >= :now
                        ORDER BY s.startsAt ASC,
                                 s.id ASC
                        """,
                        Object[].class
                )
                .setParameter("teacherId", teacherId)
                .setParameter("now", now)
                .setParameter(
                        "courseStatus",
                        CourseStatus.ACTIVE
                )
                .setMaxResults(EVENT_LIMIT)
                .getResultList();

        return rows.stream()
                .map(row -> new UpcomingSession(
                        (Long) row[0],
                        (Long) row[1],
                        (String) row[2],
                        (Long) row[3],
                        (String) row[4],
                        (Instant) row[5],
                        (Instant) row[6],
                        row[7].toString(),
                        (String) row[8]
                ))
                .toList();
    }

    private List<RecentQuizResult> findRecentQuizResults(
            Long teacherId
    ) {
        return entityManager.createQuery(
                        """
                        SELECT s.id,
                               q.id,
                               q.title,
                               c.id,
                               c.title,
                               u.id,
                               u.displayName,
                               s.attemptNumber,
                               s.score,
                               s.finishedAt
                        FROM QuizSubmission s
                        JOIN s.quiz q
                        JOIN s.student u
                        JOIN q.module m
                        JOIN m.course c
                        WHERE c.teacher.id = :teacherId
                          AND s.status = :submissionStatus
                        ORDER BY s.finishedAt DESC,
                                 s.id DESC
                        """,
                        RecentQuizResult.class
                )
                .setParameter("teacherId", teacherId)
                .setParameter(
                        "submissionStatus",
                        QuizSubmissionStatus.SUBMITTED
                )
                .setMaxResults(EVENT_LIMIT)
                .getResultList();
    }
}


package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;

import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.PageResponse;

import org.courseflow.dashboard.dto.StudentDashboardResponse;
import org.courseflow.dashboard.dto.StudentDashboardResponse.UpcomingSession;
import org.courseflow.dashboard.dto.StudentDashboardResponse.UpcomingDeadline;

import org.courseflow.learning.dto.CourseProgressResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.ProgressService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class StudentDashboardService {

    private static final int COURSE_LIMIT = 5;
    private static final int EVENT_LIMIT = 10;

    private final EntityManager entityManager;
    private final ProgressService progressService;

    public StudentDashboardService(
            EntityManager entityManager,
            ProgressService progressService
    ) {
        this.entityManager = entityManager;
        this.progressService = progressService;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse getDashboard(
            Long studentId
    ) {
        Instant now = Instant.now();

        long activeCourseCount =
                countActiveCourses(studentId);

        PageResponse<CourseProgressResponse> courses =
                progressService.getMyProgress(
                        studentId,
                        null,
                        EnrollmentStatus.ACTIVE,
                        0,
                        COURSE_LIMIT,
                        "courseTitle",
                        "asc"
                );

        List<UpcomingSession> upcomingSessions =
                findUpcomingSessions(
                        studentId,
                        now
                );

        List<UpcomingDeadline> upcomingDeadlines =
                findUpcomingDeadlines(
                        studentId,
                        now
                );

        return new StudentDashboardResponse(
                activeCourseCount,
                courses,
                upcomingSessions,
                upcomingDeadlines
        );
    }

    private long countActiveCourses(
            Long studentId
    ) {
        return entityManager.createQuery(
                        """
                        SELECT COUNT(e)
                        FROM Enrollment e
                        WHERE e.student.id = :studentId
                          AND e.status = :enrollmentStatus
                          AND e.course.status = :courseStatus
                        """,
                        Long.class
                )
                .setParameter("studentId", studentId)
                .setParameter(
                        "enrollmentStatus",
                        EnrollmentStatus.ACTIVE
                )
                .setParameter(
                        "courseStatus",
                        CourseStatus.ACTIVE
                )
                .getSingleResult();
    }

    private List<UpcomingSession> findUpcomingSessions(
            Long studentId,
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
                        WHERE c.status = :courseStatus
                          AND s.startsAt >= :now
                          AND EXISTS (
                              SELECT e.id
                              FROM Enrollment e
                              WHERE e.course.id = c.id
                                AND e.student.id = :studentId
                                AND e.status = :enrollmentStatus
                          )
                        ORDER BY s.startsAt ASC,
                                 s.id ASC
                        """,
                        Object[].class
                )
                .setParameter("studentId", studentId)
                .setParameter("now", now)
                .setParameter(
                        "enrollmentStatus",
                        EnrollmentStatus.ACTIVE
                )
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

    private List<UpcomingDeadline> findUpcomingDeadlines(
            Long studentId,
            Instant now
    ) {
        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT a.id,
                               c.id,
                               c.title,
                               l.id,
                               a.title,
                               a.maxScore,
                               a.dueAt
                        FROM Assignment a
                        JOIN a.lesson l
                        JOIN l.module m
                        JOIN m.course c
                        WHERE c.status = :courseStatus
                          AND a.dueAt > :now
                          AND EXISTS (
                              SELECT e.id
                              FROM Enrollment e
                              WHERE e.course.id = c.id
                                AND e.student.id = :studentId
                                AND e.status = :enrollmentStatus
                          )
                          AND NOT EXISTS (
                              SELECT s.id
                              FROM Submission s
                              WHERE s.assignment.id = a.id
                                AND s.student.id = :studentId
                          )
                        ORDER BY a.dueAt ASC,
                                 a.id ASC
                        """,
                        Object[].class
                )
                .setParameter("studentId", studentId)
                .setParameter("now", now)
                .setParameter(
                        "enrollmentStatus",
                        EnrollmentStatus.ACTIVE
                )
                .setParameter(
                        "courseStatus",
                        CourseStatus.ACTIVE
                )
                .setMaxResults(EVENT_LIMIT)
                .getResultList();

        return rows.stream()
                .map(row -> new UpcomingDeadline(
                        (Long) row[0],
                        (Long) row[1],
                        (String) row[2],
                        (Long) row[3],
                        (String) row[4],
                        (Integer) row[5],
                        (Instant) row[6]
                ))
                .toList();
    }
}

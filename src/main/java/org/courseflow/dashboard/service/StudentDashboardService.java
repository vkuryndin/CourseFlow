package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.dashboard.dto.StudentDashboardResponse;
import org.courseflow.dashboard.dto.StudentDashboardResponse.UpcomingDeadline;
import org.courseflow.dashboard.dto.UpcomingSession;
import org.courseflow.learning.api.ProgressQuery;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDashboardService {

  private final Clock clock;
  private final UpcomingSessions upcomingSessionsService;

  private static final int COURSE_LIMIT = 5;
  private static final int EVENT_LIMIT = 10;

  private final EntityManager entityManager;
  private final ProgressQuery progressService;

  public StudentDashboardService(
      EntityManager entityManager,
      ProgressQuery progressService,
      Clock clock,
      UpcomingSessions upcomingSessionsService) {
    this.clock = clock;
    this.upcomingSessionsService = upcomingSessionsService;
    this.entityManager = entityManager;
    this.progressService = progressService;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public StudentDashboardResponse getDashboard(Long studentId) {
    Instant now = clock.instant();

    PageResponse<CourseProgressSummaryResponse> courses =
        progressService.getDashboardProgress(studentId, COURSE_LIMIT, now);

    List<UpcomingSession> upcomingSessions =
        upcomingSessionsService.forStudent(studentId, now, EVENT_LIMIT);

    List<UpcomingDeadline> upcomingDeadlines = findUpcomingDeadlines(studentId, now);

    return new StudentDashboardResponse(
        courses.totalElements(), courses, upcomingSessions, upcomingDeadlines);
  }

  private List<UpcomingDeadline> findUpcomingDeadlines(Long studentId, Instant now) {
    return entityManager
        .createQuery(
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
            UpcomingDeadline.class)
        .setParameter("studentId", studentId)
        .setParameter("now", now)
        .setParameter("enrollmentStatus", EnrollmentStatus.ACTIVE)
        .setParameter("courseStatus", CourseStatus.ACTIVE)
        .setMaxResults(EVENT_LIMIT)
        .getResultList();
  }
}

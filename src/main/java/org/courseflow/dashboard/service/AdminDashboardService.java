package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.dashboard.dto.AdminDashboardResponse;
import org.courseflow.dashboard.dto.AdminDashboardResponse.CourseStatistics;
import org.courseflow.dashboard.dto.AdminDashboardResponse.EnrollmentStatistics;
import org.courseflow.dashboard.dto.AdminDashboardResponse.RoleStatistics;
import org.courseflow.dashboard.dto.AdminDashboardResponse.UserStatistics;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {
  public record UserCount(UserStatus status, Long count) {}

  public record RoleCount(UserRole role, Long count) {}

  public record CourseCount(CourseStatus status, Long count) {}

  public record EnrollmentCount(EnrollmentStatus status, Long count) {}

  private final EntityManager entityManager;

  public AdminDashboardService(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AdminDashboardResponse getDashboard() {

    return new AdminDashboardResponse(
        getUserStatistics(), getRoleStatistics(), getCourseStatistics(), getEnrollmentStatistics());
  }

  private UserStatistics getUserStatistics() {

    List<UserCount> rows =
        entityManager
            .createQuery(
                """
                        SELECT u.status,
                               COUNT(u)
                        FROM User u
                        GROUP BY u.status
                        """,
                UserCount.class)
            .getResultList();

    long active = countFor(rows, UserStatus.ACTIVE, UserCount::status, UserCount::count);

    long inactive = countFor(rows, UserStatus.INACTIVE, UserCount::status, UserCount::count);

    long deleted = countFor(rows, UserStatus.DELETED, UserCount::status, UserCount::count);

    return new UserStatistics(active + inactive + deleted, active, inactive, deleted);
  }

  private RoleStatistics getRoleStatistics() {

    List<RoleCount> rows =
        entityManager
            .createQuery(
                """
                        SELECT r,
                               COUNT(DISTINCT u.id)
                        FROM User u
                        JOIN u.roles r
                        WHERE u.status = :status
                        GROUP BY r
                        """,
                RoleCount.class)
            .setParameter("status", UserStatus.ACTIVE)
            .getResultList();

    return new RoleStatistics(
        countFor(rows, UserRole.STUDENT, RoleCount::role, RoleCount::count),
        countFor(rows, UserRole.TEACHER, RoleCount::role, RoleCount::count),
        countFor(rows, UserRole.ADMIN, RoleCount::role, RoleCount::count));
  }

  private CourseStatistics getCourseStatistics() {

    List<CourseCount> rows =
        entityManager
            .createQuery(
                """
                        SELECT c.status,
                               COUNT(c)
                        FROM Course c
                        GROUP BY c.status
                        """,
                CourseCount.class)
            .getResultList();

    long active = countFor(rows, CourseStatus.ACTIVE, CourseCount::status, CourseCount::count);

    long archived = countFor(rows, CourseStatus.ARCHIVED, CourseCount::status, CourseCount::count);

    return new CourseStatistics(active + archived, active, archived);
  }

  private EnrollmentStatistics getEnrollmentStatistics() {

    List<EnrollmentCount> rows =
        entityManager
            .createQuery(
                """
                        SELECT e.status,
                               COUNT(e)
                        FROM Enrollment e
                        GROUP BY e.status
                        """,
                EnrollmentCount.class)
            .getResultList();

    long active =
        countFor(rows, EnrollmentStatus.ACTIVE, EnrollmentCount::status, EnrollmentCount::count);

    long withdrawn =
        countFor(rows, EnrollmentStatus.WITHDRAWN, EnrollmentCount::status, EnrollmentCount::count);

    long activeOnActiveCourses =
        entityManager
            .createQuery(
                """
                                SELECT COUNT(e)
                                FROM Enrollment e
                                WHERE e.status = :enrollmentStatus
                                  AND e.course.status = :courseStatus
                                """,
                Long.class)
            .setParameter("enrollmentStatus", EnrollmentStatus.ACTIVE)
            .setParameter("courseStatus", CourseStatus.ACTIVE)
            .getSingleResult();

    return new EnrollmentStatistics(active + withdrawn, active, withdrawn, activeOnActiveCourses);
  }

  private <T, E extends Enum<E>> long countFor(
      List<T> rows,
      E status,
      java.util.function.Function<T, E> key,
      java.util.function.ToLongFunction<T> count) {
    return rows.stream()
        .filter(row -> status.equals(key.apply(row)))
        .mapToLong(count)
        .findFirst()
        .orElse(0L);
  }
}

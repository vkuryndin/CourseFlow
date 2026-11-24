
package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;

import org.courseflow.catalog.entity.CourseStatus;

import org.courseflow.dashboard.dto.AdminDashboardResponse;
import org.courseflow.dashboard.dto.AdminDashboardResponse.*;

import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;

import org.courseflow.learning.entity.EnrollmentStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;

@Service
public class AdminDashboardService {

    private final EntityManager entityManager;

    public AdminDashboardService(
            EntityManager entityManager
    ) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDashboardResponse getDashboard() {

        return new AdminDashboardResponse(
                getUserStatistics(),
                getRoleStatistics(),
                getCourseStatistics(),
                getEnrollmentStatistics()
        );
    }

    private UserStatistics getUserStatistics() {

        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT u.status,
                               COUNT(u)
                        FROM User u
                        GROUP BY u.status
                        """,
                        Object[].class
                )
                .getResultList();

        long active = countFor(
                rows,
                UserStatus.ACTIVE
        );

        long inactive = countFor(
                rows,
                UserStatus.INACTIVE
        );

        long deleted = countFor(
                rows,
                UserStatus.DELETED
        );

        return new UserStatistics(
                active + inactive + deleted,
                active,
                inactive,
                deleted
        );
    }

    private RoleStatistics getRoleStatistics() {

        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT r,
                               COUNT(DISTINCT u.id)
                        FROM User u
                        JOIN u.roles r
                        WHERE u.status = :status
                        GROUP BY r
                        """,
                        Object[].class
                )
                .setParameter(
                        "status",
                        UserStatus.ACTIVE
                )
                .getResultList();

        return new RoleStatistics(
                countFor(rows, UserRole.STUDENT),
                countFor(rows, UserRole.TEACHER),
                countFor(rows, UserRole.ADMIN)
        );
    }

    private CourseStatistics getCourseStatistics() {

        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT c.status,
                               COUNT(c)
                        FROM Course c
                        GROUP BY c.status
                        """,
                        Object[].class
                )
                .getResultList();

        long active = countFor(
                rows,
                CourseStatus.ACTIVE
        );

        long archived = countFor(
                rows,
                CourseStatus.ARCHIVED
        );

        return new CourseStatistics(
                active + archived,
                active,
                archived
        );
    }

    private EnrollmentStatistics
    getEnrollmentStatistics() {

        List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT e.status,
                               COUNT(e)
                        FROM Enrollment e
                        GROUP BY e.status
                        """,
                        Object[].class
                )
                .getResultList();

        long active = countFor(
                rows,
                EnrollmentStatus.ACTIVE
        );

        long withdrawn = countFor(
                rows,
                EnrollmentStatus.WITHDRAWN
        );

        long activeOnActiveCourses =
                entityManager.createQuery(
                                """
                                SELECT COUNT(e)
                                FROM Enrollment e
                                WHERE e.status = :enrollmentStatus
                                  AND e.course.status = :courseStatus
                                """,
                                Long.class
                        )
                        .setParameter(
                                "enrollmentStatus",
                                EnrollmentStatus.ACTIVE
                        )
                        .setParameter(
                                "courseStatus",
                                CourseStatus.ACTIVE
                        )
                        .getSingleResult();

        return new EnrollmentStatistics(
                active + withdrawn,
                active,
                withdrawn,
                activeOnActiveCourses
        );
    }

    private long countFor(
            List<Object[]> rows,
            Enum<?> status
    ) {
        return rows.stream()
                .filter(row -> status.equals(row[0]))
                .mapToLong(row -> (Long) row[1])
                .findFirst()
                .orElse(0L);
    }
}

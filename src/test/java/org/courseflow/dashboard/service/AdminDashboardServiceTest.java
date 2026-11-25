package org.courseflow.dashboard.service;

import static org.courseflow.dashboard.service.AdminDashboardService.CourseCount;
import static org.courseflow.dashboard.service.AdminDashboardService.EnrollmentCount;
import static org.courseflow.dashboard.service.AdminDashboardService.RoleCount;
import static org.courseflow.dashboard.service.AdminDashboardService.UserCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.junit.jupiter.api.Test;

class AdminDashboardServiceTest {
  private final EntityManager em = mock(EntityManager.class);

  @SuppressWarnings("unchecked")
  private <T> TypedQuery<T> query(Class<T> type, List<T> rows) {
    TypedQuery<T> q = mock(TypedQuery.class);
    when(em.createQuery(anyString(), eq(type))).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.getResultList()).thenReturn(rows);
    return q;
  }

  @Test
  void aggregatesCountsAndMissingGroupsAsZero() {
    query(
        UserCount.class,
        List.of(new UserCount(UserStatus.ACTIVE, 5L), new UserCount(UserStatus.DELETED, 2L)));
    query(
        RoleCount.class,
        List.of(new RoleCount(UserRole.STUDENT, 4L), new RoleCount(UserRole.TEACHER, 2L)));
    query(
        CourseCount.class,
        List.of(
            new CourseCount(CourseStatus.ACTIVE, 3L), new CourseCount(CourseStatus.ARCHIVED, 1L)));
    query(
        EnrollmentCount.class,
        List.of(
            new EnrollmentCount(EnrollmentStatus.ACTIVE, 6L),
            new EnrollmentCount(EnrollmentStatus.WITHDRAWN, 2L)));
    var count = query(Long.class, List.of());
    when(count.getSingleResult()).thenReturn(5L);

    var response = new AdminDashboardService(em).getDashboard();

    assertEquals(7, response.users().total());
    assertEquals(0, response.users().inactive());
    assertEquals(4, response.roles().students());
    assertEquals(0, response.roles().admins());
    assertEquals(4, response.courses().total());
    assertEquals(8, response.enrollments().total());
    assertEquals(5, response.enrollments().activeOnActiveCourses());
    verify(count).setParameter("courseStatus", CourseStatus.ACTIVE);
  }
}

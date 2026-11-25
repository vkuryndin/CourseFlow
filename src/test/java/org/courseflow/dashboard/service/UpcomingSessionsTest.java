package org.courseflow.dashboard.service;

import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.junit.jupiter.api.Test;

class UpcomingSessionsTest {

  @SuppressWarnings("unchecked")
  @Test
  void studentAndTeacherUseDifferentScopeAndMapRows() {
    EntityManager em = mock(EntityManager.class);
    TypedQuery<UpcomingSessionRow> query = mock(TypedQuery.class);
    when(em.createQuery(anyString(), eq(UpcomingSessionRow.class))).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.setMaxResults(5)).thenReturn(query);
    when(query.getResultList())
        .thenReturn(
            List.of(
                new UpcomingSessionRow(
                    1L,
                    40L,
                    "Course",
                    60L,
                    "Lesson",
                    NOW.minusSeconds(30),
                    NOW.plusSeconds(30),
                    LessonSessionFormat.ONLINE,
                    "https://example.test")));
    UpcomingSessions service = new UpcomingSessions(em);

    assertEquals("ONLINE", service.forStudent(10L, NOW, 5).get(0).format());
    verify(query).setParameter("studentId", 10L);
    verify(query).setParameter("enrollmentStatus", EnrollmentStatus.ACTIVE);
    assertEquals(1, service.forTeacher(20L, NOW, 5).size());
    verify(query).setParameter("teacherId", 20L);
    verify(query, times(2)).setParameter("courseStatus", CourseStatus.ACTIVE);
    verify(query, times(2)).setParameter("now", NOW);
  }
}

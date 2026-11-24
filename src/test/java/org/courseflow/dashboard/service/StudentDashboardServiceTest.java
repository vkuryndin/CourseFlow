package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.dashboard.dto.StudentDashboardResponse.UpcomingDeadline;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.service.ProgressService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.QueryMocks.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentDashboardServiceTest {
    @Test
    void dashboardUsesTotalCourseCountAndLimitsUpcomingDeadlines() {
        EntityManager em = mock(EntityManager.class);
        ProgressService progress = mock(ProgressService.class);
        UpcomingSessions sessions = mock(UpcomingSessions.class);
        var service = new StudentDashboardService(em, progress, CLOCK, sessions);
        var summary = new CourseProgressSummaryResponse(40L, "ORM", CourseStatus.ACTIVE,
                EnrollmentStatus.ACTIVE, 4, 2, 50, 80, 3L, 1L, 1L);
        when(progress.getDashboardProgress(10L, 5, NOW))
                .thenReturn(new JpqlPages.Result<>(List.of(summary), 12L, 0, 5).response());
        var deadline = new UpcomingDeadline(70L, 40L, "ORM", 60L, "Task", 100, NOW.plusSeconds(3600));
        var q = query(em, UpcomingDeadline.class, List.of(deadline));

        var result = service.getDashboard(10L);

        assertEquals(12L, result.activeCourseCount());
        assertEquals(List.of(summary), result.courses().content());
        assertEquals(List.of(deadline), result.upcomingDeadlines());
        assertTrue(result.upcomingSessions().isEmpty());
        verify(sessions).forStudent(10L, NOW, 10);
        verify(q).setParameter("studentId", 10L);
        verify(q).setParameter("now", NOW);
        verify(q).setParameter("courseStatus", CourseStatus.ACTIVE);
        verify(q).setParameter("enrollmentStatus", EnrollmentStatus.ACTIVE);
        verify(q).setMaxResults(10);
    }
}

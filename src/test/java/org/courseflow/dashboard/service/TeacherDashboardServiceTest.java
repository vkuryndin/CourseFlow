package org.courseflow.dashboard.service;

import jakarta.persistence.EntityManager;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.dashboard.dto.TeacherDashboardResponse.CourseSummary;
import org.courseflow.dashboard.dto.TeacherDashboardResponse.PendingSubmission;
import org.courseflow.dashboard.dto.TeacherDashboardResponse.RecentQuizResult;
import org.courseflow.dashboard.service.TeacherDashboardService.CourseCountRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.QueryMocks.count;
import static org.courseflow.testing.QueryMocks.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherDashboardServiceTest {
    private final EntityManager em = mock(EntityManager.class);
    private final UpcomingSessions sessions = mock(UpcomingSessions.class);
    private final TeacherDashboardService service = new TeacherDashboardService(em, CLOCK, sessions);

    @Test
    void combinesCourseCountsPendingSubmissionsAndRecentResults() {
        query(em, CourseCountRow.class, List.of(new CourseCountRow(CourseStatus.ACTIVE, 3L),
                new CourseCountRow(CourseStatus.ARCHIVED, 2L)));
        var counts = count(em, 12L);
        when(counts.getSingleResult()).thenReturn(12L, 4L);
        var summary = new CourseSummary(40L, "ORM", CourseStatus.ACTIVE, 12L);
        var pending = new PendingSubmission(1L, 70L, 60L, "Task", 40L, "ORM", 10L, "Alice", NOW, 100);
        var recent = new RecentQuizResult(2L, 80L, "Quiz", 40L, "ORM", 10L, "Alice", 1, 90, NOW);
        var c = query(em, CourseSummary.class, List.of(summary));
        var p = query(em, PendingSubmission.class, List.of(pending));
        var r = query(em, RecentQuizResult.class, List.of(recent));

        var dashboard = service.getDashboard(20L);

        assertEquals(5L, dashboard.totalCourseCount());
        assertEquals(3L, dashboard.activeCourseCount());
        assertEquals(2L, dashboard.archivedCourseCount());
        assertEquals(12L, dashboard.activeStudentCount());
        assertEquals(4L, dashboard.pendingSubmissionCount());
        assertEquals(List.of(summary), dashboard.courses());
        assertEquals(List.of(pending), dashboard.pendingSubmissions());
        assertEquals(List.of(recent), dashboard.recentQuizResults());
        verify(c).setMaxResults(5);
        verify(p).setMaxResults(10);
        verify(r).setMaxResults(10);
        verify(sessions).forTeacher(20L, NOW, 10);
    }

    @Test
    void teacherWithoutCoursesHasZeroCountsAndEmptyLists() {
        query(em, CourseCountRow.class, List.of());
        count(em, 0L);
        query(em, CourseSummary.class, List.of());
        query(em, PendingSubmission.class, List.of());
        query(em, RecentQuizResult.class, List.of());
        var dashboard = service.getDashboard(20L);
        assertEquals(0L, dashboard.totalCourseCount());
        assertEquals(0L, dashboard.pendingSubmissionCount());
        assertTrue(dashboard.courses().isEmpty());
        assertTrue(dashboard.recentQuizResults().isEmpty());
    }
}

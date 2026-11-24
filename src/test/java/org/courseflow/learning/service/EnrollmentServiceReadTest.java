package org.courseflow.learning.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class EnrollmentServiceReadTest {
    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final EnrollmentService service = new EnrollmentService(repository, guard, mock(StudentAccess.class), CLOCK);
    private final Enrollment enrollment = id(Enrollment.createForStudent(student(), course(), NOW), 90);
    private final PageRequest page = PageRequest.of(0, 10, Sort.by("id"));

    @Test
    void getExistingEnrollment() {
        when(repository.findByStudent_IdAndCourse_Id(10L, 40L)).thenReturn(Optional.of(enrollment));
        assertEquals(90L, service.getMyEnrollment(40L, 10L).id());
    }

    @Test
    void courseEnrollmentsAllStatuses() {
        when(guard.find(40L)).thenReturn(enrollment.getCourse());
        when(repository.findAllByCourse_Id(40L, page)).thenReturn(new PageImpl<>(List.of(enrollment), page, 1));
        assertEquals("Student", service.getCourseEnrollments(40L, 20L, false, null, 0, 10, "id", "asc").content().get(0).studentName());
    }

    @Test
    void courseEnrollmentsFilterStatus() {
        when(guard.find(40L)).thenReturn(enrollment.getCourse());
        when(repository.findAllByCourse_IdAndStatus(40L, EnrollmentStatus.ACTIVE, page)).thenReturn(new PageImpl<>(List.of(enrollment), page, 1));
        assertEquals(1L, service.getCourseEnrollments(40L, 20L, false, EnrollmentStatus.ACTIVE, 0, 10, "id", "asc").totalElements());
    }

    @Test
    void studentEnrollmentsAllStatuses() {
        when(repository.findAllByStudent_Id(10L, page)).thenReturn(new PageImpl<>(List.of(enrollment), page, 1));
        assertEquals(40L, service.getMyEnrollments(10L, null, 0, 10, "id", "asc").content().get(0).courseId());
    }
}

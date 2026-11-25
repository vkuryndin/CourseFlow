package org.courseflow.learning.service;

import java.time.Clock;
import java.util.Set;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.dto.EnrollmentResponse;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

  private final Clock clock;

  private final EnrollmentRepository enrollmentRepository;
  private final CourseGuard courseGuard;
  private final StudentAccess studentAccess;

  public EnrollmentService(
      EnrollmentRepository enrollmentRepository,
      CourseGuard courseGuard,
      StudentAccess studentAccess,
      Clock clock) {
    this.clock = clock;
    this.enrollmentRepository = enrollmentRepository;
    this.courseGuard = courseGuard;
    this.studentAccess = studentAccess;
  }

  public record EnrollResult(EnrollmentResponse enrollment, boolean created) {}

  @Transactional
  public EnrollResult enroll(Long courseId, Long studentId) {
    Course course = courseGuard.lockShared(courseId);

    CourseAccessPolicy.requireActive(course, "Cannot enroll in an archived course");

    User student = studentAccess.lockForEnrollment(studentId);

    if (course.getTeacher().getId().equals(studentId)) {
      throw new BusinessConflictException("Teacher cannot enroll in own course");
    }

    var existing = enrollmentRepository.findByStudent_IdAndCourse_Id(studentId, courseId);

    if (existing.isPresent()) {
      Enrollment enrollment = existing.get();

      // ACTIVE remains ACTIVE.
      // WITHDRAWN becomes ACTIVE again.
      enrollment.reactivate();

      return new EnrollResult(EnrollmentResponse.from(enrollment), false);
    }

    Enrollment enrollment = Enrollment.createForStudent(student, course, clock.instant());

    Enrollment saved = enrollmentRepository.saveAndFlush(enrollment);

    return new EnrollResult(EnrollmentResponse.from(saved), true);
  }

  @Transactional
  public void withdraw(Long courseId, Long studentId) {
    courseGuard.lockShared(courseId);

    // Same course -> student lock order as enroll and assessment writes.
    // ADMIN may withdraw an inactive student; do not require ACTIVE here.
    studentAccess.lockExisting(studentId);

    Enrollment enrollment =
        enrollmentRepository
            .findByStudent_IdAndCourse_Id(studentId, courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));

    enrollment.withdraw(clock.instant());
  }

  @Transactional(readOnly = true)
  public EnrollmentResponse getMyEnrollment(Long courseId, Long studentId) {
    Enrollment enrollment =
        enrollmentRepository
            .findByStudent_IdAndCourse_Id(studentId, courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));

    return EnrollmentResponse.from(enrollment);
  }

  @Transactional(readOnly = true)
  public PageResponse<EnrollmentListItemResponse> getCourseEnrollments(
      Long courseId,
      Long currentUserId,
      boolean isAdmin,
      EnrollmentStatus status,
      int page,
      int size,
      String sortBy,
      String direction) {
    Course course = courseGuard.find(courseId);

    CourseAccessPolicy.requireOwnerOrAdmin(
        course, currentUserId, isAdmin, "Teacher can view enrollments only for own courses");

    PageRequest pageable = enrollmentPage(page, size, sortBy, direction);

    Page<Enrollment> enrollments;

    if (status == null) {
      enrollments = enrollmentRepository.findAllByCourse_Id(courseId, pageable);
    } else {
      enrollments = enrollmentRepository.findAllByCourse_IdAndStatus(courseId, status, pageable);
    }

    return PageResponse.from(enrollments.map(EnrollmentListItemResponse::from));
  }

  @Transactional(readOnly = true)
  public PageResponse<EnrollmentListItemResponse> getMyEnrollments(
      Long studentId,
      EnrollmentStatus status,
      int page,
      int size,
      String sortBy,
      String direction) {
    PageRequest pageable = enrollmentPage(page, size, sortBy, direction);

    Page<Enrollment> enrollments;

    if (status == null) {
      enrollments = enrollmentRepository.findAllByStudent_Id(studentId, pageable);
    } else {
      enrollments = enrollmentRepository.findAllByStudent_IdAndStatus(studentId, status, pageable);
    }

    return PageResponse.from(enrollments.map(EnrollmentListItemResponse::from));
  }

  private PageRequest enrollmentPage(int page, int size, String sortBy, String direction) {
    Pagination.validate(page, size);

    Pagination.requireSortField(
        sortBy, Set.of("id", "enrolledAt", "status"), "Unsupported sort field: " + sortBy);

    Sort sort = Pagination.stableSort(sortBy, direction);

    return PageRequest.of(page, size, sort);
  }
}

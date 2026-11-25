package org.courseflow.learning.api;

import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentAccess {
  private final EnrollmentRepository enrollments;

  public EnrollmentAccess(EnrollmentRepository enrollments) {
    this.enrollments = enrollments;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireActive(Long studentId, Long courseId, String message) {
    if (!hasActiveEnrollment(studentId, courseId)) {
      throw new AccessDeniedException(message);
    }
  }

  /** Both ACTIVE and WITHDRAWN enrollment records count as educational history. */
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public boolean hasEnrollmentHistory(Long courseId) {
    return enrollments.existsByCourse_Id(courseId);
  }

  /** Used by account lifecycle checks inside the caller's user-lock transaction. */
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public boolean hasActiveEnrollments(Long studentId) {
    return enrollments.existsByStudent_IdAndStatus(studentId, EnrollmentStatus.ACTIVE);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public boolean hasActiveEnrollment(Long studentId, Long courseId) {
    return enrollments.hasActiveEnrollment(studentId, courseId);
  }
}

package org.courseflow.learning.service;

import org.courseflow.learning.repository.EnrollmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentAccess {
    private final EnrollmentRepository enrollments;
    public EnrollmentAccess(EnrollmentRepository enrollments) { this.enrollments = enrollments; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActive(Long studentId, Long courseId, String message) {
        if (!enrollments.hasActiveEnrollment(studentId, courseId)) {
            throw new AccessDeniedException(message);
        }
    }
}

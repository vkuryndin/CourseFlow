package org.courseflow.identity.service;

import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StudentAccess {
  private final UserRepository users;

  public StudentAccess(UserRepository users) {
    this.users = users;
  }

  public User lockExisting(Long studentId) {
    return users
        .findByIdForUpdate(studentId)
        .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
  }

  public User lockActive(Long studentId) {
    User student = lockExisting(studentId);
    if (student.getStatus() != UserStatus.ACTIVE
        || !student.getRoles().contains(UserRole.STUDENT)) {
      throw new AccessDeniedException("Active STUDENT is required");
    }
    return student;
  }

  public User lockForEnrollment(Long studentId) {
    User student = lockExisting(studentId);
    if (student.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessConflictException("Student must be ACTIVE");
    }
    if (!student.getRoles().contains(UserRole.STUDENT)) {
      throw new AccessDeniedException("STUDENT role is required");
    }
    return student;
  }
}

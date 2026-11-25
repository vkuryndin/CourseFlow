package org.courseflow.catalog.api;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.exception.BusinessConflictException;
import org.springframework.security.access.AccessDeniedException;

/** Access rules for a course already loaded by the caller. */
public final class CourseAccessPolicy {
  private CourseAccessPolicy() {}

  public static void requireOwnerOrAdmin(
      Course course, Long userId, boolean admin, String message) {
    if (!admin && !course.getTeacher().getId().equals(userId)) {
      throw new AccessDeniedException(message);
    }
  }

  public static void requireActive(Course course, String message) {
    if (course.getStatus() != CourseStatus.ACTIVE) {
      throw new BusinessConflictException(message);
    }
  }
}

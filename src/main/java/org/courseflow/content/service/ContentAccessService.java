package org.courseflow.content.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentAccessService {

  private final CourseGuard courseGuard;
  private final LessonRepository lessonRepository;
  private final EnrollmentAccess enrollmentAccess;

  public ContentAccessService(
      CourseGuard courseGuard,
      LessonRepository lessonRepository,
      EnrollmentAccess enrollmentAccess) {
    this.courseGuard = courseGuard;
    this.lessonRepository = lessonRepository;
    this.enrollmentAccess = enrollmentAccess;
  }

  @Transactional(readOnly = true)
  public void checkCourseAccess(Long courseId, Long currentUserId, Authentication authentication) {
    Course course = courseGuard.find(courseId);

    if (CurrentUser.hasRole(authentication, "ADMIN")) {
      return;
    }

    if (CurrentUser.hasRole(authentication, "TEACHER")
        && course.getTeacher().getId().equals(currentUserId)) {
      return;
    }

    if (CurrentUser.hasRole(authentication, "STUDENT")) {
      boolean activeEnrollment = enrollmentAccess.hasActiveEnrollment(currentUserId, courseId);

      if (activeEnrollment) {
        return;
      }
    }

    throw new AccessDeniedException("Access to course materials is denied");
  }

  @Transactional(readOnly = true)
  public void checkLessonAccess(Long lessonId, Long currentUserId, Authentication authentication) {
    Lesson lesson =
        lessonRepository
            .findById(lessonId)
            .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));

    Long courseId = lesson.getModule().getCourse().getId();

    checkCourseAccess(courseId, currentUserId, authentication);
  }
}

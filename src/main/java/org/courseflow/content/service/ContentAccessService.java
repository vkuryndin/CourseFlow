
package org.courseflow.content.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.EnrollmentRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentAccessService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;

    public ContentAccessService(
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            EnrollmentRepository enrollmentRepository
    ) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public void checkCourseAccess(
            Long courseId,
            Long currentUserId,
            Authentication authentication
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (hasRole(authentication, "ADMIN")) {
            return;
        }

        if (hasRole(authentication, "TEACHER")
                && course.getTeacher().getId()
                .equals(currentUserId)) {
            return;
        }

        if (hasRole(authentication, "STUDENT")) {
            boolean activeEnrollment = enrollmentRepository
                    .findByStudent_IdAndCourse_Id(
                            currentUserId,
                            courseId
                    )
                    .map(enrollment ->
                            enrollment.getStatus()
                                    == EnrollmentStatus.ACTIVE
                    )
                    .orElse(false);

            if (activeEnrollment) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Access to course materials is denied"
        );
    }

    @Transactional(readOnly = true)
    public void checkLessonAccess(
            Long lessonId,
            Long currentUserId,
            Authentication authentication
    ) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found: " + lessonId
                        )
                );

        Long courseId = lesson.getModule()
                .getCourse()
                .getId();

        checkCourseAccess(
                courseId,
                currentUserId,
                authentication
        );
    }

    private boolean hasRole(
            Authentication authentication,
            String role
    ) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals("ROLE_" + role)
                );
    }
}

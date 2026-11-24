package org.courseflow.catalog.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseGuard {
    private final CourseRepository courses;
    public CourseGuard(CourseRepository courses) { this.courses = courses; }

    @Transactional(propagation = Propagation.MANDATORY)
    public Course lockEditable(Long courseId, Long userId, boolean admin, String archivedMessage) {
        Course course = lockOwned(courseId, userId, admin);
        CourseAccessPolicy.requireActive(course, archivedMessage);
        return course;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Course lockOwned(Long courseId, Long userId, boolean admin) {
        Course course = lock(courseId);
        CourseAccessPolicy.requireOwnerOrAdmin(course, userId, admin, "Teacher can manage only own courses");
        return course;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Course lock(Long courseId) {
        return courses.findByIdForUpdate(courseId).orElseThrow(() ->
                new ResourceNotFoundException("Course not found: " + courseId));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Course find(Long courseId) {
        return courses.findById(courseId).orElseThrow(() ->
                new ResourceNotFoundException("Course not found: " + courseId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Course lockShared(Long courseId) {
        return courses.findByIdForShare(courseId).orElseThrow(() ->
                new ResourceNotFoundException("Course not found: " + courseId));
    }
}

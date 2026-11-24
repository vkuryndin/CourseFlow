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
        Course course = courses.findByIdForUpdate(courseId).orElseThrow(() ->
                new ResourceNotFoundException("Course not found: " + courseId));
        CourseAccessPolicy.requireOwnerOrAdmin(course, userId, admin, "Teacher can manage only own courses");
        CourseAccessPolicy.requireActive(course, archivedMessage);
        return course;
    }
}

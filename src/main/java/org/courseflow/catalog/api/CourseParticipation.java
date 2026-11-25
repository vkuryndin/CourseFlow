package org.courseflow.catalog.api;

import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Catalog's user lifecycle queries. Caller must hold the user lock when changing account state. */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class CourseParticipation {
  private final CourseRepository courses;

  public CourseParticipation(CourseRepository courses) {
    this.courses = courses;
  }

  public boolean teachesAnyCourse(Long userId) {
    return courses.existsByTeacher_Id(userId);
  }

  public boolean teachesActiveCourse(Long userId) {
    return courses.existsByTeacher_IdAndStatus(userId, CourseStatus.ACTIVE);
  }
}

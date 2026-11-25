package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.category;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.courseflow.catalog.dto.CreateCourseRequest;
import org.courseflow.catalog.dto.UpdateCourseRequest;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.service.LessonScheduleChecks;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.review.api.ReviewAccess;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CourseServiceCoverageTest {
  private final CourseRepository courses = mock(CourseRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final UserDirectory users = mock(UserDirectory.class);
  private final CourseService service =
      new CourseService(
          courses,
          categories,
          users,
          mock(ContentStructure.class),
          mock(ReviewAccess.class),
          mock(EnrollmentAccess.class),
          mock(LessonScheduleChecks.class),
          CLOCK,
          guard);
  private final Course course = course();
  private final LocalDate date = LocalDate.of(2026, 2, 1);

  @Test
  void updatesDetailsWithoutChangingTeacher() {
    when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString())).thenReturn(course);
    when(categories.findByIdForShare(30L)).thenReturn(Optional.of(category()));
    var response =
        service.update(
            40L, new UpdateCourseRequest(" New ", " Details ", 30L, 20L, 45, date, 0L), 20L, false);
    assertEquals("New", response.title());
    assertEquals("Details", response.description());
    assertEquals(45, response.duration());
    assertEquals(20L, response.teacherId());
    verify(courses).flush();
  }

  @Test
  void unknownCategoryCannotBeUsed() {
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            service.create(
                new CreateCourseRequest("Title", "Text", 99L, 20L, 30, date), 20L, false));
    verifyNoInteractions(courses);
  }

  @Test
  void adminCanCreateForTeacher() {
    when(categories.findByIdForShare(30L)).thenReturn(Optional.of(category()));
    when(users.lockTeacherReference(20L)).thenReturn(teacher());
    when(courses.save(any(Course.class))).thenAnswer(i -> id(i.getArgument(0), 90));
    assertEquals(
        20L,
        service
            .create(new CreateCourseRequest("Title", "Text", 30L, 20L, 30, date), 1L, true)
            .teacherId());
  }

  @Test
  void deleteTranslatesConstraintViolation() {
    when(guard.lockOwned(40L, 20L, false)).thenReturn(course);
    doThrow(new DataIntegrityViolationException("related")).when(courses).flush();
    assertThrows(BusinessConflictException.class, () -> service.delete(40L, 20L, false));
  }
}

package org.courseflow.review.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.review.dto.CourseReviewRequest;
import org.courseflow.review.entity.CourseReview;
import org.courseflow.review.repository.CourseReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CourseReviewServiceTest {
  private final CourseReviewRepository repo = mock(CourseReviewRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final EnrollmentAccess enrollments = mock(EnrollmentAccess.class);
  private final UserDirectory users = mock(UserDirectory.class);
  private final StudentAccess students = mock(StudentAccess.class);
  private final CourseReviewService service =
      new CourseReviewService(repo, guard, enrollments, users, students, CLOCK);
  private final org.courseflow.catalog.entity.Course c = course();
  private final org.courseflow.identity.entity.User s = student();
  private final CourseReview review = id(new CourseReview(c, s, 4, "Original", NOW), 1);

  private void setup() {
    when(guard.lockShared(40L)).thenReturn(c);
    when(students.lockActive(10L)).thenReturn(s);
    when(users.requireStudentReference(10L)).thenReturn(s);
  }

  @Test
  void createTrimsCommentAndStoresRating() {
    setup();
    when(repo.saveAndFlush(any(CourseReview.class))).thenAnswer((i) -> id(i.getArgument(0), 1));

    var response = service.create(40L, 10L, new CourseReviewRequest(5, " Great "));

    assertEquals(5, response.rating());
    assertEquals("Great", response.comment());
  }

  @Test
  void duplicateReviewRejected() {
    setup();
    when(repo.findByCourse_IdAndStudent_Id(40L, 10L)).thenReturn(Optional.of(review));

    assertThrows(
        BusinessConflictException.class,
        () -> service.create(40L, 10L, new CourseReviewRequest(5, null)));
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void reviewCannotBeModifiedAfterWithdrawalOrArchive() {
    setup();
    doThrow(new AccessDeniedException("withdrawn"))
        .when(enrollments)
        .requireActive(eq(10L), eq(40L), anyString());

    assertThrows(AccessDeniedException.class, () -> service.delete(40L, 10L));
    doNothing().when(enrollments).requireActive(eq(10L), eq(40L), anyString());
    c.archive();
    assertThrows(
        BusinessConflictException.class,
        () -> service.update(40L, 10L, new CourseReviewRequest(5, null)));
    verifyNoInteractions(repo);
  }

  @Test
  void updateAndDeleteOwnReview() {
    setup();
    when(repo.findByCourse_IdAndStudent_Id(40L, 10L)).thenReturn(Optional.of(review));
    service.update(40L, 10L, new CourseReviewRequest(3, " "));

    assertEquals(3, review.getRating());
    assertNull(review.getComment());
    service.delete(40L, 10L);
    verify(repo).delete(review);
    verify(repo).flush();
  }

  @Test
  void missingReviewRejected() {
    setup();

    assertThrows(ResourceNotFoundException.class, () -> service.getMyReview(40L, 10L));
  }
}

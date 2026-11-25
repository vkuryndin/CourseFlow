package org.courseflow.review.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.common.api.PageResponse;
import org.courseflow.review.dto.CourseRatingResponse;
import org.courseflow.review.dto.CourseReviewRequest;
import org.courseflow.review.dto.CourseReviewResponse;
import org.courseflow.review.dto.PublicCourseReviewResponse;
import org.courseflow.review.service.CourseReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class CourseReviewControllerTest {
  private final CourseReviewService courseReviewService = mock(CourseReviewService.class);
  private final CourseReviewController controller = new CourseReviewController(courseReviewService);

  @Test
  void createDelegatesAndReturnsContract() {
    Long courseId = 101L;
    CourseReviewRequest request = mock(CourseReviewRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    CourseReviewResponse expected = mock(CourseReviewResponse.class);
    when(courseReviewService.create(courseId, 42L, request)).thenReturn(expected);

    var actual = controller.create(courseId, request, jwt);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals("/api/courses/101/reviews/me", actual.getHeaders().getLocation().toString());
    verify(courseReviewService).create(courseId, 42L, request);
  }

  @Test
  void updateDelegatesAndReturnsContract() {
    Long courseId = 101L;
    CourseReviewRequest request = mock(CourseReviewRequest.class);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    CourseReviewResponse expected = mock(CourseReviewResponse.class);
    when(courseReviewService.update(courseId, 42L, request)).thenReturn(expected);

    var actual = controller.update(courseId, request, jwt);

    assertSame(expected, actual);
    verify(courseReviewService).update(courseId, 42L, request);
  }

  @Test
  void deleteDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");

    var actual = controller.delete(courseId, jwt);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(courseReviewService).delete(courseId, 42L);
  }

  @Test
  void getMyReviewDelegatesAndReturnsContract() {
    Long courseId = 101L;
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    CourseReviewResponse expected = mock(CourseReviewResponse.class);
    when(courseReviewService.getMyReview(courseId, 42L)).thenReturn(expected);

    var actual = controller.getMyReview(courseId, jwt);

    assertSame(expected, actual);
    verify(courseReviewService).getMyReview(courseId, 42L);
  }

  @Test
  void findByCourseDelegatesAndReturnsContract() {
    Long courseId = 101L;
    int page = 1;
    int size = 15;
    String sortBy = "id";
    String direction = "desc";
    PageResponse<PublicCourseReviewResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(courseReviewService.findByCourse(courseId, page, size, sortBy, direction))
        .thenReturn(expected);

    var actual = controller.findByCourse(courseId, page, size, sortBy, direction);

    assertSame(expected, actual);
    verify(courseReviewService).findByCourse(courseId, page, size, sortBy, direction);
  }

  @Test
  void getRatingDelegatesAndReturnsContract() {
    Long courseId = 101L;
    CourseRatingResponse expected = mock(CourseRatingResponse.class);
    when(courseReviewService.getRating(courseId)).thenReturn(expected);

    var actual = controller.getRating(courseId);

    assertSame(expected, actual);
    verify(courseReviewService).getRating(courseId);
  }
}

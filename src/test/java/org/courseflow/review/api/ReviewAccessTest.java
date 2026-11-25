package org.courseflow.review.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.review.repository.CourseReviewRepository;
import org.junit.jupiter.api.Test;

class ReviewAccessTest {
  @Test
  void checksExistenceThroughOwningRepository() {
    var repository = mock(CourseReviewRepository.class);
    when(repository.existsByCourse_Id(10L)).thenReturn(true);
    var access = new ReviewAccess(repository);
    assertTrue(access.hasReviews(10L));
    assertFalse(access.hasReviews(11L));
    verify(repository).existsByCourse_Id(10L);
    verify(repository).existsByCourse_Id(11L);
  }
}

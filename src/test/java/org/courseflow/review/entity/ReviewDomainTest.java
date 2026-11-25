package org.courseflow.review.entity;

import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReviewDomainTest {

  @Test
  void reviewStoresBothLinksAndCanBeUpdated() {
    var c = course();
    var s = student();
    CourseReview r = new CourseReview(c, s, 1, "Original", NOW);

    assertSame(c, r.getCourse());
    assertSame(s, r.getStudent());
    assertEquals(NOW, r.getCreatedAt());
    r.update(5, "Updated");
    assertEquals(5, r.getRating());
    assertEquals("Updated", r.getComment());
  }

  @Test
  void ratingsOutsideRangeAreRejected() {
    CourseReview r = new CourseReview(course(), student(), 3, "Keep", NOW);
    for (Integer rating : Arrays.asList(null, 0, 6)) {
      assertThrows(IllegalArgumentException.class, () -> r.update(rating, "Invalid"));
      assertThrows(
          IllegalArgumentException.class,
          () -> new CourseReview(course(), student(), rating, null, NOW));
    }
    assertEquals(3, r.getRating());
    assertEquals("Keep", r.getComment());
  }
}

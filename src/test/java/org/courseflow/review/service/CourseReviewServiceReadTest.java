package org.courseflow.review.service;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.identity.service.StudentAccess;
import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.review.entity.CourseReview;
import org.courseflow.review.repository.CourseReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class CourseReviewServiceReadTest {
    private final CourseReviewRepository repository = mock(CourseReviewRepository.class);
    private final CourseGuard guard = mock(CourseGuard.class);
    private final CourseReviewService service = new CourseReviewService(repository, guard, mock(EnrollmentAccess.class), mock(UserDirectory.class), mock(StudentAccess.class), CLOCK);
    private final CourseReview review = id(new CourseReview(course(), student(), 5, "Good", NOW), 90);

    @Test
    void ownReviewIncludesRating() {
        when(repository.findByCourse_IdAndStudent_Id(40L, 10L)).thenReturn(Optional.of(review));
        assertEquals(5, service.getMyReview(40L, 10L).rating());
    }

    @Test
    void publicReviewMapsName() {
        var page = PageRequest.of(0, 10, Sort.by("id"));
        when(repository.findAllByCourse_Id(40L, page)).thenReturn(new PageImpl<>(List.of(review), page, 1));
        var result = service.findByCourse(40L, 0, 10, "id", "asc");
        assertEquals("Student", result.content().get(0).studentName());
        assertEquals("Good", result.content().get(0).comment());
        assertEquals(5, result.content().get(0).rating());
    }

    @Test
    void ratingWithReviews() {
        when(repository.countByCourse_Id(40L)).thenReturn(2L);
        when(repository.findAverageRatingByCourseId(40L)).thenReturn(4.5);
        var result = service.getRating(40L);
        assertEquals(2L, result.reviewCount());
        assertEquals(4.5, result.averageRating());
    }

    @Test
    void emptyRatingIsNull() {
        when(repository.findAverageRatingByCourseId(40L)).thenReturn(null);
        var result = service.getRating(40L);
        assertEquals(0L, result.reviewCount());
        assertNull(result.averageRating());
    }
}

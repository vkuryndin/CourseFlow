package org.courseflow.review.api;

import org.courseflow.review.repository.CourseReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle checks exposed by the owning module; the caller retains its parent lock. */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class ReviewAccess {
    private final CourseReviewRepository repository;

    public ReviewAccess(CourseReviewRepository repository) { this.repository = repository; }

    public boolean hasReviews(Long courseId) { return repository.existsByCourse_Id(courseId); }
}

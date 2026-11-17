
package org.courseflow.review.repository;

import org.courseflow.review.entity.CourseReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CourseReviewRepository
        extends JpaRepository<CourseReview, Long> {

    Optional<CourseReview> findByCourse_IdAndStudent_Id(
            Long courseId,
            Long studentId
    );

    Page<CourseReview> findAllByCourse_Id(
            Long courseId,
            Pageable pageable
    );

    Page<CourseReview> findAllByStudent_Id(
            Long studentId,
            Pageable pageable
    );

    boolean existsByCourse_Id(Long courseId);

    long countByCourse_Id(Long courseId);

    @Query("""
            select avg(r.rating)
            from CourseReview r
            where r.course.id = :courseId
            """)
    Double findAverageRatingByCourseId(
            @Param("courseId") Long courseId
    );
}

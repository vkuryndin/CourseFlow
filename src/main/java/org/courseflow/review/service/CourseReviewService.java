package org.courseflow.review.service;

import org.courseflow.learning.service.EnrollmentAccess;
import org.courseflow.identity.service.StudentAccess;
import java.time.Clock;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import org.courseflow.review.dto.PublicCourseReviewResponse;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.review.dto.CourseRatingResponse;
import org.courseflow.review.dto.CourseReviewRequest;
import org.courseflow.review.dto.CourseReviewResponse;
import org.courseflow.review.entity.CourseReview;
import org.courseflow.review.repository.CourseReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.Set;

@Service
public class CourseReviewService {

    private final Clock clock;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "rating", "createdAt");

    private final CourseReviewRepository courseReviewRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentAccess enrollmentAccess;
    private final UserRepository userRepository;
    private final StudentAccess studentAccess;

    public CourseReviewService(
            CourseReviewRepository courseReviewRepository,
            CourseRepository courseRepository,
            EnrollmentAccess enrollmentAccess,
            UserRepository userRepository,
            StudentAccess studentAccess,
            Clock clock
    ) {
        this.clock = clock;
        this.courseReviewRepository = courseReviewRepository;
        this.courseRepository = courseRepository;
        this.enrollmentAccess = enrollmentAccess;
        this.userRepository = userRepository;
        this.studentAccess = studentAccess;
    }

    @Transactional
    public CourseReviewResponse create(
            Long courseId,
            Long studentId,
            CourseReviewRequest request
    ) {
        Course course = requireReviewModificationAccess(
                courseId,
                studentId
        );

        if (courseReviewRepository
                .findByCourse_IdAndStudent_Id(
                        courseId,
                        studentId
                ).isPresent()) {

            throw new BusinessConflictException(
                    "Student has already reviewed this course"
            );
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Student not found: " + studentId
                        )
                );

        CourseReview review = new CourseReview(
                course,
                student,
                request.rating(),
                normalizeComment(request.comment()),
                clock.instant()
        );

        CourseReview saved =
                courseReviewRepository.saveAndFlush(review);

        return CourseReviewResponse.from(saved);
    }

    @Transactional
    public CourseReviewResponse update(
            Long courseId,
            Long studentId,
            CourseReviewRequest request
    ) {
        requireReviewModificationAccess(
                courseId,
                studentId
        );

        CourseReview review = findOwnReview(
                courseId,
                studentId
        );

        review.update(
                request.rating(),
                normalizeComment(request.comment())
        );

        return CourseReviewResponse.from(review);
    }

    @Transactional
    public void delete(
            Long courseId,
            Long studentId
    ) {
        requireReviewModificationAccess(
                courseId,
                studentId
        );

        CourseReview review = findOwnReview(
                courseId,
                studentId
        );

        courseReviewRepository.delete(review);
        courseReviewRepository.flush();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseReviewResponse getMyReview(
            Long courseId,
            Long studentId
    ) {
        requireCourseExists(courseId);

        return CourseReviewResponse.from(
                findOwnReview(courseId, studentId)
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<PublicCourseReviewResponse> findByCourse(
            Long courseId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        requireCourseExists(courseId);

        Pagination.validate(page, size);

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        }

        String normalizedDirection = Pagination.direction(direction);

        Sort.Direction sortDirection =
                normalizedDirection.equals("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort sort = Sort.by(sortDirection, sortBy);

        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        PageRequest pageable =
                PageRequest.of(page, size, sort);

        Page<PublicCourseReviewResponse> result =
                courseReviewRepository
                        .findAllByCourse_Id(
                                courseId,
                                pageable
                        )
                        .map(PublicCourseReviewResponse::from);

        return PageResponse.from(result);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourseRatingResponse getRating(Long courseId) {
        requireCourseExists(courseId);

        long reviewCount =
                courseReviewRepository
                        .countByCourse_Id(courseId);

        Double averageRating =
                courseReviewRepository
                        .findAverageRatingByCourseId(courseId);

        return new CourseRatingResponse(
                courseId,
                reviewCount,
                averageRating
        );
    }

    private Course requireReviewModificationAccess(
            Long courseId,
            Long studentId
    ) {
        Course course = courseRepository
                .findByIdForShare(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        // Serialize review changes and enrollment changes of the same student.
        User student = studentAccess.lockActive(studentId);

        if (course.getTeacher().getId().equals(studentId)) {
            throw new AccessDeniedException(
                    "Teacher cannot review own course"
            );
        }

        enrollmentAccess.requireActive(studentId, courseId, "Active enrollment is required to review course");

        CourseAccessPolicy.requireActive(course,
                "Cannot modify review in archived course");

        return course;
    }

    private CourseReview findOwnReview(
            Long courseId,
            Long studentId
    ) {
        return courseReviewRepository
                .findByCourse_IdAndStudent_Id(
                        courseId,
                        studentId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course review not found"
                        )
                );
    }

    private void requireCourseExists(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException(
                    "Course not found: " + courseId
            );
        }
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }

        return comment.trim();
    }
}

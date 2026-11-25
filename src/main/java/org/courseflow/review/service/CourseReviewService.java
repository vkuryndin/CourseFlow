package org.courseflow.review.service;

import java.time.Clock;
import java.util.Set;
import org.courseflow.catalog.api.CourseAccessPolicy;
import org.courseflow.catalog.api.CourseGuard;
import org.courseflow.catalog.entity.Course;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.api.StudentAccess;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.identity.entity.User;
import org.courseflow.learning.api.EnrollmentAccess;
import org.courseflow.review.dto.CourseRatingResponse;
import org.courseflow.review.dto.CourseReviewRequest;
import org.courseflow.review.dto.CourseReviewResponse;
import org.courseflow.review.dto.PublicCourseReviewResponse;
import org.courseflow.review.entity.CourseReview;
import org.courseflow.review.repository.CourseReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseReviewService {

  private final Clock clock;

  private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "rating", "createdAt");

  private final CourseReviewRepository courseReviewRepository;
  private final CourseGuard courseGuard;
  private final EnrollmentAccess enrollmentAccess;
  private final UserDirectory userDirectory;
  private final StudentAccess studentAccess;

  public CourseReviewService(
      CourseReviewRepository courseReviewRepository,
      CourseGuard courseGuard,
      EnrollmentAccess enrollmentAccess,
      UserDirectory userDirectory,
      StudentAccess studentAccess,
      Clock clock) {
    this.clock = clock;
    this.courseReviewRepository = courseReviewRepository;
    this.courseGuard = courseGuard;
    this.enrollmentAccess = enrollmentAccess;
    this.userDirectory = userDirectory;
    this.studentAccess = studentAccess;
  }

  @Transactional
  public CourseReviewResponse create(Long courseId, Long studentId, CourseReviewRequest request) {
    Course course = requireReviewModificationAccess(courseId, studentId);

    if (courseReviewRepository.findByCourse_IdAndStudent_Id(courseId, studentId).isPresent()) {

      throw new BusinessConflictException("Student has already reviewed this course");
    }

    User student = userDirectory.requireStudentReference(studentId);

    CourseReview review =
        new CourseReview(
            course,
            student,
            request.rating(),
            normalizeComment(request.comment()),
            clock.instant());

    CourseReview saved = courseReviewRepository.saveAndFlush(review);

    return CourseReviewResponse.from(saved);
  }

  @Transactional
  public CourseReviewResponse update(Long courseId, Long studentId, CourseReviewRequest request) {
    requireReviewModificationAccess(courseId, studentId);

    CourseReview review = findOwnReview(courseId, studentId);

    review.update(request.rating(), normalizeComment(request.comment()));

    return CourseReviewResponse.from(review);
  }

  @Transactional
  public void delete(Long courseId, Long studentId) {
    requireReviewModificationAccess(courseId, studentId);

    CourseReview review = findOwnReview(courseId, studentId);

    courseReviewRepository.delete(review);
    courseReviewRepository.flush();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CourseReviewResponse getMyReview(Long courseId, Long studentId) {
    requireCourseExists(courseId);

    return CourseReviewResponse.from(findOwnReview(courseId, studentId));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<PublicCourseReviewResponse> findByCourse(
      Long courseId, int page, int size, String sortBy, String direction) {
    requireCourseExists(courseId);

    Pagination.validate(page, size);

    Pagination.requireSortField(sortBy, ALLOWED_SORT_FIELDS, "Unsupported sort field: " + sortBy);

    Sort sort = Pagination.stableSort(sortBy, direction);

    PageRequest pageable = PageRequest.of(page, size, sort);

    Page<PublicCourseReviewResponse> result =
        courseReviewRepository
            .findAllByCourse_Id(courseId, pageable)
            .map(PublicCourseReviewResponse::from);

    return PageResponse.from(result);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CourseRatingResponse getRating(Long courseId) {
    requireCourseExists(courseId);

    long reviewCount = courseReviewRepository.countByCourse_Id(courseId);

    Double averageRating = courseReviewRepository.findAverageRatingByCourseId(courseId);

    return new CourseRatingResponse(courseId, reviewCount, averageRating);
  }

  private Course requireReviewModificationAccess(Long courseId, Long studentId) {
    Course course = courseGuard.lockShared(courseId);

    // Serialize review changes and enrollment changes of the same student.
    studentAccess.lockActive(studentId);

    if (course.getTeacher().getId().equals(studentId)) {
      throw new AccessDeniedException("Teacher cannot review own course");
    }

    enrollmentAccess.requireActive(
        studentId, courseId, "Active enrollment is required to review course");

    CourseAccessPolicy.requireActive(course, "Cannot modify review in archived course");

    return course;
  }

  private CourseReview findOwnReview(Long courseId, Long studentId) {
    return courseReviewRepository
        .findByCourse_IdAndStudent_Id(courseId, studentId)
        .orElseThrow(() -> new ResourceNotFoundException("Course review not found"));
  }

  private void requireCourseExists(Long courseId) {
    courseGuard.requireExists(courseId);
  }

  private String normalizeComment(String comment) {
    if (comment == null || comment.isBlank()) {
      return null;
    }

    return comment.trim();
  }
}

package org.courseflow.learning.service;

import java.time.Clock;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.dto.EnrollmentResponse;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.EnrollmentListItemResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Set;

@Service
public class EnrollmentService {

    private final Clock clock;

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.clock = clock;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    public record EnrollResult(
            EnrollmentResponse enrollment,
            boolean created
    ) {
    }

    @Transactional
    public EnrollResult enroll(
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

        CourseAccessPolicy.requireActive(course,
                "Cannot enroll in an archived course");

        User student = userRepository.findByIdForUpdate(studentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Student not found: " + studentId
                        )
                );

        if (student.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Student must be ACTIVE"
            );
        }

        if (!student.getRoles().contains(UserRole.STUDENT)) {
            throw new AccessDeniedException(
                    "STUDENT role is required"
            );
        }

        if (course.getTeacher().getId().equals(studentId)) {
            throw new BusinessConflictException(
                    "Teacher cannot enroll in own course"
            );
        }

        var existing = enrollmentRepository
                .findByStudent_IdAndCourse_Id(
                        studentId,
                        courseId
                );

        if (existing.isPresent()) {
            Enrollment enrollment = existing.get();

            // ACTIVE remains ACTIVE.
            // WITHDRAWN becomes ACTIVE again.
            enrollment.reactivate();

            return new EnrollResult(
                    EnrollmentResponse.from(enrollment),
                    false
            );
        }

        Enrollment enrollment =
                Enrollment.createForStudent(
                        student,
                        course,
                        clock.instant()
                );

        Enrollment saved =
                enrollmentRepository.saveAndFlush(enrollment);

        return new EnrollResult(
                EnrollmentResponse.from(saved),
                true
        );
    }

    @Transactional
    public void withdraw(
            Long courseId,
            Long studentId
    ) {
        courseRepository.findByIdForShare(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        // Same course -> student lock order as enroll and assessment writes.
        // ADMIN may withdraw an inactive student; do not require ACTIVE here.
        userRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + studentId));

        Enrollment enrollment = enrollmentRepository
                .findByStudent_IdAndCourse_Id(
                        studentId,
                        courseId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Enrollment not found"
                        )
                );

        enrollment.withdraw(clock.instant());
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getMyEnrollment(
            Long courseId,
            Long studentId
    ) {
        Enrollment enrollment = enrollmentRepository
                .findByStudent_IdAndCourse_Id(
                        studentId,
                        courseId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Enrollment not found"
                        )
                );

        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentListItemResponse> getCourseEnrollments(
            Long courseId,
            Long currentUserId,
            boolean isAdmin,
            EnrollmentStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Teacher can view enrollments only for own courses");

        PageRequest pageable = enrollmentPage(
                page, size, sortBy, direction
        );

        Page<Enrollment> enrollments;

        if (status == null) {
            enrollments = enrollmentRepository.findAllByCourse_Id(
                    courseId,
                    pageable
            );
        } else {
            enrollments =
                    enrollmentRepository.findAllByCourse_IdAndStatus(
                            courseId,
                            status,
                            pageable
                    );
        }

        return PageResponse.from(
                enrollments.map(EnrollmentListItemResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentListItemResponse> getMyEnrollments(
            Long studentId,
            EnrollmentStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        PageRequest pageable = enrollmentPage(
                page, size, sortBy, direction
        );

        Page<Enrollment> enrollments;

        if (status == null) {
            enrollments = enrollmentRepository.findAllByStudent_Id(
                    studentId,
                    pageable
            );
        } else {
            enrollments =
                    enrollmentRepository.findAllByStudent_IdAndStatus(
                            studentId,
                            status,
                            pageable
                    );
        }

        return PageResponse.from(
                enrollments.map(EnrollmentListItemResponse::from)
        );
    }

    private PageRequest enrollmentPage(
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Pagination.validate(page, size);

        if (!Set.of("id", "enrolledAt", "status")
                .contains(sortBy)) {
            throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        }

        String normalizedDirection = Pagination.direction(direction);

        Sort.Direction springDirection =
                normalizedDirection.equals("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort sort = Sort.by(springDirection, sortBy);

        // Stable ordering for pagination.
        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        return PageRequest.of(page, size, sort);
    }

}

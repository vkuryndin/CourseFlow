
package org.courseflow.learning.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
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

import java.util.Locale;
import java.util.Set;


import java.time.Instant;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            UserRepository userRepository
    ) {
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
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Cannot enroll in an archived course"
            );
        }

        User student = userRepository.findById(studentId)
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
                        Instant.now()
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
        courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

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

        enrollment.withdraw(Instant.now());
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

        if (!isAdmin
                && !course.getTeacher().getId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "Teacher can view enrollments only for own courses"
            );
        }

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
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException(
                    "Invalid pagination parameters"
            );
        }

        if (!Set.of("id", "enrolledAt", "status")
                .contains(sortBy)) {
            throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        }

        String normalizedDirection =
                direction.toLowerCase(Locale.ROOT);

        if (!normalizedDirection.equals("asc")
                && !normalizedDirection.equals("desc")) {
            throw new BadRequestException(
                    "Direction must be asc or desc"
            );
        }

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

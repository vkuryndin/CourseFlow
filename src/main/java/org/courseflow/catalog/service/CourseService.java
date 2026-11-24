package org.courseflow.catalog.service;

import org.courseflow.common.exception.EditVersion;
import java.time.Clock;
import org.courseflow.content.service.LessonScheduleChecks;
import org.courseflow.catalog.dto.CourseResponse;
import org.courseflow.catalog.dto.CreateCourseRequest;
import org.courseflow.catalog.dto.UpdateCourseRequest;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.api.UserDirectory;
import org.courseflow.learning.service.EnrollmentAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.review.api.ReviewAccess;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class CourseService {

    private final Clock clock;
    private final CourseGuard courseGuard;

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final UserDirectory userDirectory;
    private final ContentStructure contentStructure;
    private final ReviewAccess reviewAccess;
    private final EnrollmentAccess enrollmentAccess;
    private final LessonScheduleChecks scheduleChecks;

    public CourseService(
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            UserDirectory userDirectory,
            ContentStructure contentStructure,
            ReviewAccess reviewAccess,
            EnrollmentAccess enrollmentAccess,
            LessonScheduleChecks scheduleChecks,
            Clock clock,
            CourseGuard courseGuard
    ) {
        this.clock = clock;
        this.courseGuard = courseGuard;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userDirectory = userDirectory;
        this.contentStructure = contentStructure;
        this.reviewAccess = reviewAccess;
        this.enrollmentAccess = enrollmentAccess;
        this.scheduleChecks = scheduleChecks;
    }

    @Transactional
    public CourseResponse create(
            CreateCourseRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        if (!isAdmin
                && !currentUserId.equals(request.teacherId())) {
            throw new AccessDeniedException(
                    "Teacher can create only own courses"
            );
        }

        Category category =
                findCategory(request.categoryId());

        User teacher =
                findActiveTeacher(request.teacherId());

        Course course = new Course(
                request.title().trim(),
                request.description().trim(),
                category,
                teacher,
                request.duration(),
                request.startDate()
        );

        Course savedCourse = courseRepository.save(course);

        return CourseResponse.from(savedCourse);
    }

    @Transactional
    public CourseResponse update(
            Long courseId,
            UpdateCourseRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseGuard.lockEditable(courseId, currentUserId, isAdmin,
                "Cannot edit an archived course");

        // Keep the existing request field for compatibility, but prohibit
        // ownership changes outside the audited /teacher endpoint.
        if (!course.getTeacher().getId().equals(request.teacherId())) {
            if (!isAdmin) {
                throw new AccessDeniedException("Teacher cannot reassign a course");
            }
            throw new BusinessConflictException("Use the dedicated teacher reassignment endpoint");
        }

        Category category = findCategory(request.categoryId());

        EditVersion.require(request.version(), course.getVersion());

        course.updateDetails(
                request.title().trim(),
                request.description().trim(),
                category,
                request.duration(),
                request.startDate()
        );

        courseRepository.flush();
        return CourseResponse.from(course);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findByIdForShare(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found: " + categoryId
                        )
                );
    }

    // The target teacher has already been locked by findActiveTeacher.
    private void ensureScheduleCompatible(Course course, Long teacherId) {
        if (course.getStatus() == CourseStatus.ACTIVE && !course.getTeacher().getId().equals(teacherId)
                && scheduleChecks.hasReassignmentOverlap(course.getId(), teacherId, clock.instant())) {
            throw new BusinessConflictException("New teacher has overlapping scheduled lessons");
        }
    }

    private void ensureTeacherNotEnrolled(Long courseId, Long teacherId) {
        boolean enrolled = enrollmentAccess.hasActiveEnrollment(teacherId, courseId);
        if (enrolled) {
            throw new BusinessConflictException(
                    "Withdraw active enrollment before assigning the course teacher"
            );
        }
    }

    private User findActiveTeacher(Long teacherId) {
        User teacher = userDirectory.lockTeacherReference(teacherId);

        if (teacher.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "Teacher must be ACTIVE"
            );
        }

        if (!teacher.getRoles().contains(UserRole.TEACHER)) {
            throw new BusinessConflictException(
                    "User does not have TEACHER role"
            );
        }

        return teacher;
    }

    @Transactional
    public CourseResponse archive(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseGuard.lockOwned(courseId, currentUserId, isAdmin);

        // Repeated archival is idempotent.
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            if (scheduleChecks.hasUnfinishedSessions(
                    courseId, clock.instant())) {
                throw new BusinessConflictException(
                        "Cancel future sessions and wait for ongoing sessions to finish before archiving");
            }
            course.archive();
        }

        // Hibernate Dirty Checking performs UPDATE.
        courseRepository.flush();
        return CourseResponse.from(course);
    }

    @Transactional
    public void delete(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseGuard.lockOwned(courseId, currentUserId, isAdmin);

        if (hasHistoricalDependencies(courseId)) {
            throw new BusinessConflictException(
                    "Course has educational dependencies "
                            + "and cannot be deleted. Archive it instead."
            );
        }

        try {
            courseRepository.delete(course);
            courseRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // A database FK may still reject the deletion.
            throw new BusinessConflictException(
                    "Course cannot be deleted because "
                            + "related records exist"
            );
        }
    }

    private boolean hasHistoricalDependencies(Long courseId) {
        return contentStructure.hasModules(courseId)
                || enrollmentAccess.hasEnrollmentHistory(courseId)
                || reviewAccess.hasReviews(courseId);
    }

    @Transactional
    public CourseResponse reassignTeacher(
            Long courseId,
            Long newTeacherId,
            Long version,
            boolean isAdmin
    ) {
        if (!isAdmin) {
            throw new AccessDeniedException(
                    "Only ADMIN can reassign course teachers"
            );
        }

        Course course = courseGuard.lock(courseId);

        User newTeacher = findActiveTeacher(newTeacherId);

        ensureTeacherNotEnrolled(courseId, newTeacherId);
        ensureScheduleCompatible(course, newTeacherId);

        EditVersion.require(version, course.getVersion());
        course.reassignTeacher(newTeacher);

        courseRepository.flush();
        return CourseResponse.from(course);
    }

}

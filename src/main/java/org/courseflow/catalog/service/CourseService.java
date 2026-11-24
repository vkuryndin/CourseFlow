package org.courseflow.catalog.service;

import org.courseflow.common.exception.EditVersion;
import java.time.Clock;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.api.SearchPatterns;
import org.courseflow.content.repository.LessonSessionRepository;
import jakarta.persistence.criteria.Predicate;
import org.courseflow.catalog.dto.CourseResponse;
import org.courseflow.catalog.dto.CreateCourseRequest;
import org.courseflow.catalog.dto.UpdateCourseRequest;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.repository.EnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.catalog.repository.TagRepository;

@Service
public class CourseService {

    private final Clock clock;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "id",
                    "title",
                    "duration",
                    "startDate",
                    "status"
            );

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final TagRepository tagRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonSessionRepository sessionRepository;

    public CourseService(
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            EntityManager entityManager,
            TagRepository tagRepository,
            EnrollmentRepository enrollmentRepository,
            LessonSessionRepository sessionRepository,
            Clock clock
    ) {
        this.clock = clock;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.tagRepository = tagRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
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
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        checkCourseOwner(course, currentUserId, isAdmin);
        CourseAccessPolicy.requireActive(course,
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

    @Transactional(readOnly = true)
    public CourseResponse getById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + id
                        )
                );

        return CourseResponse.from(course);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> findAll(
            int page,
            int size,
            String q,
            Long categoryId,
            Long teacherId,
            CourseStatus status,
            String sortBy,
            String direction
    ) {
        Pagination.validate(page, size);

        if (categoryId != null && categoryId < 1) {
            throw new BadRequestException(
                    "categoryId must be positive"
            );
        }

        if (teacherId != null && teacherId < 1) {
            throw new BadRequestException(
                    "teacherId must be positive"
            );
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        }

        String sortDirection = Pagination.direction(direction);

        String search = q == null
                ? ""
                : q.trim().toLowerCase(Locale.ROOT);

        if (search.length() > 200) {
            throw new BadRequestException(
                    "Search query is too long"
            );
        }

        Specification<Course> specification =
                (root, query, builder) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    if (!search.isEmpty()) {
                        String pattern = SearchPatterns.containsLiteral(search);

                        Predicate titleMatches = builder.like(
                                builder.lower(root.get("title")),
                                pattern, '\\'
                        );

                        Predicate descriptionMatches = builder.like(
                                builder.lower(root.get("description")),
                                pattern, '\\'
                        );

                        predicates.add(
                                builder.or(
                                        titleMatches,
                                        descriptionMatches
                                )
                        );
                    }

                    if (categoryId != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("category").get("id"),
                                        categoryId
                                )
                        );
                    }

                    if (teacherId != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("teacher").get("id"),
                                        teacherId
                                )
                        );
                    }

                    if (status != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("status"),
                                        status
                                )
                        );
                    }

                    return builder.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        Sort.Direction springDirection =
                sortDirection.equals("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        Sort sort = Sort.by(
                springDirection,
                sortBy
        );

        // Stable ordering when several courses
        // have the same value in the primary sort field.
        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<CourseResponse> result = courseRepository
                .findAll(specification, pageable)
                .map(CourseResponse::from);

        return PageResponse.from(result);
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
                && sessionRepository.countReassignmentOverlaps(course.getId(), teacherId, clock.instant()) > 0) {
            throw new BusinessConflictException("New teacher has overlapping scheduled lessons");
        }
    }

    private void ensureTeacherNotEnrolled(Long courseId, Long teacherId) {
        boolean enrolled = enrollmentRepository.hasActiveEnrollment(teacherId, courseId);
        if (enrolled) {
            throw new BusinessConflictException(
                    "Withdraw active enrollment before assigning the course teacher"
            );
        }
    }

    private User findActiveTeacher(Long teacherId) {
        User teacher = userRepository.findByIdForUpdate(teacherId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Teacher not found: " + teacherId
                        )
                );

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
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        checkCourseOwner(course, currentUserId, isAdmin);

        // Repeated archival is idempotent.
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            if (sessionRepository.existsByLesson_Module_Course_IdAndEndsAtGreaterThan(
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
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        checkCourseOwner(course, currentUserId, isAdmin);

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

    private void checkCourseOwner(
            Course course,
            Long currentUserId,
            boolean isAdmin
    ) {
        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Teacher can manage only own courses");
    }

    private boolean hasHistoricalDependencies(Long courseId) {
        // Modules may contain lessons, assignments and quizzes.
        if (existsForCourse(
                "select count(m) from Module m "
                        + "where m.course.id = :courseId",
                courseId
        )) {
            return true;
        }

        // Both ACTIVE and WITHDRAWN enrollments count as history.
        if (existsForCourse(
                "select count(e) from Enrollment e "
                        + "where e.course.id = :courseId",
                courseId
        )) {
            return true;
        }

        // Reviews are historical educational data.
        return existsForCourse(
                "select count(r) from CourseReview r "
                        + "where r.course.id = :courseId",
                courseId
        );
    }

    private boolean existsForCourse(
            String jpql,
            Long courseId
    ) {
        Long count = entityManager
                .createQuery(jpql, Long.class)
                .setParameter("courseId", courseId)
                .getSingleResult();

        return count > 0;
    }

    @Transactional
    public void addTagToCourse(
            Long courseId,
            Long tagId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = findCourseForTagModification(
                courseId, currentUserId, isAdmin
        );

        Tag tag = tagRepository.findByIdForShare(tagId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Tag not found: " + tagId
                        )
                );

        // Set prevents duplicate links.
        course.addTag(tag);
    }

    @Transactional
    public void removeTagFromCourse(
            Long courseId,
            Long tagId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = findCourseForTagModification(
                courseId, currentUserId, isAdmin
        );

        Tag tag = tagRepository.findByIdForShare(tagId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Tag not found: " + tagId
                        )
                );

        // Removing an absent link is idempotent.
        course.removeTag(tag);
    }

    @Transactional(readOnly = true)
    public PageResponse<TagResponse> findCourseTags(
            Long courseId,
            int page,
            int size
    ) {
        Pagination.validate(page, size);

        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException(
                    "Course not found: " + courseId
            );
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("name").ascending()
                        .and(Sort.by("id").ascending())
        );

        Page<TagResponse> result = tagRepository
                .findByCourseId(courseId, pageable)
                .map(TagResponse::from);

        return PageResponse.from(result);
    }

    private Course findCourseForTagModification(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        checkCourseOwner(course, currentUserId, isAdmin);

        CourseAccessPolicy.requireActive(course,
                "Cannot modify tags of an archived course");

        return course;
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

        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        User newTeacher = findActiveTeacher(newTeacherId);

        ensureTeacherNotEnrolled(courseId, newTeacherId);
        ensureScheduleCompatible(course, newTeacherId);

        EditVersion.require(version, course.getVersion());
        course.reassignTeacher(newTeacher);

        courseRepository.flush();
        return CourseResponse.from(course);
    }

}

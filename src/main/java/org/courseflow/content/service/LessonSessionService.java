
package org.courseflow.content.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.dto.CreateLessonSessionRequest;
import org.courseflow.content.dto.LessonSessionResponse;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.LessonSessionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.courseflow.content.dto.RescheduleLessonSessionRequest;

@Service
public class LessonSessionService {

    private final LessonSessionRepository sessionRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;

    public LessonSessionService(
            LessonSessionRepository sessionRepository,
            LessonRepository lessonRepository,
            CourseRepository courseRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public LessonSessionResponse create(
            Long lessonId,
            CreateLessonSessionRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        validateTimeRange(request);

        // First determine which course owns the lesson.
        Lesson lesson = findLesson(lessonId);
        Long courseId = lesson.getModule().getCourse().getId();

        // Serialize schedule changes within the course.
        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (!isAdmin
                && !currentUserId.equals(
                course.getTeacher().getId()
        )) {
            throw new AccessDeniedException(
                    "Teacher can manage only own courses"
            );
        }

        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessConflictException(
                    "Cannot schedule lessons in an archived course"
            );
        }

        long overlaps = sessionRepository
                .countOverlappingSessions(
                        lessonId,
                        request.startsAt(),
                        request.endsAt()
                );

        if (overlaps > 0) {
            throw new BusinessConflictException(
                    "Lesson session overlaps an existing session"
            );
        }

        String meetingUrl = request.meetingUrl() == null
                || request.meetingUrl().isBlank()
                ? null
                : request.meetingUrl().trim();

        LessonSession session = lesson.addSession(
                request.startsAt(),
                request.endsAt(),
                request.format(),
                meetingUrl
        );

        LessonSession saved =
                sessionRepository.saveAndFlush(session);

        return LessonSessionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonSessionResponse> findByLesson(
            Long lessonId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException(
                    "Invalid pagination parameters"
            );
        }

        findLesson(lessonId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("startsAt").ascending()
                        .and(Sort.by("id").ascending())
        );

        Page<LessonSessionResponse> result =
                sessionRepository
                        .findAllByLesson_Id(lessonId, pageable)
                        .map(LessonSessionResponse::from);

        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public LessonSessionResponse getById(
            Long lessonId,
            Long sessionId
    ) {
        findLesson(lessonId);

        LessonSession session = sessionRepository
                .findByIdAndLesson_Id(sessionId, lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson session not found: " + sessionId
                        )
                );

        return LessonSessionResponse.from(session);
    }

    private Lesson findLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found: " + lessonId
                        )
                );
    }

    private void validateTimeRange(
            CreateLessonSessionRequest request
    ) {
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BadRequestException(
                    "Session start time must be before end time"
            );
        }
    }

    @Transactional
    public LessonSessionResponse update(
            Long lessonId,
            Long sessionId,
            CreateLessonSessionRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        LessonSession session = findEditableSession(
                lessonId, sessionId, currentUserId, isAdmin
        );

        validateInterval(request.startsAt(), request.endsAt());

        checkNoOverlap(
                lessonId,
                sessionId,
                request.startsAt(),
                request.endsAt()
        );

        String meetingUrl = request.meetingUrl() == null
                || request.meetingUrl().isBlank()
                ? null
                : request.meetingUrl().trim();

        session.updateDetails(
                request.startsAt(),
                request.endsAt(),
                request.format(),
                meetingUrl
        );

        return LessonSessionResponse.from(session);
    }

    @Transactional
    public LessonSessionResponse reschedule(
            Long lessonId,
            Long sessionId,
            RescheduleLessonSessionRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        LessonSession session = findEditableSession(
                lessonId, sessionId, currentUserId, isAdmin
        );

        validateInterval(request.startsAt(), request.endsAt());

        checkNoOverlap(
                lessonId,
                sessionId,
                request.startsAt(),
                request.endsAt()
        );

        session.changeTime(
                request.startsAt(),
                request.endsAt()
        );

        return LessonSessionResponse.from(session);
    }


    @Transactional
    public void delete(
            Long lessonId,
            Long sessionId,
            Long currentUserId,
            boolean isAdmin
    ) {
        LessonSession session = findEditableSession(
                lessonId, sessionId, currentUserId, isAdmin
        );

        session.getLesson().removeSession(session);

        sessionRepository.delete(session);
        sessionRepository.flush();
    }

    private LessonSession findEditableSession(
            Long lessonId,
            Long sessionId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Lesson lesson = findLesson(lessonId);

        Long courseId = lesson.getModule()
                .getCourse()
                .getId();

        Course course = courseRepository
                .findByIdForUpdate(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + courseId
                        )
                );

        if (!isAdmin
                && !currentUserId.equals(
                course.getTeacher().getId()
        )) {
            throw new AccessDeniedException(
                    "Teacher can manage only own courses"
            );
        }

        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessConflictException(
                    "Cannot modify sessions of an archived course"
            );
        }

        return sessionRepository
                .findByIdAndLesson_Id(sessionId, lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson session not found: " + sessionId
                        )
                );
    }

    private void validateInterval(
            java.time.Instant startsAt,
            java.time.Instant endsAt
    ) {
        if (!startsAt.isBefore(endsAt)) {
            throw new BadRequestException(
                    "Session start time must be before end time"
            );
        }
    }

    private void checkNoOverlap(
            Long lessonId,
            Long sessionId,
            java.time.Instant startsAt,
            java.time.Instant endsAt
    ) {
        long overlaps = sessionRepository
                .countOverlappingSessionsExcept(
                        lessonId,
                        sessionId,
                        startsAt,
                        endsAt
                );

        if (overlaps > 0) {
            throw new BusinessConflictException(
                    "Lesson session overlaps an existing session"
            );
        }
    }


}

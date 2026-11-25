package org.courseflow.content.service;

import java.time.Clock;
import java.time.Instant;
import org.courseflow.catalog.api.CourseGuard;
import org.courseflow.catalog.entity.Course;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.common.validation.HttpUrls;
import org.courseflow.content.api.ContentAccessService;
import org.courseflow.content.dto.CreateLessonSessionRequest;
import org.courseflow.content.dto.LessonSessionResponse;
import org.courseflow.content.dto.RescheduleLessonSessionRequest;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.LessonSessionRepository;
import org.courseflow.identity.api.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonSessionService {

  private final LessonSessionRepository sessionRepository;
  private final LessonRepository lessonRepository;
  private final CourseGuard courseGuard;
  private final ContentAccessService contentAccessService;
  private final UserDirectory userDirectory;
  private final Clock clock;

  public LessonSessionService(
      LessonSessionRepository sessionRepository,
      LessonRepository lessonRepository,
      ContentAccessService contentAccessService,
      CourseGuard courseGuard,
      UserDirectory userDirectory,
      Clock clock) {
    this.sessionRepository = sessionRepository;
    this.lessonRepository = lessonRepository;
    this.courseGuard = courseGuard;
    this.contentAccessService = contentAccessService;
    this.userDirectory = userDirectory;
    this.clock = clock;
  }

  @Transactional
  public LessonSessionResponse create(
      Long lessonId, CreateLessonSessionRequest request, Long currentUserId, boolean isAdmin) {
    validateInterval(request.startsAt(), request.endsAt());

    Long courseId = findCourseId(lessonId);

    // Serialize schedule changes within the course.
    Course course =
        courseGuard.lockEditable(
            courseId, currentUserId, isAdmin, "Cannot schedule lessons in an archived course");

    Long teacherId = course.getTeacher().getId();
    lockTeacher(teacherId);
    requireFutureStart(request.startsAt());
    Lesson lesson = findLesson(lessonId);
    checkNoOverlap(teacherId, null, request.startsAt(), request.endsAt());

    String meetingUrl = validateMeetingUrl(request);

    LessonSession session =
        lesson.addSession(request.startsAt(), request.endsAt(), request.format(), meetingUrl);

    LessonSession saved = sessionRepository.saveAndFlush(session);

    return LessonSessionResponse.from(saved);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<LessonSessionResponse> findByLesson(
      Long lessonId, int page, int size, Long currentUserId, Authentication authentication) {
    contentAccessService.checkLessonAccess(lessonId, currentUserId, authentication);
    Pagination.validate(page, size);

    findLesson(lessonId);

    PageRequest pageable = PageRequest.of(page, size, Pagination.stableSort("startsAt", "asc"));

    Page<LessonSessionResponse> result =
        sessionRepository.findAllByLesson_Id(lessonId, pageable).map(LessonSessionResponse::from);

    return PageResponse.from(result);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public LessonSessionResponse getById(
      Long lessonId, Long sessionId, Long currentUserId, Authentication authentication) {
    contentAccessService.checkLessonAccess(lessonId, currentUserId, authentication);
    findLesson(lessonId);

    LessonSession session =
        sessionRepository
            .findByIdAndLesson_Id(sessionId, lessonId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Lesson session not found: " + sessionId));

    return LessonSessionResponse.from(session);
  }

  private Lesson findLesson(Long lessonId) {
    return lessonRepository
        .findById(lessonId)
        .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
  }

  private String validateMeetingUrl(CreateLessonSessionRequest request) {
    String url = HttpUrls.optional(request.meetingUrl(), "meetingUrl");
    if (request.format() == LessonSessionFormat.ONLINE && url == null) {
      throw new BadRequestException("ONLINE session requires meetingUrl");
    }
    if (request.format() == LessonSessionFormat.OFFLINE && url != null) {
      throw new BadRequestException("OFFLINE session must not have meetingUrl");
    }
    return url;
  }

  @Transactional
  public LessonSessionResponse update(
      Long lessonId,
      Long sessionId,
      CreateLessonSessionRequest request,
      Long currentUserId,
      boolean isAdmin) {
    LessonSession session = findEditableSession(lessonId, sessionId, currentUserId, isAdmin);

    validateInterval(request.startsAt(), request.endsAt());
    requireNotStarted(session);
    requireFutureStart(request.startsAt());

    checkNoOverlap(
        session.getLesson().getModule().getCourse().getTeacher().getId(),
        sessionId,
        request.startsAt(),
        request.endsAt());

    String meetingUrl = validateMeetingUrl(request);

    EditVersion.require(request.version(), session.getVersion());

    session.updateDetails(request.startsAt(), request.endsAt(), request.format(), meetingUrl);

    sessionRepository.flush();
    return LessonSessionResponse.from(session);
  }

  @Transactional
  public LessonSessionResponse reschedule(
      Long lessonId,
      Long sessionId,
      RescheduleLessonSessionRequest request,
      Long currentUserId,
      boolean isAdmin) {
    LessonSession session = findEditableSession(lessonId, sessionId, currentUserId, isAdmin);

    validateInterval(request.startsAt(), request.endsAt());
    requireNotStarted(session);
    requireFutureStart(request.startsAt());

    checkNoOverlap(
        session.getLesson().getModule().getCourse().getTeacher().getId(),
        sessionId,
        request.startsAt(),
        request.endsAt());

    EditVersion.require(request.version(), session.getVersion());

    session.changeTime(request.startsAt(), request.endsAt());

    sessionRepository.flush();
    return LessonSessionResponse.from(session);
  }

  @Transactional
  public void delete(Long lessonId, Long sessionId, Long currentUserId, boolean isAdmin) {
    LessonSession session = findEditableSession(lessonId, sessionId, currentUserId, isAdmin);

    requireNotStarted(session);
    session.getLesson().removeSession(session);

    sessionRepository.delete(session);
    sessionRepository.flush();
  }

  private LessonSession findEditableSession(
      Long lessonId, Long sessionId, Long currentUserId, boolean isAdmin) {
    Long courseId = findCourseId(lessonId);

    Course course =
        courseGuard.lockEditable(
            courseId, currentUserId, isAdmin, "Cannot modify sessions of an archived course");

    lockTeacher(course.getTeacher().getId());
    return sessionRepository
        .findByIdAndLesson_Id(sessionId, lessonId)
        .orElseThrow(() -> new ResourceNotFoundException("Lesson session not found: " + sessionId));
  }

  private void requireFutureStart(Instant startsAt) {
    if (!startsAt.isAfter(clock.instant())) {
      throw new BadRequestException("Session start must be in the future");
    }
  }

  private void requireNotStarted(LessonSession session) {
    if (!session.getStartsAt().isAfter(clock.instant())) {
      throw new BusinessConflictException(
          "A session that has already started cannot be changed or deleted");
    }
  }

  private void validateInterval(java.time.Instant startsAt, java.time.Instant endsAt) {
    if (!startsAt.isBefore(endsAt)) {
      throw new BadRequestException("Session start time must be before end time");
    }
  }

  private Long findCourseId(Long lessonId) {
    return lessonRepository
        .findCourseId(lessonId)
        .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
  }

  private void lockTeacher(Long teacherId) {
    userDirectory.lockTeacherReference(teacherId);
  }

  private void checkNoOverlap(
      Long teacherId, Long sessionId, java.time.Instant startsAt, java.time.Instant endsAt) {
    if (sessionRepository.countTeacherOverlaps(
            teacherId, sessionId == null ? Long.valueOf(-1L) : sessionId, startsAt, endsAt)
        > 0) {
      throw new BusinessConflictException(
          "Lesson session overlaps another session of this teacher");
    }
  }
}

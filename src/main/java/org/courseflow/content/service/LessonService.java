package org.courseflow.content.service;

import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.common.validation.HttpUrls;
import org.courseflow.content.dto.ChangeLessonPositionRequest;
import org.courseflow.content.dto.CreateLessonRequest;
import org.courseflow.content.dto.LessonResponse;
import org.courseflow.content.dto.LessonSummaryResponse;
import org.courseflow.content.dto.UpdateLessonRequest;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.content.repository.ModuleRepository;
import org.courseflow.learning.api.AssignmentAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonService {

  private final LessonRepository lessonRepository;
  private final ModuleRepository moduleRepository;
  private final CourseGuard courseGuard;
  private final ContentAccessService contentAccessService;
  private final LessonScheduleChecks scheduleChecks;
  private final AssignmentAccess assignmentAccess;

  public LessonService(
      LessonRepository lessonRepository,
      ModuleRepository moduleRepository,
      ContentAccessService contentAccessService,
      CourseGuard courseGuard,
      LessonScheduleChecks scheduleChecks,
      AssignmentAccess assignmentAccess) {
    this.lessonRepository = lessonRepository;
    this.moduleRepository = moduleRepository;
    this.courseGuard = courseGuard;
    this.contentAccessService = contentAccessService;
    this.scheduleChecks = scheduleChecks;
    this.assignmentAccess = assignmentAccess;
  }

  @Transactional
  public LessonResponse create(
      Long courseId,
      Long moduleId,
      CreateLessonRequest request,
      Long currentUserId,
      boolean isAdmin) {
    // Lock the parent course to serialize content changes.
    courseGuard.lockEditable(
        courseId, currentUserId, isAdmin, "Cannot add lessons to an archived course");

    Module module = findModule(courseId, moduleId);

    if (lessonRepository.existsByModule_IdAndPosition(moduleId, request.position())) {
      throw new BusinessConflictException("Lesson position is already occupied");
    }

    String videoUrl = HttpUrls.optional(request.videoUrl(), "videoUrl");

    Lesson lesson =
        module.addLesson(
            request.title().trim(), request.content().trim(), videoUrl, request.position());

    Lesson saved = lessonRepository.saveAndFlush(lesson);

    return LessonResponse.from(saved);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<LessonSummaryResponse> findByModule(
      Long courseId,
      Long moduleId,
      int page,
      int size,
      Long currentUserId,
      Authentication authentication) {
    contentAccessService.checkCourseAccess(courseId, currentUserId, authentication);
    Pagination.validate(page, size);

    findModule(courseId, moduleId);

    PageRequest pageable = PageRequest.of(page, size, Pagination.stableSort("position", "asc"));

    Page<LessonSummaryResponse> result =
        lessonRepository.findSummariesByModuleId(moduleId, pageable);

    return PageResponse.from(result);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public LessonResponse getById(
      Long courseId,
      Long moduleId,
      Long lessonId,
      Long currentUserId,
      Authentication authentication) {
    contentAccessService.checkCourseAccess(courseId, currentUserId, authentication);
    findModule(courseId, moduleId);

    Lesson lesson =
        lessonRepository
            .findByIdAndModule_Id(lessonId, moduleId)
            .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));

    return LessonResponse.from(lesson);
  }

  private Module findModule(Long courseId, Long moduleId) {
    return moduleRepository
        .findByIdAndCourse_Id(moduleId, courseId)
        .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));
  }

  @Transactional
  public LessonResponse update(
      Long courseId,
      Long moduleId,
      Long lessonId,
      UpdateLessonRequest request,
      Long currentUserId,
      boolean isAdmin) {
    findEditableCourse(courseId, currentUserId, isAdmin);
    findModule(courseId, moduleId);

    Lesson lesson = findLesson(moduleId, lessonId);

    String videoUrl = HttpUrls.optional(request.videoUrl(), "videoUrl");

    EditVersion.require(request.version(), lesson.getVersion());

    lesson.updateDetails(request.title().trim(), request.content().trim(), videoUrl);

    lessonRepository.flush();
    return LessonResponse.from(lesson);
  }

  @Transactional
  public LessonResponse changePosition(
      Long courseId,
      Long moduleId,
      Long lessonId,
      ChangeLessonPositionRequest request,
      Long currentUserId,
      boolean isAdmin) {
    findEditableCourse(courseId, currentUserId, isAdmin);
    findModule(courseId, moduleId);

    Lesson lesson = findLesson(moduleId, lessonId);

    EditVersion.require(request.version(), lesson.getVersion());

    int oldPosition = lesson.getPosition();
    int newPosition = request.position();

    if (oldPosition == newPosition) {
      return LessonResponse.from(lesson);
    }

    var affected =
        lessonRepository.findByModule_IdAndPositionBetweenOrderByPositionAsc(
            moduleId, Math.min(oldPosition, newPosition), Math.max(oldPosition, newPosition));
    PositionMover.move(
        lesson,
        oldPosition,
        newPosition,
        affected,
        Lesson::getPosition,
        Lesson::changePosition,
        () ->
            lessonRepository
                .findFirstByModule_IdOrderByPositionDescIdDesc(moduleId)
                .map(Lesson::getPosition)
                .orElse(0),
        lessonRepository::flush);

    return LessonResponse.from(lesson);
  }

  @Transactional
  public void delete(
      Long courseId, Long moduleId, Long lessonId, Long currentUserId, boolean isAdmin) {
    findEditableCourse(courseId, currentUserId, isAdmin);

    Module module = findModule(courseId, moduleId);
    Lesson lesson = findLesson(moduleId, lessonId);

    if (scheduleChecks.hasSessionsForLesson(lessonId)
        || assignmentAccess.hasAssignments(lessonId)) {
      throw new BusinessConflictException(
          "Lesson has sessions or assignments " + "and cannot be deleted");
    }

    try {
      module.removeLesson(lesson);
      lessonRepository.delete(lesson);
      lessonRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessConflictException("Lesson has related records and cannot be deleted");
    }
  }

  private Lesson findLesson(Long moduleId, Long lessonId) {
    return lessonRepository
        .findByIdAndModule_Id(lessonId, moduleId)
        .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
  }

  private Course findEditableCourse(Long courseId, Long currentUserId, boolean isAdmin) {
    Course course =
        courseGuard.lockEditable(
            courseId, currentUserId, isAdmin, "Cannot modify lessons of archived course");

    return course;
  }
}

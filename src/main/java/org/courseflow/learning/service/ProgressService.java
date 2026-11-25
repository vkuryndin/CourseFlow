package org.courseflow.learning.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.CourseProgressResponse;
import org.courseflow.learning.dto.CourseProgressResponse.AssignmentProgress;
import org.courseflow.learning.dto.CourseProgressResponse.QuizProgress;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.courseflow.learning.repository.ProgressQueryRepository;
import org.courseflow.learning.repository.ProgressQueryRepository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
  private final Clock clock;

  private final ProgressQueryRepository queries;

  public ProgressService(ProgressQueryRepository queries, Clock clock) {
    this.clock = clock;
    this.queries = queries;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<CourseProgressSummaryResponse> getDashboardProgress(
      Long studentId, int size, Instant now) {
    return loadSummaries(
        studentId,
        null,
        EnrollmentStatus.ACTIVE,
        CourseStatus.ACTIVE,
        0,
        size,
        "courseTitle",
        "asc",
        now);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<CourseProgressResponse> getMyProgress(
      Long studentId,
      Long courseId,
      EnrollmentStatus enrollmentStatus,
      int page,
      int size,
      String sortBy,
      String direction,
      boolean includeDetails,
      int detailPage,
      int detailSize) {
    Pagination.validate(detailPage, detailSize);
    if (includeDetails && courseId == null) {
      throw new BadRequestException("courseId is required when includeDetails is true");
    }
    Instant now = clock.instant();
    var summaries =
        loadSummaries(
            studentId, courseId, enrollmentStatus, null, page, size, sortBy, direction, now);
    List<CourseProgressResponse> result = new ArrayList<>();
    for (var summary : summaries.content()) {
      List<AssignmentProgress> assignments = List.of();
      List<QuizProgress> quizzes = List.of();
      List<AssignmentProgress> deadlines = List.of();
      if (includeDetails) {
        List<Long> ids = List.of(summary.courseId());
        assignments =
            loadAssignments(studentId, ids, now, detailPage, detailSize, false)
                .getOrDefault(summary.courseId(), List.of());
        quizzes =
            loadQuizzes(studentId, ids, now, detailPage, detailSize)
                .getOrDefault(summary.courseId(), List.of());
        if (summary.totalUpcomingDeadlines() > 0) {
          deadlines =
              loadAssignments(studentId, ids, now, detailPage, detailSize, true)
                  .getOrDefault(summary.courseId(), List.of());
        }
      }
      result.add(
          new CourseProgressResponse(
              summary.courseId(),
              summary.courseTitle(),
              summary.courseStatus(),
              summary.enrollmentStatus(),
              summary.totalAssessments(),
              summary.completedAssessments(),
              summary.progressPercent(),
              summary.performancePercent(),
              assignments,
              quizzes,
              deadlines,
              includeDetails,
              detailPage,
              detailSize,
              summary.totalAssignments(),
              summary.totalQuizzes(),
              summary.totalUpcomingDeadlines()));
    }
    return new PageResponse<>(
        result,
        summaries.page(),
        summaries.size(),
        summaries.totalElements(),
        summaries.totalPages(),
        summaries.first(),
        summaries.last());
  }

  private PageResponse<CourseProgressSummaryResponse> loadSummaries(
      Long studentId,
      Long courseId,
      EnrollmentStatus enrollmentStatus,
      CourseStatus courseStatus,
      int page,
      int size,
      String sortBy,
      String direction,
      Instant now) {
    Pagination.validate(page, size);

    if (courseId != null && courseId < 1) {
      throw new BadRequestException("Invalid courseId");
    }

    var paged =
        queries.findCourses(
            studentId, courseId, enrollmentStatus, courseStatus, page, size, sortBy, direction);
    List<CourseRow> courses = paged.rows();
    long total = paged.total();

    List<Long> courseIds = courses.stream().map(row -> row.courseId()).toList();

    Map<Long, AssignmentAggregate> assignments = new HashMap<>();
    Map<Long, QuizAggregate> quizzes = new HashMap<>();
    for (var row : queries.aggregateAssignments(studentId, courseIds, now))
      assignments.put(row.courseId(), row);
    for (var row : queries.aggregateQuizzes(studentId, courseIds)) quizzes.put(row.courseId(), row);
    List<CourseProgressSummaryResponse> result = new ArrayList<>();
    for (CourseRow row : courses) {
      var a = assignments.get(row.courseId());
      var q = quizzes.get(row.courseId());
      long assignmentCount = a == null ? 0 : a.total();
      long quizCount = q == null ? 0 : q.total();
      long completed = (a == null ? 0 : a.completed()) + (q == null ? 0 : q.evaluated());
      long evaluated = (a == null ? 0 : a.evaluated()) + (q == null ? 0 : q.evaluated());
      double performance =
          (a == null ? 0.0 : a.performanceSum())
              + (q == null || q.performanceSum() == null ? 0.0 : q.performanceSum());
      long totalAssessments = assignmentCount + quizCount;
      long deadlines =
          a != null
                  && row.courseStatus() == CourseStatus.ACTIVE
                  && row.enrollmentStatus() == EnrollmentStatus.ACTIVE
              ? a.upcoming()
              : 0;
      result.add(
          new CourseProgressSummaryResponse(
              row.courseId(),
              row.title(),
              row.courseStatus(),
              row.enrollmentStatus(),
              Math.toIntExact(totalAssessments),
              Math.toIntExact(completed),
              totalAssessments == 0 ? 0 : (int) Math.round(completed * 100.0 / totalAssessments),
              evaluated == 0 ? null : (int) Math.round(performance / evaluated),
              assignmentCount,
              quizCount,
              deadlines));
    }
    return new JpqlPages.Result<>(result, total, page, size).response();
  }

  private Map<Long, List<AssignmentProgress>> loadAssignments(
      Long studentId,
      List<Long> courseIds,
      Instant now,
      int detailPage,
      int detailSize,
      boolean deadlinesOnly) {
    Map<Long, List<AssignmentProgress>> result = new HashMap<>();

    if (courseIds.isEmpty()) {
      return result;
    }

    List<AssignmentRow> rows =
        queries.findAssignments(studentId, courseIds, now, detailPage, detailSize, deadlinesOnly);
    for (AssignmentRow row : rows) {

      Long courseId = row.courseId();

      String status = AssessmentStatuses.assignment(row.status(), row.dueAt(), now);

      AssignmentProgress assignment =
          new AssignmentProgress(
              row.assignmentId(), row.title(), row.maxScore(), row.score(), status, row.dueAt());

      result.computeIfAbsent(courseId, key -> new ArrayList<>()).add(assignment);
    }

    return result;
  }

  private Map<Long, List<QuizProgress>> loadQuizzes(
      Long studentId, List<Long> courseIds, Instant now, int detailPage, int detailSize) {
    Map<Long, List<QuizProgress>> result = new HashMap<>();

    if (courseIds.isEmpty()) {
      return result;
    }

    List<QuizRow> rows = queries.findQuizzes(studentId, courseIds, now, detailPage, detailSize);
    for (QuizRow row : rows) {

      Long courseId = row.courseId();
      Integer bestScore = row.bestScore();
      Long attemptsUsed = row.attemptsUsed();

      String status = AssessmentStatuses.quiz(bestScore, attemptsUsed, row.activeAttempts());

      QuizProgress quiz =
          new QuizProgress(
              row.quizId(), row.title(), bestScore, attemptsUsed, row.maxAttempts(), status);

      result.computeIfAbsent(courseId, key -> new ArrayList<>()).add(quiz);
    }

    return result;
  }
}

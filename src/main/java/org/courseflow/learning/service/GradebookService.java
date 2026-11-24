package org.courseflow.learning.service;

import java.time.Clock;
import java.time.Instant;
import org.courseflow.catalog.service.CourseAccessPolicy;
import org.courseflow.common.api.Pagination;
import org.courseflow.learning.repository.GradebookQueryRepository;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.learning.dto.GradebookResponse;
import org.courseflow.learning.dto.GradebookResponse.*;
import org.courseflow.learning.entity.EnrollmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.List;

@Service
public class GradebookService {

    private final Clock clock;

    private final CourseGuard courseGuard;
    private final ContentStructure contentStructure;
    private final GradebookQueryRepository queries;

    public GradebookService(
            CourseGuard courseGuard,
            ContentStructure contentStructure,
            GradebookQueryRepository queries,
            Clock clock
    ) {
        this.clock = clock;
        this.courseGuard = courseGuard;
        this.contentStructure = contentStructure;
        this.queries = queries;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GradebookResponse getGradebook(
            Long courseId,
            Long currentUserId,
            boolean isAdmin,
            String q,
            EnrollmentStatus enrollmentStatus,
            ReviewStatus reviewStatus,
            Long moduleId,
            AssessmentType type,
            int page,
            int size,
            String sortBy,
            String direction,
            int columnPage, int columnSize
    ) {
        requireCourseAccess(
                courseId,
                currentUserId,
                isAdmin
        );

        Pagination.validate(page, size);
        Pagination.validate(columnPage, columnSize);

        if (type == AssessmentType.QUIZ
                && reviewStatus != null) {
            throw new BadRequestException(
                    "Review status applies only to assignments"
            );
        }

        if (moduleId != null) {
            requireModule(courseId, moduleId);
        }

        Instant now = clock.instant();
        var studentPage = queries.findStudents(courseId, q, enrollmentStatus, reviewStatus,
                moduleId, page, size, sortBy, direction, now);
        long assignmentCount = type == AssessmentType.QUIZ ? 0 : queries.countColumns(courseId, moduleId, AssessmentType.ASSIGNMENT);
        long quizCount = type == AssessmentType.ASSIGNMENT ? 0 : queries.countColumns(courseId, moduleId, AssessmentType.QUIZ);
        long totalColumns = assignmentCount + quizCount;
        var columnRows = queries.loadColumns(courseId, moduleId, columnPage * columnSize, columnSize,
                assignmentCount, quizCount);
        List<Column> columns = GradebookAssembler.columns(columnRows);
        List<Long> studentIds = studentPage.rows().stream().map(row -> row.studentId()).toList();
        List<Long> assignmentIds = columnRows.assignments().stream().map(row -> row.id()).toList();
        List<Long> quizIds = columnRows.quizzes().stream().map(row -> row.id()).toList();
        var assignmentResults = queries.findAssignmentResults(studentIds, assignmentIds);
        var quizResults = queries.findQuizResults(studentIds, quizIds, now);
        PageResponse<StudentRow> students = GradebookAssembler.assembleStudents(studentPage, columns, columnRows,
                assignmentResults, quizResults, now);

        return new GradebookResponse(
                courseId,
                columns,
                students, columnPage, columnSize, totalColumns,
                totalColumns / columnSize + (totalColumns % columnSize == 0 ? 0 : 1)
        );
    }

    private void requireCourseAccess(
            Long courseId,
            Long currentUserId,
            boolean isAdmin
    ) {
        Course course = courseGuard.find(courseId);

        CourseAccessPolicy.requireOwnerOrAdmin(course, currentUserId, isAdmin,
                "Teacher can view only own course gradebook");
    }

    private void requireModule(
            Long courseId,
            Long moduleId
    ) {
        contentStructure.requireModuleInCourse(courseId, moduleId);
    }

}

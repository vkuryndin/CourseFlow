package org.courseflow.learning.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.courseflow.common.api.JpqlPages;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.GradebookResponse.*;
import org.courseflow.learning.repository.GradebookQueryRepository.*;

/** Pure read-model assembly: no persistence or security dependencies. */
final class GradebookAssembler {
  private GradebookAssembler() {}

  private record CellKey(Long studentId, Long assessmentId, AssessmentType type) {}

  static List<Column> columns(ColumnRows rows) {
    List<Column> columns = new ArrayList<>();
    for (AssignmentColumn row : rows.assignments()) {
      columns.add(
          new Column(
              row.id(),
              AssessmentType.ASSIGNMENT,
              row.title(),
              row.moduleId(),
              row.moduleTitle(),
              row.maxScore()));
    }
    for (QuizColumn row : rows.quizzes()) {
      columns.add(
          new Column(
              row.id(), AssessmentType.QUIZ, row.title(), row.moduleId(), row.moduleTitle(), 100));
    }
    return columns;
  }

  static PageResponse<StudentRow> assembleStudents(
      JpqlPages.Result<StudentProjection> students,
      List<Column> columns,
      ColumnRows columnRows,
      List<AssignmentResult> assignmentResults,
      List<QuizResult> quizResults,
      Instant now) {
    Map<Long, Instant> assignmentDeadlines = new HashMap<>();
    for (AssignmentColumn row : columnRows.assignments())
      assignmentDeadlines.put(row.id(), row.dueAt());
    Map<CellKey, Cell> cells = new HashMap<>();
    for (AssignmentResult row : assignmentResults) {

      Long studentId = row.studentId();
      Long assignmentId = row.assignmentId();

      cells.put(
          new CellKey(studentId, assignmentId, AssessmentType.ASSIGNMENT),
          new Cell(
              assignmentId, AssessmentType.ASSIGNMENT, row.status().toString(), row.score(), null));
    }
    for (QuizResult row : quizResults) {

      Long studentId = row.studentId();
      Long quizId = row.quizId();
      Long attemptsUsed = row.attemptsUsed();
      Integer bestScore = row.bestScore();

      String status = AssessmentStatuses.quiz(bestScore, attemptsUsed, row.activeAttempts());

      cells.put(
          new CellKey(studentId, quizId, AssessmentType.QUIZ),
          new Cell(quizId, AssessmentType.QUIZ, status, bestScore, attemptsUsed));
    }
    List<StudentRow> result = new ArrayList<>();

    for (StudentProjection row : students.rows()) {

      Long studentId = row.studentId();

      List<Cell> studentResults =
          columns.stream()
              .map(
                  column -> {
                    Cell cell = cells.get(new CellKey(studentId, column.id(), column.type()));
                    return cell == null ? emptyCell(column, assignmentDeadlines, now) : cell;
                  })
              .toList();

      result.add(
          new StudentRow(
              studentId, row.name(), row.status().name(), row.enrollmentStatus(), studentResults));
    }

    return new JpqlPages.Result<>(result, students.total(), students.page(), students.size())
        .response();
  }

  private static Cell emptyCell(
      Column column, Map<Long, Instant> assignmentDeadlines, Instant now) {

    boolean isQuiz = column.type() == AssessmentType.QUIZ;

    return new Cell(
        column.id(),
        column.type(),
        isQuiz
            ? "NOT_STARTED"
            : AssessmentStatuses.assignment(null, assignmentDeadlines.get(column.id()), now),
        null,
        isQuiz ? 0L : null);
  }
}

package org.courseflow.integration.orm;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.courseflow.assessment.repository.QuestionRepository;
import org.courseflow.assessment.service.QuestionService;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.service.AuthService;
import org.courseflow.catalog.service.CourseQueryService;
import org.courseflow.content.repository.LessonRepository;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.service.UserService;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.courseflow.learning.service.GradebookService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Counts prepared Hibernate statements, not wall-clock time or HTTP latency. */
class QueryEfficiencyIT extends AdvancedHttpTest {
  @Autowired private AuthService authService;
  @Autowired private EntityManagerFactory emf;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ScheduledAnnotationBeanPostProcessor scheduler;
  @Autowired private QuestionRepository questions;
  @Autowired private QuestionService questionService;
  @Autowired private UserService userService;
  @Autowired private CourseQueryService courseQuery;
  @Autowired private LessonRepository lessons;
  @Autowired private GradebookService gradebook;

  @BeforeAll
  void stopSchedulerDuringStatementMeasurements() {
    scheduler.getScheduledTasks().forEach(ScheduledTask::cancel);
  }

  @Test
  void fetchJoinLoadsOptionsForOneHundredQuestionsWithOneStatement() throws Exception {
    QuizGraph q = populatedQuiz(100);
    long statements =
        measure(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          var loaded = questions.findWithOptionsByQuizId(q.quiz());
                          assertEquals(100, loaded.size());
                          for (var question : loaded)
                            assertEquals(2, question.getAnswerOptions().size());
                        }));
    assertEquals(1L, statements, "Questions/options must be fetched in one SQL query");
  }

  @Test
  void questionManagementQueryCountDoesNotGrowWithQuestionCount() throws Exception {
    QuizGraph small = populatedQuiz(1);
    QuizGraph large = populatedQuiz(100);
    long one =
        measure(
            () ->
                assertEquals(
                    1,
                    questionService
                        .getQuestionsForManagement(small.quiz(), teacher.id(), false)
                        .size()));
    long many =
        measure(
            () ->
                assertEquals(
                    100,
                    questionService
                        .getQuestionsForManagement(large.quiz(), teacher.id(), false)
                        .size()));
    assertTrue(many <= one + 1, "Question management query count grew from " + one + " to " + many);
    assertTrue(many <= 7, "Expected bounded question-management queries, got " + many);
  }

  @Test
  void userPageFetchesRoleCollectionsInBoundedQueries() throws Exception {
    for (int i = 0; i < 34; i++) actor(List.of("STUDENT"));
    long one =
        measure(
            () ->
                assertEquals(
                    1,
                    userService
                        .findAll(0, 1, null, null, UserRole.STUDENT, "id", "asc")
                        .content()
                        .size()));
    long many =
        measure(
            () -> {
              var page = userService.findAll(0, 33, null, null, UserRole.STUDENT, "id", "asc");
              assertEquals(33, page.content().size());
              for (var user : page.content()) assertTrue(user.roles().contains(UserRole.STUDENT));
            });
    assertTrue(many <= one + 1, "User/role queries grew from " + one + " to " + many);
    assertTrue(many <= 3, "Expected page, count and roles fetch only, got " + many);
  }

  @Test
  void coursePageAvoidsPerCourseAssociationQueries() throws Exception {
    String prefix = "Query-budget-" + unique();
    for (int i = 0; i < 34; i++) {
      Actor owner = actor(List.of("TEACHER"));
      long category =
          id(
              expect(
                  api(
                      "POST",
                      "/api/categories",
                      admin,
                      Map.of("name", "Category-" + unique(), "description", "Query fixture")),
                  201));
      var body = coursePayload();
      body.put("title", prefix + "-" + i);
      body.put("categoryId", category);
      body.put("teacherId", owner.id());
      expect(api("POST", "/api/courses", owner.token(), body), 201);
    }
    long one =
        measure(
            () ->
                assertEquals(
                    1,
                    courseQuery
                        .findAll(0, 1, prefix, null, null, null, "id", "asc")
                        .content()
                        .size()));
    long many =
        measure(
            () -> {
              var page = courseQuery.findAll(0, 33, prefix, null, null, null, "id", "asc");
              assertEquals(33, page.content().size());
              assertEquals(
                  33L, page.content().stream().map(item -> item.categoryId()).distinct().count());
              assertEquals(
                  33L, page.content().stream().map(item -> item.teacherId()).distinct().count());
            });
    assertTrue(many <= one + 1, "Course page queries grew from " + one + " to " + many);
    assertTrue(many <= 3, "Course page should not issue a query per association");
  }

  @Test
  void lessonSummaryProjectionUsesAtMostTwoStatementsForPage() throws Exception {
    Graph g = graph();
    for (int i = 2; i <= 100; i++) {
      expect(
          api(
              "POST",
              "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
              teacher.token(),
              Map.of("title", "Lesson " + i, "content", "x".repeat(2000), "position", i)),
          201);
    }
    long statements =
        measure(
            () -> {
              var page = lessons.findSummariesByModuleId(g.module(), PageRequest.of(0, 50));
              assertEquals(50, page.getContent().size());
              assertEquals(100L, page.getTotalElements());
              for (var lesson : page.getContent()) assertEquals(g.module(), lesson.moduleId());
            });
    assertTrue(
        statements >= 1 && statements <= 2,
        "Projection should use one page and at most one count query, got " + statements);
  }

  @Test
  void gradebookQueryCountDoesNotGrowPerStudentOrCell() throws Exception {
    Graph g = graph();
    long assignment = assignment(g.lesson());
    QuizGraph q = quiz(g.module());
    for (int i = 2; i <= 10; i++) {
      long lesson =
          id(
              expect(
                  api(
                      "POST",
                      "/api/courses/" + g.course() + "/modules/" + g.module() + "/lessons",
                      teacher.token(),
                      Map.of(
                          "title",
                          "Gradebook lesson " + i,
                          "content",
                          "Gradebook content",
                          "position",
                          i)),
                  201));
      assignment(lesson);
    }
    List<Actor> actors = new ArrayList<>();
    actors.add(student);
    for (int i = 1; i < 34; i++) actors.add(actor(List.of("STUDENT")));
    for (Actor actor : actors) {
      enroll(g.course(), actor);
      expect(
          api(
              "POST",
              "/api/assignments/" + assignment + "/submissions",
              actor.token(),
              Map.of("content", "Answer")),
          201);
      long attempt = start(q, actor);
      expect(
          api("POST", attemptPath(q) + "/" + attempt + "/submit", actor.token(), answers(q, true)),
          200);
    }
    long small =
        measure(
            () ->
                assertEquals(
                    1,
                    gradebook
                        .getGradebook(
                            g.course(),
                            teacher.id(),
                            false,
                            null,
                            null,
                            null,
                            null,
                            null,
                            0,
                            1,
                            "studentName",
                            "asc",
                            0,
                            20)
                        .students()
                        .content()
                        .size()));
    long large =
        measure(
            () -> {
              var result =
                  gradebook.getGradebook(
                      g.course(),
                      teacher.id(),
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      0,
                      33,
                      "studentName",
                      "asc",
                      0,
                      20);
              assertEquals(33, result.students().content().size());
              assertEquals(11, result.columns().size());
              for (var row : result.students().content()) assertEquals(11, row.results().size());
            });
    assertTrue(large <= small + 2, "Gradebook queries grew from " + small + " to " + large);
    assertTrue(large <= 12, "Expected bounded gradebook query count, got " + large);
  }

  @Override
  protected String login(String email, String password) {
    // Fixture authentication only; query counts do not include this preparation.
    return authService.login(new LoginRequest(email, password)).accessToken();
  }

  private QuizGraph populatedQuiz(int size) throws Exception {
    QuizGraph q = quiz(graph().module());
    for (int i = 2; i <= size; i++) {
      expect(
          api(
              "POST",
              "/api/quizzes/" + q.quiz() + "/questions",
              teacher.token(),
              questionPayload(i, 2)),
          201);
    }
    return q;
  }

  private long measure(Runnable action) {
    Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
    boolean wasEnabled = statistics.isStatisticsEnabled();
    try {
      statistics.setStatisticsEnabled(true);
      long before = statistics.getPrepareStatementCount();
      action.run();
      long result = statistics.getPrepareStatementCount() - before;
      assertTrue(result > 0, "No SQL measured: invalid measurement");
      return result;
    } finally {
      statistics.setStatisticsEnabled(wasEnabled);
    }
  }
}

package org.courseflow.integration.orm;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.assessment.service.ExpiredQuizAttemptsCleanup;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.integration.support.AbstractIntegrationTest;
import org.courseflow.integration.support.ConcurrentCalls;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.review.entity.CourseReview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Direct ORM and PostgreSQL checks; no mock repositories and no enclosing test transaction. */
class OrmPersistenceIT extends AbstractIntegrationTest {
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExpiredQuizAttemptsCleanup quizCleanup;

    @Test
    void categoryPersistFlushClearAndReload() {
        String name = unique("Category");
        long id = tx(em -> {
            var category = new Category(name, "Initial description");
            em.persist(category);
            em.flush();
            Long generated = category.getId();
            assertNotNull(generated);
            em.clear();
            var loaded = em.find(Category.class, generated);
            assertNotSame(category, loaded);
            assertEquals(name, loaded.getName());
            loaded.update(name, "Changed description");
            return generated;
        });
        assertEquals("Changed description", jdbc.queryForObject(
                "SELECT description FROM categories WHERE id = ?", String.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM categories WHERE id = ?", Long.class, id));
    }

    @Test
    void rollbackAfterFlushLeavesNoPersistedRow() {
        String name = unique("Rollback");
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(new Category(name, "Will roll back"));
            em.flush();
            assertEquals(1L, em.createQuery("SELECT count(c) FROM Category c WHERE c.name = :name", Long.class)
                    .setParameter("name", name).getSingleResult());
            em.getTransaction().rollback();
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
        assertEquals(0L, count("SELECT count(*) FROM categories WHERE name = ?", name));
    }

    @Test
    void cascadePersistStoresCourseModuleLessonAndSession() {
        Graph g = graph();
        assertEquals(1L, count("SELECT count(*) FROM modules WHERE course_id = ?", g.course()));
        assertEquals(1L, count("SELECT count(*) FROM lessons WHERE module_id = ?", g.module()));
        assertEquals(1L, count("SELECT count(*) FROM lesson_sessions WHERE lesson_id = ?", g.lesson()));
        assertEquals(1L, count("SELECT count(*) FROM course_tag WHERE course_id = ? AND tag_id = ?", g.course(), g.tag()));
    }

    @Test
    void lazyCollectionsLoadInsidePersistenceContext() {
        Graph g = graph();
        tx(em -> {
            var course = em.find(Course.class, g.course());
            assertFalse(emf.getPersistenceUnitUtil().isLoaded(course, "modules"), "Modules should be LAZY");
            var modules = course.getModules();
            assertEquals(1, modules.size());
            assertTrue(emf.getPersistenceUnitUtil().isLoaded(course, "modules"));
            assertEquals(g.module(), modules.get(0).getId());
            assertEquals(g.course(), modules.get(0).getCourse().getId());
            assertEquals(g.lesson(), modules.get(0).getLessons().get(0).getId());
            return null;
        });
    }

    @Test
    void manyToManyDetachRemovesJoinRowButPreservesTag() {
        Graph g = graph();
        tx(em -> {
            var course = em.find(Course.class, g.course());
            course.removeTag(em.find(Tag.class, g.tag()));
            return null;
        });
        assertEquals(0L, count("SELECT count(*) FROM course_tag WHERE course_id = ? AND tag_id = ?", g.course(), g.tag()));
        assertEquals(1L, count("SELECT count(*) FROM tags WHERE id = ?", g.tag()));
    }

    @Test
    void quizQuestionAnswerCascadePersistsOwningSide() {
        Graph g = graph();
        QuizGraph q = quizGraph(g);
        assertEquals(2L, count("SELECT count(*) FROM questions WHERE quiz_id = ?", q.quiz()));
        assertEquals(2L, count("SELECT count(*) FROM answer_options WHERE question_id = ?", q.firstQuestion()));
        assertEquals(1L, count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", q.attempt()));
        assertEquals(100, jdbc.queryForObject("SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, q.attempt()));
    }

    @Test
    void databaseRejectsCaseInsensitiveDuplicateCategoryAndTag() {
        Graph g = graph();
        String categoryName = jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, g.category());
        String tagName = jdbc.queryForObject("SELECT name FROM tags WHERE id = ?", String.class, g.tag());
        constraint("23505", () -> jdbc.update("INSERT INTO categories (name, description, version) VALUES (?, ?, 0)",
                categoryName.toUpperCase(Locale.ROOT), "Duplicate"));
        constraint("23505", () -> jdbc.update("INSERT INTO tags (name, version) VALUES (?, 0)", tagName.toUpperCase(Locale.ROOT)));
    }

    @Test
    void databaseRejectsInvalidCourseDurationAndPosition() {
        Graph g = graph();
        constraint("23514", () -> jdbc.update("UPDATE courses SET duration = 0 WHERE id = ?", g.course()));
        constraint("23514", () -> jdbc.update("UPDATE modules SET position = 0 WHERE id = ?", g.module()));
        constraint("23514", () -> jdbc.update("UPDATE lessons SET position = -1 WHERE id = ?", g.lesson()));
        assertEquals(56, jdbc.queryForObject("SELECT duration FROM courses WHERE id = ?", Integer.class, g.course()));
    }

    @Test
    void databaseRejectsDuplicateEnrollmentAndInvalidReviewRating() {
        Graph g = graph();
        long review = tx(em -> {
            var course = em.find(Course.class, g.course());
            var student = em.find(User.class, g.student());
            em.persist(Enrollment.createForStudent(student, course, Instant.now()));
            var entity = new CourseReview(course, student, 5, "Review", Instant.now());
            em.persist(entity);
            em.flush();
            return entity.getId();
        });
        constraint("23505", () -> jdbc.update("""
                INSERT INTO enrollments (course_id, student_id, enrolled_at, status)
                SELECT course_id, student_id, enrolled_at, status FROM enrollments
                WHERE course_id = ? AND student_id = ?
                """, g.course(), g.student()));
        constraint("23514", () -> jdbc.update("UPDATE course_reviews SET rating = 0 WHERE id = ?", review));
        constraint("23505", () -> jdbc.update("""
                INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at)
                SELECT course_id, student_id, rating, comment, created_at FROM course_reviews WHERE id = ?
                """, review));
        assertEquals(5, jdbc.queryForObject("SELECT rating FROM course_reviews WHERE id = ?", Integer.class, review));
    }

    @Test
    void databaseRejectsAnswerOptionFromDifferentQuestion() {
        Graph g = graph();
        QuizGraph q = quizGraph(g);
        constraint("23503", () -> jdbc.update("""
                INSERT INTO quiz_answer_selections (quiz_submission_id, question_id, answer_option_id)
                VALUES (?, ?, ?)
                """, q.attempt(), q.firstQuestion(), q.secondOption()));
        assertEquals(1L, count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", q.attempt()));
    }

    @Test
    void databaseRejectsDeletingReferencedCategory() {
        Graph g = graph();
        constraint("23503", () -> jdbc.update("DELETE FROM categories WHERE id = ?", g.category()));
        assertEquals(1L, count("SELECT count(*) FROM categories WHERE id = ?", g.category()));
    }

    @Test
    void hibernateVersionPreventsLostUpdateAcrossTwoTransactions() throws Exception {
        String name = unique("Optimistic");
        long id = tx(em -> {
            var category = new Category(name, "Initial");
            em.persist(category);
            em.flush();
            return category.getId();
        });
        CountDownLatch bothRead = new CountDownLatch(2);
        var results = ConcurrentCalls.pair(
                () -> updateSameVersion(id, "First", bothRead),
                () -> updateSameVersion(id, "Second", bothRead));
        assertEquals(1L, results.stream().filter(UpdateResult::committed).count());
        var winner = results.stream().filter(UpdateResult::committed).findFirst().orElseThrow();
        assertEquals(winner.description(), jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM categories WHERE id = ?", Long.class, id));
    }

    @Test
    void cleanupExpiresOverdueAttemptAndPreservesCompletedAttempt() {
        Graph g = graph();
        QuizGraph q = quizGraph(g);
        long expired = tx(em -> {
            var quiz = em.find(Quiz.class, q.quiz());
            var student = em.find(User.class, g.student());
            var attempt = new QuizSubmission(quiz, student, 2, Instant.now().minus(1, ChronoUnit.HOURS));
            em.persist(attempt);
            em.flush();
            return attempt.getId();
        });
        quizCleanup.expireAttempts();
        assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM quiz_submissions WHERE id = ?", String.class, expired));
        assertEquals(1L, count("SELECT count(*) FROM quiz_submissions WHERE id = ? AND finished_at = expires_at", expired));
        assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM quiz_submissions WHERE id = ?", String.class, q.attempt()));
    }

    private UpdateResult updateSameVersion(long id, String description, CountDownLatch bothRead) throws Exception {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            var category = em.find(Category.class, id);
            assertEquals(0L, category.getVersion());
            bothRead.countDown();
            assertTrue(bothRead.await(15, TimeUnit.SECONDS), "Both transactions must read the same version");
            category.update(category.getName(), description);
            try {
                em.getTransaction().commit();
                return new UpdateResult(true, description);
            } catch (OptimisticLockException ex) {
                return new UpdateResult(false, description);
            } catch (RollbackException ex) {
                for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                    if (cause instanceof OptimisticLockException) return new UpdateResult(false, description);
                }
                throw ex;
            }
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private Graph graph() {
        return tx(em -> {
            var teacher = new User("ORM teacher", unique("teacher") + "@courseflow.local", Set.of(UserRole.TEACHER), Instant.now());
            var student = new User("ORM student", unique("student") + "@courseflow.local", Set.of(UserRole.STUDENT), Instant.now());
            var category = new Category(unique("Category"), "ORM category");
            var tag = new Tag(unique("Tag"));
            em.persist(teacher);
            em.persist(student);
            em.persist(category);
            em.persist(tag);
            var course = new Course(unique("Course"), "ORM course", category, teacher, 56, LocalDate.now(ZoneOffset.UTC).plusDays(1));
            course.addTag(tag);
            var module = course.addModule("Module", "Module content", 1);
            var lesson = module.addLesson("Lesson", "Lesson content", null, 1);
            Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
            lesson.addSession(start, start.plus(1, ChronoUnit.HOURS), LessonSessionFormat.OFFLINE, null);
            em.persist(course); // cascading PERSIST: course -> module -> lesson -> session
            em.flush();
            return new Graph(course.getId(), module.getId(), lesson.getId(), student.getId(), category.getId(), tag.getId());
        });
    }

    private QuizGraph quizGraph(Graph g) {
        return tx(em -> {
            var quiz = new Quiz(em.find(org.courseflow.content.entity.Module.class, g.module()), "Quiz", 20);
            var first = quiz.addQuestion("First", QuestionType.SINGLE_CHOICE, 1);
            var firstOption = first.addAnswerOption("Correct", true);
            first.addAnswerOption("Wrong", false);
            var second = quiz.addQuestion("Second", QuestionType.SINGLE_CHOICE, 2);
            var secondOption = second.addAnswerOption("Correct", true);
            second.addAnswerOption("Wrong", false);
            em.persist(quiz);
            em.flush();
            Instant now = Instant.now();
            var attempt = new QuizSubmission(quiz, em.find(User.class, g.student()), 1, now);
            attempt.addSelection(first, firstOption);
            attempt.complete(100, now.plusSeconds(1));
            em.persist(attempt);
            em.flush();
            return new QuizGraph(quiz.getId(), first.getId(), secondOption.getId(), attempt.getId());
        });
    }

    private <T> T tx(Function<EntityManager, T> work) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            T result = work.apply(em);
            em.getTransaction().commit();
            return result;
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private static void constraint(String state, Runnable operation) {
        var error = assertThrows(DataIntegrityViolationException.class, operation::run);
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) return;
        }
        fail("Expected SQLSTATE " + state, error);
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private record Graph(long course, long module, long lesson, long student, long category, long tag) {}
    private record QuizGraph(long quiz, long firstQuestion, long secondOption, long attempt) {}
    private record UpdateResult(boolean committed, String description) {}
}

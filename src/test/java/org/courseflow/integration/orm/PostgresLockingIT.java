package org.courseflow.integration.orm;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.courseflow.assessment.repository.QuizSubmissionRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.catalog.repository.TagRepository;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.learning.repository.SubmissionRepository;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.courseflow.integration.support.ConcurrentCalls;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual PostgreSQL lock conflicts, plus production repository lock methods. */
class PostgresLockingIT extends AdvancedHttpTest {
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CategoryRepository categories;
    @Autowired private CourseRepository courses;
    @Autowired private TagRepository tags;
    @Autowired private UserRepository users;
    @Autowired private UserAccountRepository accounts;
    @Autowired private SubmissionRepository submissions;
    @Autowired private QuizSubmissionRepository attempts;

    @Test
    void categoryRepositoryWriteLockBlocksConcurrentWriterUntilCommit() throws Exception {
        long id = newCategory();
        assertRepositoryBlocks("categories", id, () -> categories.findByIdForUpdate(id).orElseThrow());
    }

    @Test
    void categoryRepositorySharedLockBlocksWriterUntilCommit() throws Exception {
        long id = newCategory();
        assertRepositoryBlocks("categories", id, () -> categories.findByIdForShare(id).orElseThrow());
    }

    @Test
    void courseRepositoryWriteLockBlocksConcurrentWriterUntilCommit() throws Exception {
        long id = course();
        assertRepositoryBlocks("courses", id, () -> courses.findByIdForUpdate(id).orElseThrow());
    }

    @Test
    void courseRepositorySharedLockBlocksWriterUntilCommit() throws Exception {
        long id = course();
        assertRepositoryBlocks("courses", id, () -> courses.findByIdForShare(id).orElseThrow());
    }

    @Test
    void tagRepositoryWriteLockBlocksConcurrentWriterUntilCommit() throws Exception {
        long id = newTag();
        assertRepositoryBlocks("tags", id, () -> tags.findByIdForUpdate(id).orElseThrow());
    }

    @Test
    void tagRepositorySharedLockBlocksWriterUntilCommit() throws Exception {
        long id = newTag();
        assertRepositoryBlocks("tags", id, () -> tags.findByIdForShare(id).orElseThrow());
    }

    @Test
    void userRepositoryWriteLockBlocksConcurrentWriterUntilCommit() throws Exception {
        long id = student.id();
        assertRepositoryBlocks("users", id, () -> users.findByIdForUpdate(id).orElseThrow());
    }

    @Test
    void accountRepositoryLockBlocksConcurrentCredentialMutation() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM user_accounts WHERE user_id = ?", Long.class, student.id());
        assertRepositoryBlocks("user_accounts", id, () -> accounts.findByIdForUpdate(id).orElseThrow());
    }

    @Test
    void submissionGradingRepositoryLockBlocksConcurrentWriter() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        long assignment = assignment(g.lesson());
        long submission = id(expect(api("POST", "/api/assignments/" + assignment + "/submissions", student.token(),
                java.util.Map.of("content", "Answer")), 201));
        assertRepositoryBlocks("submissions", submission, () -> submissions.findForGrading(submission, assignment).orElseThrow());
    }

    @Test
    void ownedQuizAttemptRepositoryLockBlocksConcurrentWriter() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        QuizGraph q = quiz(g.module());
        long attempt = start(q, student);
        assertRepositoryBlocks("quiz_submissions", attempt, () -> attempts.findOwnedForUpdate(attempt, q.quiz(), student.id()).orElseThrow());
    }

    @Test
    void latestQuizAttemptRepositoryLockBlocksConcurrentWriter() throws Exception {
        Graph g = graph();
        enroll(g.course(), student);
        QuizGraph q = quiz(g.module());
        long attempt = start(q, student);
        assertRepositoryBlocks("quiz_submissions", attempt,
                () -> attempts.findFirstByQuiz_IdAndStudent_IdOrderByAttemptNumberDesc(q.quiz(), student.id()).orElseThrow());
    }

    @Test
    void twoCourseRepositorySharedLocksCanCoexist() throws Exception {
        long id = course();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        var tx = new TransactionTemplate(transactionManager);
        tx.setTimeout(15);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '8s'");
                courses.findByIdForShare(id).orElseThrow();
                firstLocked.countDown();
                await(secondLocked);
            }));
            assertTrue(firstLocked.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '3s'");
                courses.findByIdForShare(id).orElseThrow();
                secondLocked.countDown();
            }));
            second.get(10, TimeUnit.SECONDS);
            first.get(10, TimeUnit.SECONDS);
        } finally {
            secondLocked.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void lockedCategoryDoesNotBlockUnrelatedRowOrMvccRead() throws Exception {
        long locked = newCategory();
        long free = newCategory();
        try (Connection a = transaction(); Connection b = transaction()) {
            try {
                lockRow(a, "categories", locked);
                execute(b, "SET LOCAL lock_timeout = '1s'");
                assertEquals(locked, scalar(b, "SELECT id FROM categories WHERE id = ?", locked));
                assertEquals(1, update(b, "UPDATE categories SET description = 'Independent' WHERE id = ?", free));
                b.commit();
            } finally {
                a.rollback();
                b.rollback();
            }
        }
    }

    @Test
    void rowLockNowaitReturnsSqlState55P03() throws Exception {
        long id = newCategory();
        try (Connection a = transaction(); Connection b = transaction()) {
            try {
                lockRow(a, "categories", id);
                SQLException error = assertThrows(SQLException.class, () -> scalar(b,
                        "SELECT id FROM categories WHERE id = ? FOR UPDATE NOWAIT", id));
                assertEquals("55P03", error.getSQLState());
            } finally {
                b.rollback();
                a.rollback();
            }
        }
    }

    @Test
    void lockTimeoutRejectsBlockedWriteAndRollbackRestoresConnection() throws Exception {
        long id = newCategory();
        try (Connection a = transaction(); Connection b = transaction()) {
            try {
                lockRow(a, "categories", id);
                execute(b, "SET LOCAL lock_timeout = '300ms'");
                SQLException error = assertThrows(SQLException.class, () -> update(b,
                        "UPDATE categories SET description = 'Blocked' WHERE id = ?", id));
                assertEquals("55P03", error.getSQLState());
                b.rollback();
                a.rollback();
                assertEquals(1, update(b, "UPDATE categories SET description = 'Recovered' WHERE id = ?", id));
                b.commit();
            } finally {
                b.rollback();
                a.rollback();
            }
        }
        assertEquals("Recovered", jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, id));
    }

    @Test
    void tableAccessExclusiveLockRejectsConcurrentAccessShareNowait() throws Exception {
        try (Connection a = transaction(); Connection b = transaction()) {
            try {
                execute(a, "LOCK TABLE categories IN ACCESS EXCLUSIVE MODE");
                SQLException error = assertThrows(SQLException.class, () -> execute(b,
                        "LOCK TABLE categories IN ACCESS SHARE MODE NOWAIT"));
                assertEquals("55P03", error.getSQLState());
                b.rollback();
                a.rollback();
                execute(b, "LOCK TABLE categories IN ACCESS SHARE MODE NOWAIT");
            } finally {
                b.rollback();
                a.rollback();
            }
        }
    }

    @Test
    void tableShareLockAllowsReadButRejectsUpdate() throws Exception {
        long id = newCategory();
        try (Connection a = transaction(); Connection b = transaction()) {
            try {
                execute(a, "LOCK TABLE categories IN SHARE MODE");
                assertEquals(id, scalar(b, "SELECT id FROM categories WHERE id = ?", id));
                execute(b, "SET LOCAL lock_timeout = '300ms'");
                SQLException error = assertThrows(SQLException.class, () -> update(b,
                        "UPDATE categories SET description = 'Blocked' WHERE id = ?", id));
                assertEquals("55P03", error.getSQLState());
            } finally {
                b.rollback();
                a.rollback();
            }
        }
    }

    @Test
    void postgresDetectsRealDeadlockAndRollsBackExactlyOneTransaction() throws Exception {
        long a = newCategory();
        long b = newCategory();
        CountDownLatch firstUpdates = new CountDownLatch(2);
        List<DeadlockResult> results = ConcurrentCalls.pair(
                () -> deadlockTransaction(a, b, "First", firstUpdates),
                () -> deadlockTransaction(b, a, "Second", firstUpdates));
        assertEquals(1L, results.stream().filter(r -> r.state().equals("COMMITTED")).count());
        assertEquals(1L, results.stream().filter(r -> r.state().equals("40P01")).count());
        String winner = results.stream().filter(r -> r.state().equals("COMMITTED")).findFirst().orElseThrow().value();
        assertEquals(winner, jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, a));
        assertEquals(winner, jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, b));
    }

    private DeadlockResult deadlockTransaction(long first, long second, String value, CountDownLatch barrier) throws Exception {
        try (Connection c = transaction()) {
            try {
                execute(c, "SET LOCAL statement_timeout = '20s'");
                update(c, "UPDATE categories SET description = ? WHERE id = ?", value, first);
                barrier.countDown();
                await(barrier);
                update(c, "UPDATE categories SET description = ? WHERE id = ?", value, second);
                c.commit();
                return new DeadlockResult("COMMITTED", value);
            } catch (SQLException error) {
                c.rollback();
                if (!"40P01".equals(error.getSQLState())) throw error;
                return new DeadlockResult(error.getSQLState(), value);
            } finally {
                c.rollback();
            }
        }
    }

    /** Uses pg_blocking_pids rather than guessing overlap from sleep duration. */
    private void assertRepositoryBlocks(String table, long id, Runnable repositoryLock) throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        tx.setTimeout(25);
        var executor = Executors.newSingleThreadExecutor();
        try (Connection contender = transaction()) {
            int contenderPid = (int) scalar(contender, "SELECT pg_backend_pid()");
            execute(contender, "SET LOCAL lock_timeout = '15s'");
            // Run repository lock on current thread, then wait for PostgreSQL to report blocking.
            var future = tx.execute(status -> {
                int holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                repositoryLock.run();
                var blocked = executor.submit(() -> {
                    long result = scalar(contender, "SELECT id FROM " + table + " WHERE id = ? FOR UPDATE", id);
                    contender.commit();
                    return result;
                });
                try {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                    boolean observed = false;
                    while (System.nanoTime() < deadline) {
                        if (Boolean.TRUE.equals(jdbc.queryForObject(
                                "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holderPid, contenderPid))) {
                            observed = true;
                            break;
                        }
                        if (blocked.isDone()) break;
                        Thread.sleep(25);
                    }
                    assertTrue(observed, "PostgreSQL did not report the repository transaction as blocker for " + table);
                    assertFalse(blocked.isDone());
                    return blocked;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            });
            assertNotNull(future);
            assertEquals(id, future.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
        }
    }

    private long newCategory() throws Exception {
        return id(expect(api("POST", "/api/categories", admin, java.util.Map.of("name", "Lock-" + unique(), "description", "Original")), 201));
    }

    private long newTag() throws Exception {
        return id(expect(api("POST", "/api/tags", admin, java.util.Map.of("name", "Lock-" + unique())), 201));
    }

    private Connection transaction() throws SQLException {
        Connection c = dataSource.getConnection();
        try {
            c.setAutoCommit(false);
            return c;
        } catch (SQLException e) {
            c.close();
            throw e;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Transaction barrier timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static void lockRow(Connection c, String table, long id) throws SQLException {
        assertEquals(id, scalar(c, "SELECT id FROM " + table + " WHERE id = ? FOR UPDATE", id));
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }

    private static long scalar(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement.executeUpdate();
        }
    }

    private record DeadlockResult(String state, String value) {}
}

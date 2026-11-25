package org.courseflow.integration.concurrency;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.courseflow.auth.repository.PasswordResetTokenRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.auth.service.SystemAdminBootstrapService;
import org.courseflow.common.system.DatabaseInvariantInitializer;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Hold a real row lock from an independent transaction while the HTTP API runs. */
@TestPropertySource(
    properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout TO '10s'")
class ControlledTransactionIT extends AdvancedHttpTest {
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private UserAccountRepository accounts;
  @Autowired private UserRepository users;
  @Autowired private PasswordResetTokenRepository resetTokens;
  @Autowired private SystemAdminBootstrapService bootstrap;
  @Autowired private DatabaseInvariantInitializer initializer;

  @Test
  void waitingUpdateRejectsStaleVersionAfterConcurrentCommit() throws Exception {
    Category initial = createCategory();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try {
        int pid = lockCategory(holder, initial.id());
        Future<Response> waiting = executor.submit(() -> update(initial, "Rejected stale edit"));
        awaitBlocked(pid, waiting);
        mutate(holder, initial.id(), "Committed concurrent edit");
        holder.commit();
        Object error = expect(waiting.get(20, TimeUnit.SECONDS), 409);
        assertEquals("CONCURRENT_CONFLICT", field(error, "code"));
        assertCategory(initial, "Committed concurrent edit", initial.version() + 1);
        Category latest = new Category(initial.id(), initial.name(), initial.version() + 1);
        assertEquals(
            latest.version() + 1, number(expect(update(latest, "Fresh edit"), 200), "version"));
        assertCategory(initial, "Fresh edit", initial.version() + 2);
      } finally {
        holder.rollback();
      }
    } finally {
      shutdown(executor);
    }
  }

  @Test
  void waitingUpdateUsesOriginalVersionAfterConcurrentRollback() throws Exception {
    Category initial = createCategory();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try {
        int pid = lockCategory(holder, initial.id());
        mutate(holder, initial.id(), "Uncommitted edit");
        Future<Response> waiting =
            executor.submit(() -> update(initial, "HTTP edit after rollback"));
        awaitBlocked(pid, waiting);
        holder.rollback();
        Object result = expect(waiting.get(20, TimeUnit.SECONDS), 200);
        assertEquals(initial.id(), id(result));
        assertEquals(initial.version() + 1, number(result, "version"));
        assertCategory(initial, "HTTP edit after rollback", initial.version() + 1);
      } finally {
        holder.rollback();
      }
    } finally {
      shutdown(executor);
    }
  }

  @Test
  void lockTimeoutReturnsConflictAndNextHttpRequestSucceeds() throws Exception {
    Category initial = createCategory();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try {
        int pid = lockCategory(holder, initial.id());
        Future<Response> waiting = executor.submit(() -> update(initial, "Timed out edit"));
        awaitBlocked(pid, waiting);
        Object error = expect(waiting.get(25, TimeUnit.SECONDS), 409);
        assertEquals("CONCURRENT_CONFLICT", field(error, "code"));
        // The blocking transaction is still open; the failed operation must not change the row.
        assertCategory(initial, "Initial description", initial.version());
      } finally {
        holder.rollback();
      }
      Object result = expect(update(initial, "Recovered edit"), 200);
      assertEquals(initial.version() + 1, number(result, "version"));
      assertCategory(initial, "Recovered edit", initial.version() + 1);
    } finally {
      shutdown(executor);
    }
  }

  @Test
  void accountLookupByUserIdAcquiresRealWriteLock() throws Exception {
    long account =
        jdbc.queryForObject(
            "SELECT id FROM user_accounts WHERE user_id = ?", Long.class, student.id());
    repositoryWaitsForRow(
        "user_accounts",
        account,
        () -> {
          assertEquals(
              account,
              accounts.findByUserIdForUpdate(student.id()).orElseThrow().getId().longValue());
        });
  }

  @Test
  void systemAdminLookupAcquiresRealWriteLock() throws Exception {
    long systemAdmin = jdbc.queryForObject("SELECT id FROM users WHERE system_admin", Long.class);
    repositoryWaitsForRow(
        "users",
        systemAdmin,
        () -> {
          assertEquals(
              systemAdmin, users.findSystemAdminForUpdate().orElseThrow().getId().longValue());
        });
  }

  @Test
  void passwordResetTokenLookupAcquiresRealWriteLock() throws Exception {
    String hash = (unique() + unique()).replace("-", "");
    long token =
        jdbc.queryForObject(
            """
                INSERT INTO password_reset_tokens (user_account_id, token_hash, expires_at)
                SELECT id, ?, CURRENT_TIMESTAMP + INTERVAL '30 minutes'
                FROM user_accounts WHERE user_id = ? RETURNING id
                """,
            Long.class,
            hash,
            student.id());
    repositoryWaitsForRow(
        "password_reset_tokens",
        token,
        () -> {
          assertEquals(
              token, resetTokens.findByTokenHashForUpdate(hash).orElseThrow().getId().longValue());
        });
  }

  @Test
  void systemAdminBootstrapWaitsForAdvisoryLockAndPreservesExistingAccounts() throws Exception {
    long adminId = jdbc.queryForObject("SELECT id FROM users WHERE system_admin", Long.class);
    long userCount = count("SELECT count(*) FROM users");
    long accountCount = count("SELECT count(*) FROM user_accounts");
    waitsForAdvisoryLock(bootstrap::initialize);
    assertEquals(1L, count("SELECT count(*) FROM users WHERE system_admin"));
    assertEquals(
        adminId,
        jdbc.queryForObject("SELECT id FROM users WHERE system_admin", Long.class).longValue());
    assertEquals(userCount, count("SELECT count(*) FROM users"));
    assertEquals(accountCount, count("SELECT count(*) FROM user_accounts"));
    assertEquals(student.id(), id(expect(api("GET", "/api/users/me", student.token(), null), 200)));
  }

  @Test
  void invariantInitializerWaitsForAdvisoryLockAndPreservesCourseData() throws Exception {
    Category category = createCategory();
    long course = course();
    long before = count("SELECT count(*) FROM courses");
    waitsForAdvisoryLock(() -> initializer.run(null));
    assertEquals(before, count("SELECT count(*) FROM courses"));
    assertEquals(1L, count("SELECT count(*) FROM courses WHERE id = ?", course));
    assertCategory(category, "Initial description", category.version());
    assertEquals(
        1L,
        count(
            """
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'quiz_answer_selections'::regclass
                  AND conname = 'fk_cf_selection_option_question'
                """));
  }

  private void repositoryWaitsForRow(String table, long row, Runnable lockOperation)
      throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try {
        int pid = lockRow(holder, table, row);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(25);
        Future<?> waiting =
            executor.submit(() -> transaction.executeWithoutResult(status -> lockOperation.run()));
        awaitBlocked(pid, waiting);
        holder.rollback();
        waiting.get(20, TimeUnit.SECONDS);
      } finally {
        holder.rollback();
      }
    } finally {
      shutdown(executor);
    }
  }

  private void waitsForAdvisoryLock(Runnable operation) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try {
        int pid;
        try (var statement = holder.createStatement();
            var result = statement.executeQuery("SELECT pg_backend_pid()")) {
          assertTrue(result.next());
          pid = result.getInt(1);
        }
        try (var statement = holder.createStatement()) {
          statement.execute("SELECT pg_advisory_xact_lock(62130001)");
        }
        Future<?> waiting = executor.submit(operation);
        awaitBlocked(pid, waiting);
        holder.commit();
        waiting.get(20, TimeUnit.SECONDS);
      } finally {
        holder.rollback();
      }
    } finally {
      shutdown(executor);
    }
  }

  private Category createCategory() throws Exception {
    String name = "Controlled-" + unique();
    Object body =
        expect(
            api(
                "POST",
                "/api/categories",
                admin,
                Map.of("name", name, "description", "Initial description")),
            201);
    return new Category(id(body), name, number(body, "version"));
  }

  private Response update(Category category, String description) throws Exception {
    return api(
        "PUT",
        "/api/categories/" + category.id(),
        admin,
        Map.of("name", category.name(), "description", description, "version", category.version()));
  }

  private void assertCategory(Category category, String description, long version)
      throws Exception {
    Object body = expect(api("GET", "/api/categories/" + category.id(), admin, null), 200);
    assertEquals(category.id(), id(body));
    assertEquals(category.name(), field(body, "name"));
    assertEquals(description, field(body, "description"));
    assertEquals(version, number(body, "version"));
    assertEquals(
        description,
        jdbc.queryForObject(
            "SELECT description FROM categories WHERE id = ?", String.class, category.id()));
    assertEquals(
        version,
        jdbc.queryForObject(
                "SELECT version FROM categories WHERE id = ?", Long.class, category.id())
            .longValue());
  }

  private static int lockCategory(Connection connection, long id) throws SQLException {
    return lockRow(connection, "categories", id);
  }

  private static int lockRow(Connection connection, String table, long id) throws SQLException {
    // Table identifiers are fixed test constants, never request data.
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT pg_backend_pid() FROM " + table + " WHERE id = ? FOR UPDATE")) {
      statement.setLong(1, id);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next(), "Missing row to lock");
        return result.getInt(1);
      }
    }
  }

  private static void mutate(Connection connection, long id, String description)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE categories SET description = ?, version = version + 1 WHERE id = ?")) {
      statement.setString(1, description);
      statement.setLong(2, id);
      assertEquals(1, statement.executeUpdate());
    }
  }

  private void awaitBlocked(int blocker, Future<?> request) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
    while (System.nanoTime() < deadline) {
      if (count(
              """
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND pid <> ?
                      AND ? = ANY(pg_blocking_pids(pid))
                    """,
              blocker,
              blocker)
          > 0) {
        assertFalse(request.isDone(), "Blocked operation already finished");
        return;
      }
      assertFalse(request.isDone(), "Operation did not wait for the database lock");
      Thread.sleep(25);
    }
    throw new AssertionError("PostgreSQL did not observe the expected lock wait");
  }

  private static void shutdown(ExecutorService executor) throws InterruptedException {
    executor.shutdownNow();
    assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "Worker did not stop");
  }

  private record Category(long id, String name, long version) {}
}

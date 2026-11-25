package org.courseflow.integration.recovery;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

/** Real connection failures against the disposable Testcontainers database only. */
@TestPropertySource(
    properties = {
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=0",
      "spring.datasource.hikari.connection-timeout=1500",
      "spring.datasource.hikari.validation-timeout=1000"
    })
class DatabaseAvailabilityIT extends AdvancedHttpTest {
  private static final String TEST_DATABASE = "courseflow_test";
  private static final String CATEGORY_PATH = "/api/categories/";

  @Autowired private DataSource dataSource;

  @Value("${spring.datasource.username}")
  private String databaseUser;

  @Value("${spring.datasource.password}")
  private String databasePassword;

  @Test
  void exhaustedPoolRejectsDatabaseRequestAndRecoversWhenConnectionsReturn() throws Exception {
    Object original = readCategory();
    HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
    assertEquals(3, pool.getMaximumPoolSize());
    try (Connection first = dataSource.getConnection();
        Connection second = dataSource.getConnection();
        Connection third = dataSource.getConnection()) {
      assertEquals(3, pool.getHikariPoolMXBean().getActiveConnections());
      assertEquals(0, pool.getHikariPoolMXBean().getIdleConnections());
      assertDatabaseError(api("GET", CATEGORY_PATH + categoryId, null, null));
      assertHealth("readiness", 503, "DOWN");
      assertHealth("liveness", 200, "UP");
      assertFalse(first.isClosed());
      assertFalse(second.isClosed());
      assertFalse(third.isClosed());
    }
    assertRecovered(original);
  }

  @Test
  void unavailableDatabaseReportsNotReadyAndRecoversWithoutApplicationRestart() throws Exception {
    Graph graph = graph();
    enroll(graph.course(), student);
    Object original = readCategory();
    long enrollmentId =
        jdbc.queryForObject(
            "SELECT id FROM enrollments WHERE course_id = ? AND student_id = ?",
            Long.class,
            graph.course(),
            student.id());
    String coursePath = "/api/courses/" + graph.course();
    Object originalCourse = expect(api("GET", coursePath, null, null), 200);
    try (Connection maintenance = maintenanceConnection()) {
      // Refuse all new connections, including Hikari's replacement attempts.
      // Always restore connectivity from the independent postgres database.
      try {
        execute(maintenance, "ALTER DATABASE courseflow_test ALLOW_CONNECTIONS false");
        terminateDatabaseConnections(maintenance);
        assertEquals(0L, databaseConnections(maintenance));
        assertDatabaseError(api("GET", CATEGORY_PATH + categoryId, null, null));
        assertHealth("readiness", 503, "DOWN");
        assertHealth("liveness", 200, "UP");
        assertEquals(0L, databaseConnections(maintenance));
      } finally {
        execute(maintenance, "ALTER DATABASE courseflow_test ALLOW_CONNECTIONS true");
      }
    }
    assertRecovered(original);
    assertEquals(originalCourse, expect(api("GET", coursePath, null, null), 200));
    assertEquals(
        enrollmentId,
        id(expect(api("GET", coursePath + "/enrollments/me", student.token(), null), 200)));
  }

  @Test
  void terminatedTransactionRollsBackUncommittedChangeAndPoolRemainsUsable() throws Exception {
    Object original = readCategory();
    try (Connection maintenance = maintenanceConnection();
        Connection victim = dataSource.getConnection()) {
      victim.setAutoCommit(false);
      int pid;
      try (Statement statement = victim.createStatement();
          ResultSet result = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(result.next());
        pid = result.getInt(1);
      }
      try (PreparedStatement update =
          victim.prepareStatement(
              "UPDATE categories SET description = ?, version = version + 1 WHERE id = ?")) {
        update.setString(1, "Uncommitted change");
        update.setLong(2, categoryId);
        assertEquals(1, update.executeUpdate());
      }
      try (PreparedStatement select =
          victim.prepareStatement("SELECT description FROM categories WHERE id = ?")) {
        select.setLong(1, categoryId);
        try (ResultSet result = select.executeQuery()) {
          assertTrue(result.next());
          assertEquals("Uncommitted change", result.getString(1));
        }
      }
      assertEquals(original, readCategory());
      try (PreparedStatement kill =
          maintenance.prepareStatement(
              """
                    SELECT pg_terminate_backend(pid, 5000)
                    FROM pg_stat_activity WHERE datname = ? AND pid = ?
                    """)) {
        kill.setString(1, TEST_DATABASE);
        kill.setInt(2, pid);
        try (ResultSet result = kill.executeQuery()) {
          assertTrue(result.next(), "Victim must belong to the temporary database");
          assertTrue(result.getBoolean(1), "Backend termination must succeed");
        }
      }
      try (Statement statement = victim.createStatement()) {
        statement.executeQuery("SELECT 1");
        throw new AssertionError("Terminated connection unexpectedly remained usable");
      } catch (SQLException expected) {
        String state = expected.getSQLState();
        assertNotNull(state);
        assertTrue(
            state.startsWith("08") || state.equals("57P01"),
            "Expected connection failure or administrative shutdown, got " + state);
      }
    }
    assertRecovered(original);
  }

  private Object readCategory() throws Exception {
    Object body = expect(api("GET", CATEGORY_PATH + categoryId, null, null), 200);
    assertEquals(categoryId, id(body));
    return body;
  }

  private void assertRecovered(Object original) throws Exception {
    // Poll a real readiness probe; no fixed sleep as evidence of recovery.
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    Response readiness;
    do {
      readiness = api("GET", "/actuator/health/readiness", null, null);
      if (readiness.status() == 200 && "UP".equals(field(readiness.body(), "status"))) {
        break;
      }
      Thread.sleep(100);
    } while (System.nanoTime() < deadline);
    assertEquals(200, readiness.status(), "Database did not recover");
    assertEquals("UP", field(readiness.body(), "status"));
    assertEquals(original, readCategory(), "Previously committed data changed");
    assertEquals(student.id(), id(expect(api("GET", "/api/users/me", student.token(), null), 200)));
    String description = "Recovered-" + unique();
    Object updated =
        expect(
            api(
                "PUT",
                CATEGORY_PATH + categoryId,
                admin,
                Map.of(
                    "name",
                    field(original, "name"),
                    "description",
                    description,
                    "version",
                    number(original, "version"))),
            200);
    assertEquals(categoryId, id(updated));
    assertEquals(number(original, "version") + 1, number(updated, "version"));
    assertEquals(description, field(updated, "description"));
    assertEquals(updated, readCategory());
    assertEquals(
        description,
        jdbc.queryForObject(
            "SELECT description FROM categories WHERE id = ?", String.class, categoryId));
  }

  private void assertHealth(String group, int status, String health) throws Exception {
    Object body = expect(api("GET", "/actuator/health/" + group, null, null), status);
    assertEquals(health, field(body, "status"));
  }

  private void assertDatabaseError(Response response) {
    Object error = expect(response, 500);
    assertEquals("INTERNAL_ERROR", field(error, "code"));
    assertEquals("Internal server error", field(error, "message"));
    assertEquals(CATEGORY_PATH + categoryId, field(error, "path"));
    assertEquals(500L, number(error, "status"));
    assertNotNull(response.header("X-Request-Id"));
    assertEquals(response.header("X-Request-Id"), field(error, "requestId"));
  }

  private Connection maintenanceConnection() throws SQLException {
    String url;
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT current_database()")) {
      assertTrue(result.next());
      assertEquals(TEST_DATABASE, result.getString(1), "Never disrupt a non-test database");
      url = connection.getMetaData().getURL();
    }
    assertTrue(
        url.matches("jdbc:postgresql://[^/]+/courseflow_test(?:\\?.*)?"),
        "Expected the Testcontainers JDBC URL");
    assertEquals("test", databaseUser, "Expected the disposable Testcontainers database user");
    Properties properties = new Properties();
    properties.setProperty("user", databaseUser);
    properties.setProperty("password", databasePassword);
    properties.setProperty("connectTimeout", "5");
    properties.setProperty("socketTimeout", "15");
    // Drop test URL options; the maintenance connection must remain independent of the pool.
    String maintenanceUrl = url.replaceFirst("/courseflow_test(?:\\?.*)?$", "/postgres");
    return DriverManager.getConnection(maintenanceUrl, properties);
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(10);
      statement.execute(sql);
    }
  }

  private static void terminateDatabaseConnections(Connection maintenance) throws SQLException {
    try (PreparedStatement statement =
        maintenance.prepareStatement(
            """
                SELECT pg_terminate_backend(pid, 5000)
                FROM pg_stat_activity WHERE datname = ? AND backend_type = 'client backend'
                """)) {
      statement.setString(1, TEST_DATABASE);
      try (ResultSet result = statement.executeQuery()) {
        int terminated = 0;
        while (result.next()) {
          assertTrue(result.getBoolean(1), "Existing database connection did not terminate");
          terminated++;
        }
        // The Hikari pool must have at least one connection before the outage.
        assertTrue(terminated > 0, "No actual database connections were interrupted");
      }
    }
  }

  private static long databaseConnections(Connection maintenance) throws SQLException {
    try (PreparedStatement statement =
        maintenance.prepareStatement(
            "SELECT count(*) FROM pg_stat_activity WHERE datname = ? AND backend_type = 'client backend'")) {
      statement.setString(1, TEST_DATABASE);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        return result.getLong(1);
      }
    }
  }
}

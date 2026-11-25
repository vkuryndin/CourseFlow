package org.courseflow.common.system;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.Version;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class SystemInfoService {
  static final int CACHE_SECONDS = 60;
  private static final int QUERY_TIMEOUT_SECONDS = 3;
  static final String IDENTITY_SQL =
      """
            SELECT current_database(), current_user, current_schema(),
                   host(inet_server_addr()), inet_server_port()
            """;
  static final String SIZE_SQL = "SELECT pg_database_size(current_database())";
  static final String TABLE_COUNT_SQL =
      """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
            """;

  private final DataSource dataSource;
  private final Environment environment;
  private final Clock clock;
  private SystemInfoResponse.Snapshot cached;
  private long cachedNanos;

  public SystemInfoService(DataSource dataSource, Environment environment, Clock clock) {
    this.dataSource = dataSource;
    this.environment = environment;
    this.clock = clock;
  }

  // Lazy, single-flight refresh. No background job, no extra connections per metric.
  public synchronized SystemInfoResponse.Snapshot snapshot() {
    if (cached != null
        && System.nanoTime() - cachedNanos < TimeUnit.SECONDS.toNanos(CACHE_SECONDS)) {
      return cached;
    }
    var application = applicationInfo();
    var runtime = runtimeInfo();
    var database = databaseInfo();
    var pool = poolInfo();
    var mail = mailInfo();
    cached =
        new SystemInfoResponse.Snapshot(
            clock.instant(), CACHE_SECONDS, application, runtime, database, pool, mail);
    cachedNanos = System.nanoTime();
    return cached;
  }

  private SystemInfoResponse.Application applicationInfo() {
    Properties build = resourceProperties("/META-INF/build-info.properties");
    Properties git = resourceProperties("/git.properties");
    String version = build.getProperty("build.version");
    if (version == null) {
      version = SystemInfoService.class.getPackage().getImplementationVersion();
    }
    return new SystemInfoResponse.Application(
        environment.getProperty("spring.application.name", "courseflow"),
        version,
        SpringBootVersion.getVersion(),
        Version.getVersionString(),
        List.of(environment.getActiveProfiles()),
        List.of(environment.getDefaultProfiles()),
        environment.getProperty("spring.jpa.hibernate.ddl-auto"),
        build.getProperty("build.time"),
        git.getProperty("git.commit.id", git.getProperty("git.commit.id.abbrev")));
  }

  private SystemInfoResponse.RuntimeInfo runtimeInfo() {
    var runtime = ManagementFactory.getRuntimeMXBean();
    var os = ManagementFactory.getOperatingSystemMXBean();
    Long total = null;
    Long free = null;
    if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
      total = nonNegative(extended.getTotalMemorySize());
      free = nonNegative(extended.getFreeMemorySize());
    }
    String hostname = safeHost(environment.getProperty("HOSTNAME"));
    if (hostname == null) {
      hostname = safeHost(environment.getProperty("COMPUTERNAME"));
    }
    return new SystemInfoResponse.RuntimeInfo(
        hostname,
        System.getProperty("java.version"),
        System.getProperty("java.vendor"),
        runtime.getVmName(),
        os.getName(),
        os.getVersion(),
        os.getArch(),
        os.getAvailableProcessors(),
        total,
        free,
        Instant.ofEpochMilli(runtime.getStartTime()),
        runtime.getUptime() / 1000,
        ZoneId.systemDefault().getId());
  }

  private SystemInfoResponse.Database databaseInfo() {
    String product = null;
    String version = null;
    String driver = null;
    String driverVersion = null;
    String address = null;
    String name = null;
    String user = null;
    String schema = null;
    Integer port = null;
    Long size = null;
    Long tables = null;
    Long elapsed = null;
    String status = "UNAVAILABLE";
    try (Connection connection = dataSource.getConnection()) {
      status = "PARTIAL";
      try {
        var metadata = connection.getMetaData();
        product = metadata.getDatabaseProductName();
        version = metadata.getDatabaseProductVersion();
        driver = metadata.getDriverName();
        driverVersion = metadata.getDriverVersion();
      } catch (SQLException ignored) {
        // Keep remaining diagnostics; never expose JDBC error text (may contain secrets).
      }
      long start = System.nanoTime();
      try (var statement = connection.createStatement()) {
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        try (ResultSet row = statement.executeQuery(IDENTITY_SQL)) {
          if (row.next()) {
            name = row.getString(1);
            user = row.getString(2);
            schema = row.getString(3);
            address = row.getString(4);
            int p = row.getInt(5);
            port = row.wasNull() ? null : p;
            elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            status = "CONNECTED";
          }
        }
      } catch (SQLException ignored) {
        // No error text in the response. Other sections remain available.
      }
      // Only read queries. Independent optional fields (e.g. database size permission).
      if ("CONNECTED".equals(status)) {
        size = scalar(connection, SIZE_SQL);
        tables = scalar(connection, TABLE_COUNT_SQL);
        if (product == null
            || version == null
            || driver == null
            || driverVersion == null
            || size == null
            || tables == null) {
          status = "PARTIAL";
        }
      }
    } catch (SQLException ignored) {
      // Connection acquisition/close failed. Do not send exception messages.
    }
    return new SystemInfoResponse.Database(
        status,
        product,
        version,
        driver,
        driverVersion,
        address,
        port,
        name,
        user,
        schema,
        size,
        tables,
        elapsed);
  }

  private static Long scalar(Connection connection, String sql) {
    try (var statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      try (ResultSet row = statement.executeQuery(sql)) {
        if (row.next()) {
          long result = row.getLong(1);
          return row.wasNull() ? null : nonNegative(result);
        }
      }
    } catch (SQLException ignored) {
      // Optional metric; null means unavailable, never zero.
    }
    return null;
  }

  private SystemInfoResponse.Pool poolInfo() {
    try {
      HikariDataSource hikari =
          dataSource instanceof HikariDataSource direct
              ? direct
              : dataSource.isWrapperFor(HikariDataSource.class)
                  ? dataSource.unwrap(HikariDataSource.class)
                  : null;
      if (hikari != null) {
        HikariPoolMXBean stats = hikari.getHikariPoolMXBean();
        return new SystemInfoResponse.Pool(
            hikari.getPoolName(),
            hikari.getMinimumIdle(),
            hikari.getMaximumPoolSize(),
            hikari.getConnectionTimeout(),
            stats == null ? null : stats.getActiveConnections(),
            stats == null ? null : stats.getIdleConnections(),
            stats == null ? null : stats.getThreadsAwaitingConnection());
      }
    } catch (SQLException | RuntimeException ignored) {
      // Pool information is optional.
    }
    return new SystemInfoResponse.Pool(null, null, null, null, null, null, null);
  }

  private SystemInfoResponse.Mail mailInfo() {
    return new SystemInfoResponse.Mail(
        safeHost(environment.getProperty("spring.mail.host")),
        environment.getProperty("spring.mail.port", Integer.class),
        environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class),
        environment.getProperty(
            "spring.mail.properties.mail.smtp.starttls.required", Boolean.class),
        environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable", Boolean.class),
        "NOT_CHECKED");
  }

  private static Long nonNegative(long value) {
    return value >= 0 ? value : null;
  }

  private static String safeHost(String value) {
    return value != null && value.length() <= 255 && value.matches("[A-Za-z0-9_.:%\\[\\]-]+")
        ? value
        : null;
  }

  private static Properties resourceProperties(String path) {
    Properties properties = new Properties();
    try (var stream = SystemInfoService.class.getResourceAsStream(path)) {
      if (stream != null) {
        properties.load(stream);
      }
    } catch (IOException | IllegalArgumentException ignored) {
      // Build metadata is optional.
    }
    return properties;
  }
}

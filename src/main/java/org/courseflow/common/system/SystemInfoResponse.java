package org.courseflow.common.system;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Allowlisted, administrator-only diagnostic snapshot. No connection URLs or credentials. */
public record SystemInfoResponse(
    Snapshot snapshot, ServerEndpoint serverEndpoint, Long accessTokenLifetimeSeconds) {

  public record Snapshot(
      Instant collectedAt,
      int cacheSeconds,
      Application application,
      RuntimeInfo runtime,
      Database database,
      Pool pool,
      Mail mail) {}

  public record Application(
      String name,
      String version,
      String springBootVersion,
      String hibernateVersion,
      List<String> activeProfiles,
      List<String> defaultProfiles,
      String schemaManagement,
      String buildTime,
      String gitCommit) {
    public Application {
      activeProfiles =
          activeProfiles == null
              ? null
              : Collections.unmodifiableList(new ArrayList<>(activeProfiles));
      defaultProfiles =
          defaultProfiles == null
              ? null
              : Collections.unmodifiableList(new ArrayList<>(defaultProfiles));
    }

    @Override
    public List<String> activeProfiles() {
      return activeProfiles == null ? null : Collections.unmodifiableList(activeProfiles);
    }

    @Override
    public List<String> defaultProfiles() {
      return defaultProfiles == null ? null : Collections.unmodifiableList(defaultProfiles);
    }
  }

  public record RuntimeInfo(
      String hostName,
      String javaVersion,
      String javaVendor,
      String jvmName,
      String osName,
      String osVersion,
      String architecture,
      int availableProcessors,
      Long totalMemoryBytes,
      Long freeMemoryBytes,
      Instant jvmStartedAt,
      long uptimeSeconds,
      String timeZone) {}

  public record Database(
      String status,
      String product,
      String version,
      String driver,
      String driverVersion,
      String serverAddress,
      Integer serverPort,
      String databaseName,
      String databaseUser,
      String schema,
      Long sizeBytes,
      Long tableCount,
      Long queryTimeMillis) {}

  public record Pool(
      String name,
      Integer minimumIdle,
      Integer maximumSize,
      Long connectionTimeoutMillis,
      Integer active,
      Integer idle,
      Integer pending) {}

  public record Mail(
      String host,
      Integer port,
      Boolean startTlsEnabled,
      Boolean startTlsRequired,
      Boolean sslEnabled,
      String status) {}

  public record ServerEndpoint(String localAddress, int localPort) {}
}

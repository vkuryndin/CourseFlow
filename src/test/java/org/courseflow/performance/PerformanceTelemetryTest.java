package org.courseflow.performance;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformanceTelemetryTest {
  @TempDir Path directory;

  @Test
  void persistsObservationsBeforeCloseAndKeepsOutcomeCounts() throws Exception {
    Path output = directory.resolve("telemetry.csv");
    Files.writeString(output, PerformanceTelemetry.HEADER);
    HikariDataSource source = mock(HikariDataSource.class);
    // No live database needed: verify that an unavailable observer is recorded, not hidden.
    when(source.getJdbcUrl()).thenReturn("jdbc:courseflow-test-unavailable:dummy");
    when(source.getUsername()).thenReturn("test");
    when(source.getPassword()).thenReturn("test");
    var columns = Arrays.asList(PerformanceTelemetry.HEADER.strip().split(","));
    try (var observer =
        new PerformanceTelemetry(
            "test",
            source,
            mock(Statistics.class),
            output,
            s -> !s.successful() && s.status() != 503)) {
      assertTrue(Files.readAllLines(output).size() >= 2);
      observer.record(new LoadRunner.Sample(0, "read", 10, 200, 200, ""));
      observer.record(new LoadRunner.Sample(1, "read", 10, 200, 503, ""));
      observer.record(new LoadRunner.Sample(2, "read", 10, 200, 500, ""));
      observer.record(new LoadRunner.Sample(3, "read", 10, 200, 200, "INVALID_RESPONSE"));
      assertEquals(4, observer.recorded());
      await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(
              () -> {
                var lines = Files.readAllLines(output);
                var row = lines.get(lines.size() - 1).split(",", -1);
                assertEquals(columns.size(), row.length);
                assertEquals("1", row[columns.indexOf("client_validated_success")]);
                assertEquals("1", row[columns.indexOf("client_expected_non_success")]);
                assertEquals("2", row[columns.indexOf("client_unexpected_errors")]);
                assertEquals("SQLException", row[columns.indexOf("observer_error")]);
              });
      assertTrue(observer.errors() >= 2);
    }
    var lines = Files.readAllLines(output);
    assertTrue(lines.size() >= 4);
    var finalRow = lines.get(lines.size() - 1).split(",", -1);
    assertEquals("2", finalRow[columns.indexOf("client_unexpected_errors")]);
  }
}

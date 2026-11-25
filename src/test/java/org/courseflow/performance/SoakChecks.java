package org.courseflow.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Sustained-load checks on complete one-minute windows, excluding the warm-up minute. */
final class SoakChecks {
  private SoakChecks() {}

  static void verify(LoadRunner.Result result, int seconds, Set<String> operations,
      double minimumRps, double p95Ms, double p99Ms) {
    assertTrue(seconds >= 300, "A full soak requires at least five minutes");
    long minute = Duration.ofMinutes(1).toNanos();
    int windows = seconds / 60;
    for (int index = 1; index < windows; index++) {
      long start = index * minute;
      long end = start + minute;
      List<LoadRunner.Sample> window = result.samples().stream()
          .filter(s -> s.completionOffsetNanos() >= start && s.completionOffsetNanos() < end)
          .toList();
      String label = "Soak minute " + (index + 1);
      assertTrue(window.size() / 60.0 >= minimumRps, label + ": throughput too low");
      assertTrue(window.stream().allMatch(LoadRunner.Sample::successful), label + ": failed request");
      assertEquals(operations, window.stream().map(LoadRunner.Sample::operation).collect(Collectors.toSet()), label);
      for (String operation : operations) {
        long[] durations = window.stream().filter(s -> operation.equals(s.operation()))
            .mapToLong(LoadRunner.Sample::nanos).sorted().toArray();
        assertTrue(percentile(durations, .95) <= p95Ms, label + "/" + operation + ": p95");
        assertTrue(percentile(durations, .99) <= p99Ms, label + "/" + operation + ": p99");
      }
    }
  }

  private static double percentile(long[] values, double quantile) {
    assertTrue(values.length > 0, "No samples for operation");
    return values[(int) Math.ceil(values.length * quantile) - 1] / 1e6;
  }
}

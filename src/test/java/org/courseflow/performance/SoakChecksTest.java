package org.courseflow.performance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SoakChecksTest {
  @Test
  void acceptsAllCompleteWindows() {
    assertDoesNotThrow(() -> verify(samples()));
  }

  @Test
  void rejectsMissingOperationInOneWindowEvenWhenOverallWorkloadContainsIt() {
    var samples = samples();
    samples.removeIf(
        s ->
            s.operation().equals("write")
                && s.completionOffsetNanos() == Duration.ofMinutes(2).toNanos());
    assertThrows(AssertionError.class, () -> verify(samples));
  }

  @Test
  void rejectsLocalLatencyRegression() {
    var samples = samples();
    samples.add(
        new LoadRunner.Sample(
            0,
            "read",
            Duration.ofSeconds(3).toNanos(),
            200,
            200,
            "",
            Duration.ofMinutes(3).toNanos()));
    assertThrows(AssertionError.class, () -> verify(samples));
  }

  private static ArrayList<LoadRunner.Sample> samples() {
    var samples = new ArrayList<LoadRunner.Sample>();
    for (int minute = 1; minute < 5; minute++) {
      for (String operation : List.of("read", "write")) {
        samples.add(
            new LoadRunner.Sample(
                0,
                operation,
                Duration.ofMillis(10).toNanos(),
                200,
                200,
                "",
                Duration.ofMinutes(minute).toNanos()));
      }
    }
    return samples;
  }

  private static void verify(List<LoadRunner.Sample> samples) {
    SoakChecks.verify(
        new LoadRunner.Result(1, 300, samples, 1), 300, Set.of("read", "write"), 0.01, 500, 1000);
  }
}

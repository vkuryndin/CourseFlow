package org.courseflow.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LoadRunnerTest {
  @Test
  void wrongStatusIsNotSuccessful() throws Exception {
    var result =
        LoadRunner.once(
            1,
            (user, probe) ->
                probe.request("read", 200, () -> 500, Integer::intValue, response -> {}));
    assertFalse(result.samples().get(0).successful());
    assertEquals(500, result.samples().get(0).status());
  }

  @Test
  void failedResponseValidationIsRecorded() throws Exception {
    var result =
        LoadRunner.once(
            1,
            (user, probe) ->
                probe.request(
                    "read",
                    200,
                    () -> 200,
                    Integer::intValue,
                    response -> {
                      throw new AssertionError("Invalid body");
                    }));
    assertEquals("INVALID_RESPONSE", result.samples().get(0).exception());
    assertFalse(result.samples().get(0).successful());
  }

  @Test
  void timeoutIsNotSuccessful() throws Exception {
    var result =
        LoadRunner.once(
            1,
            (user, probe) ->
                probe.<Integer>request(
                    "read",
                    200,
                    () -> {
                      throw new HttpTimeoutException("timeout");
                    },
                    Integer::intValue,
                    response -> {}));
    assertEquals("HttpTimeoutException", result.samples().get(0).exception());
    assertFalse(result.samples().get(0).successful());
  }

  @Test
  void missingRequestsAndWorkerExceptionsFailRun() {
    assertThrows(IllegalStateException.class, () -> LoadRunner.once(2, (user, probe) -> {}));
    assertThrows(
        ExecutionException.class,
        () ->
            LoadRunner.once(
                1,
                (user, probe) -> {
                  throw new IllegalArgumentException("worker failure");
                }));
  }

  @Test
  void simultaneousRequestsAreMeasured() throws Exception {
    CountDownLatch entered = new CountDownLatch(2);
    var result =
        LoadRunner.once(
            2,
            (user, probe) ->
                probe.request(
                    "read",
                    200,
                    () -> {
                      entered.countDown();
                      assertTrue(entered.await(5, TimeUnit.SECONDS));
                      return 200;
                    },
                    Integer::intValue,
                    response -> assertEquals(200, response)));
    assertEquals(2, result.peakInFlight());
    assertEquals(2, result.samples().size());
    assertTrue(result.samples().stream().allMatch(LoadRunner.Sample::successful));
  }

  @Test
  void reportsEveryValidatedSampleWithoutLosingMeasurements() throws Exception {
    var observed = new ConcurrentLinkedQueue<LoadRunner.Sample>();
    var result =
        LoadRunner.sustained(
            4,
            Duration.ofMillis(150),
            Duration.ofMillis(10),
            (user, probe) ->
                probe.request(
                    "read",
                    200,
                    () -> user == 0 ? 503 : 200,
                    Integer::intValue,
                    response -> {
                      if (user == 1) throw new AssertionError("Invalid response");
                    }),
            observed::add);
    assertEquals(result.samples().size(), observed.size());
    assertEquals(new java.util.HashSet<>(result.samples()), new java.util.HashSet<>(observed));
    assertEquals(4, result.samples().stream().map(LoadRunner.Sample::user).distinct().count());
    assertTrue(observed.stream().anyMatch(s -> s.status() == 503));
    assertTrue(observed.stream().anyMatch(s -> "INVALID_RESPONSE".equals(s.exception())));
    assertTrue(observed.stream().anyMatch(LoadRunner.Sample::successful));
    assertThrows(UnsupportedOperationException.class, () -> result.samples().clear());
  }
}

package org.courseflow.performance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import java.util.function.Consumer;

/** Closed-loop HTTP load: one synchronous request at a time per virtual user. */
final class LoadRunner {

    private LoadRunner() {}

    @FunctionalInterface
    interface Work {
        void execute(int user, Probe probe) throws Exception;
    }

    record Sample(int user, String operation, long nanos, int expected, int status, String exception) {
        boolean successful() {
            return exception.isEmpty() && status == expected;
        }
    }

    record Result(int users, double seconds, List<Sample> samples) {}

    static final class Probe {
        private final Queue<Sample> samples;
        private final int user;

        private Probe(Queue<Sample> samples, int user) {
            this.samples = samples;
            this.user = user;
        }

        <T> T request(String operation, int expected, Callable<T> action,
                      ToIntFunction<T> statusCode, Consumer<T> validate) throws InterruptedException {
            long started = System.nanoTime();
            long elapsed = -1;
            int status = 0;
            String exception = "";
            try {
                T response = action.call();
                status = statusCode.applyAsInt(response);
                elapsed = System.nanoTime() - started;
                if (status == expected) {
                    validate.accept(response);
                }
                return response;
            } catch (InterruptedException interrupted) {
                exception = interrupted.getClass().getSimpleName();
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (AssertionError invalid) {
                exception = "INVALID_RESPONSE";
                return null;
            } catch (Exception failure) {
                exception = failure.getClass().getSimpleName();
                return null;
            } finally {
                samples.add(new Sample(user, operation, elapsed < 0 ? System.nanoTime() - started : elapsed,
                        expected, status, exception));
            }
        }
    }

    static Result once(int users, Work work) throws Exception {
        return run(users, Duration.ZERO, Duration.ZERO, work);
    }

    static Result sustained(int users, Duration duration, Duration pause, Work work)
            throws Exception {
        if (duration.isZero() || duration.isNegative() || pause.isNegative()) {
            throw new IllegalArgumentException("Invalid load duration or pause");
        }
        return run(users, duration, pause, work);
    }

    private static Result run(int users, Duration duration, Duration pause, Work work)
            throws Exception {
        if (users < 1) {
            throw new IllegalArgumentException("At least one virtual user is required");
        }
        ExecutorService executor = Executors.newFixedThreadPool(users);
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch start = new CountDownLatch(1);
        Queue<Sample> samples = new ConcurrentLinkedQueue<>();
        List<Future<Void>> futures = new ArrayList<>();
        long[] started = new long[1];
        long durationNanos = duration.toNanos();
        long pauseNanos = pause.toNanos();
        try {
            for (int index = 0; index < users; index++) {
                int user = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Load start timed out");
                    }
                    // CountDownLatch publishes the shared start time to every worker.
                    Probe probe = new Probe(samples, user);
                    do {
                        work.execute(user, probe);
                        long remaining = durationNanos - (System.nanoTime() - started[0]);
                        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        TimeUnit.NANOSECONDS.sleep(Math.min(pauseNanos, remaining));
                    } while (System.nanoTime() - started[0] < durationNanos);
                    return null;
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Virtual users did not become ready");
            }
            started[0] = System.nanoTime();
            start.countDown();
            // A quiz workflow can contain two HTTP requests with 45-second timeouts.
            long budget = durationNanos + TimeUnit.SECONDS.toNanos(100);
            for (Future<Void> future : futures) {
                long remaining = budget - (System.nanoTime() - started[0]);
                future.get(Math.max(1, remaining), TimeUnit.NANOSECONDS);
            }
            double seconds = (System.nanoTime() - started[0]) / 1_000_000_000.0;
            return new Result(users, seconds, List.copyOf(samples));
        } finally {
            executor.shutdownNow();
            start.countDown();
            if (!executor.awaitTermination(50, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Load workers did not terminate");
            }
        }
    }
}

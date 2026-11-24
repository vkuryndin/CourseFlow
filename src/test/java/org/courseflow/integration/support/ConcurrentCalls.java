package org.courseflow.integration.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A synchronized start; workers may overlap without sharing a transaction. */
public final class ConcurrentCalls {
    private ConcurrentCalls() {}

    public static <T> List<T> run(List<? extends Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent start timed out");
                    }
                    return task.call();
                }));
            }
            if (!ready.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers failed to reach the start barrier");
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            for (Future<T> future : futures) {
                long remaining = Math.max(1, deadline - System.nanoTime());
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            start.countDown();
            for (Future<T> future : futures) future.cancel(true);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public static <T> List<T> pair(Callable<T> first, Callable<T> second) throws Exception {
        return run(List.of(first, second));
    }
}

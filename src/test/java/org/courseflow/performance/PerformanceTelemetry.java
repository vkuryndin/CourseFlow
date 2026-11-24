package org.courseflow.performance;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.ObjectName;
import org.hibernate.stat.Statistics;

/** Samples the test JVM, server and database without borrowing a Hikari connection. */
final class PerformanceTelemetry implements AutoCloseable {
    static final String HEADER = "scenario,timestamp,elapsed_s,client_in_flight,client_peak_in_flight,"
            + "client_completed,heap_used_bytes,non_heap_used_bytes,jvm_process_cpu_pct,"
            + "gc_count,gc_time_ms,hikari_active,hikari_idle,hikari_total,hikari_waiting,"
            + "tomcat_busy,tomcat_threads,hibernate_sql_prepared,hibernate_transactions,"
            + "hibernate_successful_transactions,pg_connections,pg_active,pg_open_transactions,"
            + "pg_lock_waiters,pg_deadlocks,pg_commits,pg_rollbacks,observer_error\n";

    private final String scenario;
    private final HikariDataSource dataSource;
    private final Statistics statistics;
    private final Path output;
    private final long start = System.nanoTime();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicLong completed = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "courseflow-performance-observer");
        t.setDaemon(true);
        return t;
    });
    private final List<String> rows = new ArrayList<>();
    private Connection observer;
    private ScheduledFuture<?> samplingTask;
    private int pgSamples;
    private int errors;
    private int serverSamples;
    private long firstDeadlocks = -1;
    private long lastDeadlocks = -1;

    PerformanceTelemetry(String scenario, HikariDataSource dataSource, Statistics statistics,
                         Path output) throws Exception {
        this.scenario = scenario;
        this.dataSource = dataSource;
        this.statistics = statistics;
        this.output = output;
        try {
            sample();
            samplingTask = scheduler.scheduleWithFixedDelay(this::sample, 2, 2, TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            scheduler.shutdownNow();
            closeObserver();
            throw failure;
        }
    }

    <T> T request(Callable<T> call) throws Exception {
        int active = inFlight.incrementAndGet();
        peak.accumulateAndGet(active, Math::max);
        try {
            return call.call();
        } finally {
            completed.incrementAndGet();
            inFlight.decrementAndGet();
        }
    }

    int peak() { return peak.get(); }
    long completed() { return completed.get(); }
    int pgSamples() { return pgSamples; }
    int errors() { return errors; }
    int serverSamples() { return serverSamples; }
    long deadlockDelta() { return firstDeadlocks < 0 ? -1 : lastDeadlocks - firstDeadlocks; }

    private void sample() {
        List<String> values = new ArrayList<>();
        values.add(scenario);
        values.add(Instant.now().toString());
        values.add(String.format(Locale.ROOT, "%.3f", (System.nanoTime() - start) / 1e9));
        add(values, inFlight.get(), peak.get(), completed.get());
        var memory = ManagementFactory.getMemoryMXBean();
        add(values, memory.getHeapMemoryUsage().getUsed(), memory.getNonHeapMemoryUsage().getUsed());
        var os = ManagementFactory.getOperatingSystemMXBean();
        double cpu = os instanceof com.sun.management.OperatingSystemMXBean extended
                ? extended.getProcessCpuLoad() : -1;
        values.add(cpu < 0 || !Double.isFinite(cpu) ? "NA" : String.format(Locale.ROOT, "%.3f", cpu * 100));
        long gcCount = 0;
        long gcTime = 0;
        boolean gcCountAvailable = false;
        boolean gcTimeAvailable = false;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() >= 0) { gcCount += gc.getCollectionCount(); gcCountAvailable = true; }
            if (gc.getCollectionTime() >= 0) { gcTime += gc.getCollectionTime(); gcTimeAvailable = true; }
        }
        values.add(gcCountAvailable ? Long.toString(gcCount) : "NA");
        values.add(gcTimeAvailable ? Long.toString(gcTime) : "NA");
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        if (pool == null) {
            unavailable(values, 4);
        } else {
            add(values, pool.getActiveConnections(), pool.getIdleConnections(),
                    pool.getTotalConnections(), pool.getThreadsAwaitingConnection());
        }
        long[] tomcat = tomcatThreads();
        if (tomcat == null) {
            unavailable(values, 2);
        } else {
            serverSamples++;
            add(values, tomcat[0], tomcat[1]);
        }
        if (statistics.isStatisticsEnabled()) {
            add(values, statistics.getPrepareStatementCount(), statistics.getTransactionCount(),
                    statistics.getSuccessfulTransactionCount());
        } else {
            unavailable(values, 3);
        }
        String error = "";
        try {
            long[] pg = postgres();
            for (long value : pg) add(values, value);
            if (firstDeadlocks < 0) firstDeadlocks = pg[4];
            lastDeadlocks = pg[4];
            pgSamples++;
        } catch (Exception failure) {
            unavailable(values, 7);
            error = failure.getClass().getSimpleName();
            errors++;
            closeObserver();
        }
        values.add(error);
        rows.add(String.join(",", values));
    }

    private long[] postgres() throws Exception {
        if (observer == null || observer.isClosed()) {
            var props = new java.util.Properties();
            props.setProperty("user", dataSource.getUsername());
            props.setProperty("password", dataSource.getPassword());
            props.setProperty("connectTimeout", "3");
            props.setProperty("socketTimeout", "4");
            props.setProperty("ApplicationName", "courseflow-performance-observer");
            observer = DriverManager.getConnection(dataSource.getJdbcUrl(), props);
            try (Statement statement = observer.createStatement()) {
                statement.setQueryTimeout(3);
                try (ResultSet rs = statement.executeQuery("SELECT current_database()")) {
                    if (!rs.next() || !"courseflow_test".equals(rs.getString(1))) {
                        throw new IllegalStateException("Observer must use the temporary database");
                    }
                }
            }
        }
        // Autocommit: each sample obtains a new PostgreSQL statistics snapshot.
        try (Statement statement = observer.createStatement()) {
            statement.setQueryTimeout(3);
            try (ResultSet rs = statement.executeQuery("""
                    SELECT a.connections, a.active, a.open_transactions, a.lock_waiters,
                           d.deadlocks, d.xact_commit, d.xact_rollback
                    FROM pg_stat_database d CROSS JOIN (
                        SELECT count(*) AS connections,
                               count(*) FILTER (WHERE state = 'active') AS active,
                               count(*) FILTER (WHERE xact_start IS NOT NULL) AS open_transactions,
                               count(*) FILTER (WHERE wait_event_type = 'Lock') AS lock_waiters
                        FROM pg_stat_activity
                        WHERE datname = current_database() AND pid <> pg_backend_pid()
                    ) a WHERE d.datname = current_database()
                    """)) {
                if (!rs.next()) throw new IllegalStateException("Missing PostgreSQL statistics");
                long[] values = new long[7];
                for (int i = 0; i < values.length; i++) values[i] = rs.getLong(i + 1);
                return values;
            }
        }
    }

    private static long[] tomcatThreads() {
        try {
            var server = ManagementFactory.getPlatformMBeanServer();
            long busy = 0;
            long total = 0;
            int found = 0;
            for (ObjectName name : server.queryNames(new ObjectName("*:type=ThreadPool,*"), null)) {
                try {
                    long b = ((Number) server.getAttribute(name, "currentThreadsBusy")).longValue();
                    long t = ((Number) server.getAttribute(name, "currentThreadCount")).longValue();
                    if (b >= 0 && t >= 0) { busy += b; total += t; found++; }
                } catch (Exception unavailable) {
                    // Optional Tomcat MBeans may not exist on every runner.
                }
            }
            return found == 0 ? null : new long[]{busy, total};
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static void add(List<String> target, long... values) {
        for (long value : values) target.add(Long.toString(value));
    }

    private static void unavailable(List<String> target, int count) {
        for (int i = 0; i < count; i++) target.add("NA");
    }

    private void closeObserver() {
        if (observer != null) {
            try { observer.close(); } catch (Exception ignored) { /* best effort */ }
            observer = null;
        }
    }

    @Override
    public void close() throws Exception {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(15, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                throw new IllegalStateException("Performance observer did not stop");
            }
            if (samplingTask.isDone() && !samplingTask.isCancelled()) {
                samplingTask.get(); // Surface an unexpected sampler exception.
            }
            sample();
            Files.write(output, rows, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } finally {
            closeObserver();
        }
    }
}

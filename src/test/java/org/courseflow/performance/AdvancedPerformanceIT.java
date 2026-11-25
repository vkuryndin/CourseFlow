package org.courseflow.performance;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import javax.sql.DataSource;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.service.AuthService;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Opt-in exploratory stress tests and strict recovery/steady-state checks. */
@Tag("performance")
@EnabledIfSystemProperty(named = "courseflow.performance", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "server.tomcat.mbeanregistry.enabled=true",
      "spring.jpa.properties.hibernate.session.events.log=false",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
    })
class AdvancedPerformanceIT extends HttpIntegrationTest {
  private static final String PREFIX = "courseflow.performance.advanced.";
  private static final Duration THINK = Duration.ofMillis(200);
  private static final int AUTH_USERS = 100;
  private static final int COURSE_COUNT = 100;
  private static final int STUDY_COURSES = 10;
  private static final String LESSON_CONTENT =
      "Advanced performance lesson content ".repeat(20).trim();

  @Autowired private AuthService auth;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;
  @Autowired private DataSource pooledDataSource;
  private HikariDataSource dataSource;
  @Autowired private EntityManagerFactory emf;

  private final List<Actor> students = new ArrayList<>();
  private final List<StudyCourse> studyCourses = new ArrayList<>();
  private final List<Long> catalogIds = new ArrayList<>();
  private final Map<Long, Long> enrollmentIds = new java.util.HashMap<>();
  private int maxUsers;
  private Duration stageDuration;
  private int soakSeconds;
  private Statistics statistics;
  private Path report;
  private Path telemetry;

  private record Actor(long id, String email, String token, StudyCourse study) {}

  private record StudyCourse(long course, long module, long lesson) {}

  private enum Policy {
    NORMAL,
    STRESS,
    AUTH
  }

  private record Phase(
      LoadRunner.Result result, double p95, double p99, double rps, long overload) {}

  @BeforeAll
  void prepare() throws Exception {
    assumeTrue(
        Boolean.getBoolean("courseflow.performance"), "Opt in with -Dcourseflow.performance=true");
    maxUsers = Integer.getInteger(PREFIX + "maxUsers", 500);
    int stageSeconds = Integer.getInteger(PREFIX + "stageSeconds", 10);
    soakSeconds = Integer.getInteger(PREFIX + "soakSeconds", 900);
    int minimumUsers = Boolean.getBoolean("courseflow.performance.smoke") ? 100 : 500;
    assertTrue(maxUsers >= minimumUsers && maxUsers <= 500, "Invalid maxUsers for selected profile");
    assertTrue(stageSeconds >= 5 && stageSeconds <= 60, "stageSeconds must be 5..60");
    int minimumSoak = Boolean.getBoolean("courseflow.performance.smoke") ? 10 : 300;
    assertTrue(soakSeconds >= minimumSoak && soakSeconds <= 1800, "Invalid soakSeconds for selected profile");
    stageDuration = Duration.ofSeconds(stageSeconds);
    assertEquals("courseflow_test", jdbc.queryForObject("SELECT current_database()", String.class));
    dataSource = pooledDataSource.unwrap(HikariDataSource.class);
    String run = UUID.randomUUID().toString();
    Path directory = Path.of("build", "reports", "performance").toAbsolutePath();
    Files.createDirectories(directory);
    report = directory.resolve("advanced-performance-" + run + ".csv");
    telemetry = directory.resolve("advanced-telemetry-" + run + ".csv");
    Files.writeString(
        report,
        "scenario,operation,users,seconds,requests,valid_responses,"
            + "rate_limited,overload_candidates,unexpected_errors,rps,valid_rps,avg_ms,"
            + "min_ms,p50_ms,p95_ms,p99_ms,max_ms,valid_p95_ms,valid_p99_ms,"
            + "peak_client_in_flight,observer_samples,observer_errors,tomcat_samples,pg_deadlock_delta,outcomes\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW);
    Files.writeString(
        telemetry,
        PerformanceTelemetry.HEADER,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW);
    System.out.println("Advanced performance report: " + report);
    System.out.println("Advanced telemetry report: " + telemetry);
    System.out.println("Preparing " + maxUsers + " distinct student accounts and 100 courses...");

    // All fixture authentication uses real BCrypt and JWT, but leaves the HTTP IP budget untouched.
    String admin = auth.login(new LoginRequest(adminEmail, adminPassword)).accessToken();
    Actor teacher = createActor(admin, List.of("TEACHER", "STUDENT"), null);
    long category =
        id(
            expect(
                api(
                    "POST",
                    "/api/categories",
                    admin,
                    Map.of("name", "Perf-" + run, "description", "Advanced performance fixtures")),
                201));
    for (int i = 0; i < COURSE_COUNT; i++) {
      long course =
          id(
              expect(
                  api(
                      "POST",
                      "/api/courses",
                      teacher.token(),
                      Map.of(
                          "title",
                          "Perf " + run + " " + i,
                          "description",
                          "Advanced load course " + i,
                          "categoryId",
                          category,
                          "teacherId",
                          teacher.id(),
                          "duration",
                          56,
                          "startDate",
                          LocalDate.now(clock).plusDays(1).toString())),
                  201));
      catalogIds.add(course);
      if (i < STUDY_COURSES) {
        long module =
            id(
                expect(
                    api(
                        "POST",
                        "/api/courses/" + course + "/modules",
                        teacher.token(),
                        Map.of("title", "Module", "position", 1)),
                    201));
        long lesson =
            id(
                expect(
                    api(
                        "POST",
                        "/api/courses/" + course + "/modules/" + module + "/lessons",
                        teacher.token(),
                        Map.of("title", "Lesson", "content", LESSON_CONTENT, "position", 1)),
                    201));
        studyCourses.add(new StudyCourse(course, module, lesson));
      }
    }
    catalogIds.sort(Long::compareTo);
    for (int i = 0; i < maxUsers; i++) {
      StudyCourse study = studyCourses.get(i % STUDY_COURSES);
      Actor actor = createActor(admin, List.of("STUDENT"), study);
      Object enrollment =
          expect(
              api("POST", "/api/courses/" + study.course() + "/enrollments", actor.token(), null),
              201);
      enrollmentIds.put(actor.id(), id(enrollment));
      validateEnrollment(actor, enrollment);
      students.add(actor);
    }
    assertEquals((long) maxUsers, students.stream().map(Actor::id).distinct().count());
    assertEquals((long) maxUsers, students.stream().map(Actor::token).distinct().count());
    checkEnrollments();
    // Warm each measured endpoint before starting measurements.
    for (int page = 0; page < 5; page++) {
      validateCatalog(
          page, expect(api("GET", catalogPath(page), students.get(page).token(), null), 200));
    }
    for (int i = 0; i < STUDY_COURSES; i++) {
      Actor actor = students.get(i);
      validateLesson(actor, expect(api("GET", lessonPath(actor), actor.token(), null), 200));
      validateProfile(actor, expect(api("GET", "/api/users/me", actor.token(), null), 200));
    }
    statistics = emf.unwrap(SessionFactory.class).getStatistics();
    System.out.println("Fixtures ready. Hikari pool size: " + dataSource.getMaximumPoolSize());
  }

  @Test
  void steppedRampUp() throws Exception {
    for (int users :
        Arrays.stream(new int[] {10, 50, 100, 200, maxUsers}).distinct().sorted().toArray()) {
      if (users <= maxUsers) {
        phase(
            "ramp_" + users,
            users,
            stageDuration,
            THINK,
            users <= 100 ? Policy.NORMAL : Policy.STRESS);
      }
    }
    recover("ramp_recovery");
  }

  @Test
  void saturationWithoutThinkTime() throws Exception {
    phase("saturation_100", 100, stageDuration, Duration.ZERO, Policy.STRESS);
    int high = Math.min(200, maxUsers);
    if (high > 100) phase("saturation_" + high, high, stageDuration, Duration.ZERO, Policy.STRESS);
    recover("saturation_recovery");
  }

  @Test
  void stagedSpikeAndRecovery() throws Exception {
    phase("spike_before", 10, stageDuration, THINK, Policy.NORMAL);
    phase("spike_peak_" + maxUsers, maxUsers, stageDuration, Duration.ZERO, Policy.STRESS);
    // No deliberate cooldown. The transition includes worker shutdown/startup and reporting.
    recover("spike_after");
  }

  @Test
  void extendedSoak() throws Exception {
    Phase soak = phase(
        "soak_100_" + soakSeconds + "s",
        100,
        Duration.ofSeconds(soakSeconds),
        Duration.ofSeconds(1),
        Policy.NORMAL);
    if (!Boolean.getBoolean("courseflow.performance.smoke")) {
      SoakChecks.verify(soak.result(), soakSeconds, Set.of("catalog", "lesson", "profile", "repeat_enrollment"),
          threshold("soakMinRps", 10), threshold("p95Ms", 500), threshold("p99Ms", 1000));
    }
    recover("soak_recovery");
  }

  @Test
  void authenticationBurstExercisesRealRateLimiter() throws Exception {
    // No other test in this class performs an HTTP login. All 100 accounts are valid and distinct.
    Map<Integer, String> tokens = new ConcurrentHashMap<>();
    Phase phase =
        run(
            "auth_100",
            AUTH_USERS,
            Policy.AUTH,
            observer ->
                LoadRunner.once(
                    AUTH_USERS,
                    (user, probe) -> {
                      Actor actor = students.get(user);
                      probe.request(
                          "login",
                          200,
                          () -> {
                            Response response =
                                observer.request(
                                    () ->
                                        api(
                                            "POST",
                                            "/api/auth/login",
                                            null,
                                            Map.of("email", actor.email(), "password", PASSWORD)));
                            if (response.status() == 429) {
                              assertEquals("RATE_LIMIT_EXCEEDED", field(response.body(), "code"));
                              assertEquals(429L, number(response.body(), "status"));
                              String header = response.header("Retry-After");
                              assertTrue(header != null && header.matches("[0-9]+"));
                              long delay = Long.parseLong(header);
                              assertTrue(delay >= 1 && delay <= 60);
                            }
                            return response;
                          },
                          Response::status,
                          response -> {
                            Object body = response.body();
                            assertEquals(actor.id(), number(body, "userId"));
                            assertEquals("Bearer", field(body, "tokenType"));
                            Object token = field(body, "accessToken");
                            assertTrue(token instanceof String && !((String) token).isBlank());
                            assertTrue(!actor.token().equals(token));
                            tokens.put(user, (String) token);
                          });
                    }));
    assertTrue(
        phase.result().seconds() < 60, "Authentication burst must fit one fixed rate-limit window");
    assertEquals(
        60L, phase.result().samples().stream().filter(LoadRunner.Sample::successful).count());
    assertEquals(
        40L, phase.result().samples().stream().filter(AdvancedPerformanceIT::rateLimited).count());
    assertEquals(60, tokens.size());
    assertEquals(60, new HashSet<>(tokens.values()).size());
    for (int i = 0; i < AUTH_USERS; i++) {
      Actor actor = students.get(i);
      assertEquals(
          tokens.containsKey(i) ? 2L : 1L,
          count(
              """
                    SELECT count(*) FROM auth_sessions s JOIN user_accounts a ON a.id = s.user_account_id
                    WHERE a.user_id = ? AND NOT s.revoked
                    """,
              actor.id()));
      if (tokens.containsKey(i)) {
        validateProfile(actor, expect(api("GET", "/api/users/me", tokens.get(i), null), 200));
      }
    }
    recover("auth_recovery");
  }

  private Response checkedLoadResponse(Response response) {
    if (response.status() == 503) {
      assertEquals("SERVICE_UNAVAILABLE", field(response.body(), "code"));
      assertEquals(503L, number(response.body(), "status"));
      Object requestId = field(response.body(), "requestId");
      assertTrue(requestId instanceof String id && !id.isBlank());
      assertEquals(requestId, response.header("X-Request-Id"));
    }
    return response;
  }

  private Phase phase(String name, int users, Duration duration, Duration pause, Policy policy)
      throws Exception {
    AtomicIntegerArray iterations = new AtomicIntegerArray(users);
    return run(
        name,
        users,
        policy,
        observer ->
            LoadRunner.sustained(
                users,
                duration,
                pause,
                (user, probe) -> {
                  Actor actor = students.get(user);
                  int iteration = iterations.getAndIncrement(user);
                  switch ((user + iteration) % 4) {
                    case 0 -> {
                      int page = (user + iteration / 4) % 5;
                      probe.request(
                          "catalog",
                          200,
                          () ->
                              observer.request(
                                  () -> checkedLoadResponse(api("GET", catalogPath(page), actor.token(), null))),
                          Response::status,
                          r -> validateCatalog(page, r.body()));
                    }
                    case 1 ->
                        probe.request(
                            "lesson",
                            200,
                            () ->
                                observer.request(
                                    () -> checkedLoadResponse(api("GET", lessonPath(actor), actor.token(), null))),
                            Response::status,
                            r -> validateLesson(actor, r.body()));
                    case 2 ->
                        probe.request(
                            "profile",
                            200,
                            () ->
                                observer.request(
                                    () -> checkedLoadResponse(api("GET", "/api/users/me", actor.token(), null))),
                            Response::status,
                            r -> validateProfile(actor, r.body()));
                    default ->
                        probe.request(
                            "repeat_enrollment",
                            200,
                            () ->
                                observer.request(
                                    () ->
                                        checkedLoadResponse(api(
                                            "POST",
                                            "/api/courses/"
                                                + actor.study().course()
                                                + "/enrollments",
                                            actor.token(),
                                            null))),
                            Response::status,
                            r -> validateEnrollment(actor, r.body()));
                  }
                }));
  }

  private void recover(String name) throws Exception {
    Phase result = phase(name, 10, stageDuration, THINK, Policy.NORMAL);
    assertTrue(
        result.rps() >= threshold("recoveryMinRps", 10), "Recovery throughput too low: " + name);
    checkEnrollments();
    validateProfile(
        students.get(0), expect(api("GET", "/api/users/me", students.get(0).token(), null), 200));
    awaitQuiescence(name);
  }

  private void awaitQuiescence(String name) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    do {
      var pool = dataSource.getHikariPoolMXBean();
      long transactions = count("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND xact_start IS NOT NULL");
      long waiters = count("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'");
      if (pool.getActiveConnections() == 0 && pool.getThreadsAwaitingConnection() == 0
          && transactions == 0 && waiters == 0) {
        return;
      }
      Thread.sleep(100);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Connections, transactions or lock waiters did not recover: " + name);
  }

  @FunctionalInterface
  private interface Run {
    LoadRunner.Result execute(PerformanceTelemetry observer) throws Exception;
  }

  private Phase run(String name, int users, Policy policy, Run run) throws Exception {
    System.out.println("Starting " + name + ": " + users + " users");
    boolean wasEnabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    PerformanceTelemetry observer;
    LoadRunner.Result result;
    try {
      observer = new PerformanceTelemetry(name, dataSource, statistics, telemetry);
      try (observer) {
        result = run.execute(observer);
      }
    } finally {
      statistics.setStatisticsEnabled(wasEnabled);
    }
    List<LoadRunner.Sample> samples = result.samples();
    Map<String, List<LoadRunner.Sample>> operations = new TreeMap<>();
    for (var sample : samples)
      operations.computeIfAbsent(sample.operation(), k -> new ArrayList<>()).add(sample);
    Phase summary = append(name, "ALL", result, samples, policy, observer);
    for (var entry : operations.entrySet())
      append(name, entry.getKey(), result, entry.getValue(), policy, observer);
    assertEquals(users, result.users());
    assertEquals(
        (long) users,
        samples.stream().map(LoadRunner.Sample::user).distinct().count(),
        "Every user must execute a request");
    assertEquals((long) samples.size(), observer.completed(), "Missing HTTP measurements");
    assertTrue(observer.peak() >= Math.max(2, users / 10),
        "Insufficient measured HTTP concurrency: " + name + ": " + observer.peak());
    assertTrue(observer.pgSamples() >= 2, "Missing database telemetry: " + name);
    assertEquals(0, observer.errors(), "Database telemetry failed: " + name);
    assertEquals(0L, observer.deadlockDelta(), "New PostgreSQL deadlocks: " + name);
    if (observer.serverSamples() == 0) System.out.println("Tomcat MBeans unavailable; CSV uses NA");
    assertEquals(
        0L,
        samples.stream().filter(s -> unexpected(s, policy)).count(),
        "Unexpected results: " + name + "; see " + report);
    assertTrue(
        samples.stream().anyMatch(LoadRunner.Sample::successful),
        "No successful responses: " + name);
    Set<String> required = policy == Policy.AUTH ? Set.of("login")
        : Set.of("catalog", "lesson", "profile", "repeat_enrollment");
    assertEquals(required, operations.keySet(), "Workload operation coverage: " + name);
    if (policy == Policy.STRESS) {
      double minimumSuccess = threshold("stressMinSuccessRatio", 0.90);
      assertTrue(minimumSuccess <= 1, "stressMinSuccessRatio must be at most 1");
      for (var entry : operations.entrySet()) {
        double operationRatio = entry.getValue().stream().filter(LoadRunner.Sample::successful).count()
            / (double) entry.getValue().size();
        assertTrue(operationRatio >= minimumSuccess,
            "Stress success ratio: " + operationRatio + " in " + name + "/" + entry.getKey());
      }
      double successRatio = samples.stream().filter(LoadRunner.Sample::successful).count()
          / (double) samples.size();
      assertTrue(successRatio >= minimumSuccess, "Stress success ratio: " + successRatio + " in " + name);
    }
    if (policy == Policy.NORMAL) {
      double p95Limit = threshold("p95Ms", 500);
      double p99Limit = threshold("p99Ms", 1000);
      assertTrue(p99Limit >= p95Limit, "Invalid latency thresholds");
      // Per-operation thresholds prevent a fast endpoint from hiding a slow one.
      for (var entry : operations.entrySet()) {
        assertTrue(
            percentile(entry.getValue(), .95) <= p95Limit,
            name + "/" + entry.getKey() + " p95 too high");
        assertTrue(
            percentile(entry.getValue(), .99) <= p99Limit,
            name + "/" + entry.getKey() + " p99 too high");
      }
      assertEquals(4, operations.size(), "Not all workload operations executed");
    }
    return summary;
  }

  private Phase append(
      String scenario,
      String operation,
      LoadRunner.Result result,
      List<LoadRunner.Sample> samples,
      Policy policy,
      PerformanceTelemetry observer)
      throws Exception {
    List<LoadRunner.Sample> successful =
        samples.stream().filter(LoadRunner.Sample::successful).toList();
    long rateLimited =
        policy == Policy.AUTH
            ? samples.stream().filter(AdvancedPerformanceIT::rateLimited).count()
            : 0;
    long overloaded =
        policy == Policy.STRESS
            ? samples.stream().filter(AdvancedPerformanceIT::overload).count()
            : 0;
    long errors = samples.stream().filter(s -> unexpected(s, policy)).count();
    double rps = samples.size() / result.seconds();
    double avg = samples.stream().mapToDouble(s -> s.nanos() / 1e6).average().orElse(0);
    double p95 = percentile(samples, .95);
    double p99 = percentile(samples, .99);
    Map<String, Long> outcomes = new TreeMap<>();
    for (var sample : samples) {
      String key =
          "HTTP_"
              + sample.status()
              + (sample.exception().isEmpty() ? "" : "_" + sample.exception());
      outcomes.merge(key, 1L, Long::sum);
    }
    String row =
        String.format(
            Locale.ROOT,
            "%s,%s,%d,%.3f,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s,%d,%d,%d,%d,%d,%s%n",
            scenario,
            operation,
            result.users(),
            result.seconds(),
            samples.size(),
            successful.size(),
            rateLimited,
            overloaded,
            errors,
            rps,
            successful.size() / result.seconds(),
            avg,
            percentile(samples, 0),
            percentile(samples, .5),
            p95,
            p99,
            percentile(samples, 1),
            optionalPercentile(successful, .95),
            optionalPercentile(successful, .99),
            observer.peak(),
            observer.pgSamples(),
            observer.errors(),
            observer.serverSamples(),
            observer.deadlockDelta(),
            outcomes.toString().replace(',', ';'));
    Files.writeString(report, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    if ("ALL".equals(operation))
      System.out.printf(
          Locale.ROOT,
          "%s: %d requests, %.2f RPS, p95 %.2f ms, p99 %.2f ms, peak in-flight %d, overload %d, rate-limited %d, unexpected %d%n",
          scenario,
          samples.size(),
          rps,
          p95,
          p99,
          observer.peak(),
          overloaded,
          rateLimited,
          errors);
    return new Phase(result, p95, p99, rps, overloaded);
  }

  private static boolean rateLimited(LoadRunner.Sample sample) {
    return sample.status() == 429 && sample.exception().isEmpty();
  }

  private static boolean overload(LoadRunner.Sample sample) {
    return LoadPolicy.controlledOverload(sample);
  }

  private static boolean unexpected(LoadRunner.Sample sample, Policy policy) {
    return !sample.successful()
        && !(policy == Policy.AUTH && rateLimited(sample))
        && !(policy == Policy.STRESS && overload(sample));
  }

  private static double percentile(List<LoadRunner.Sample> samples, double quantile) {
    if (samples.isEmpty()) return 0;
    long[] sorted = samples.stream().mapToLong(LoadRunner.Sample::nanos).sorted().toArray();
    return sorted[Math.max(0, (int) Math.ceil(quantile * sorted.length) - 1)] / 1e6;
  }

  private static String optionalPercentile(List<LoadRunner.Sample> samples, double quantile) {
    return samples.isEmpty()
        ? "NA"
        : String.format(Locale.ROOT, "%.3f", percentile(samples, quantile));
  }

  private static double threshold(String name, double fallback) {
    double value = Double.parseDouble(System.getProperty(PREFIX + name, Double.toString(fallback)));
    assertTrue(Double.isFinite(value) && value > 0, "Invalid threshold: " + name);
    return value;
  }

  private Actor createActor(String admin, List<String> roles, StudyCourse study) throws Exception {
    String email = "perf." + UUID.randomUUID() + "@courseflow.local";
    Object created =
        expect(
            api(
                "POST",
                "/api/users",
                admin,
                Map.of(
                    "displayName",
                    "Perf-" + UUID.randomUUID(),
                    "email",
                    email,
                    "password",
                    PASSWORD,
                    "passwordChangeRequired",
                    false,
                    "roles",
                    roles)),
            201);
    var login = auth.login(new LoginRequest(email, PASSWORD));
    assertEquals(id(created), login.userId().longValue());
    return new Actor(id(created), email, login.accessToken(), study);
  }

  private String catalogPath(int page) {
    return "/api/courses?page=" + page + "&size=20&sortBy=id&direction=asc";
  }

  private String lessonPath(Actor actor) {
    return "/api/courses/"
        + actor.study().course()
        + "/modules/"
        + actor.study().module()
        + "/lessons/"
        + actor.study().lesson();
  }

  private void validateCatalog(int page, Object body) {
    assertEquals((long) page, number(body, "page"));
    assertEquals((long) COURSE_COUNT, number(body, "totalElements"));
    Object content = field(body, "content");
    assertTrue(content instanceof List<?>);
    assertEquals(
        catalogIds.subList(page * 20, (page + 1) * 20),
        ((List<?>) content).stream().map(AdvancedPerformanceIT::id).toList());
  }

  private static void validateLesson(Actor actor, Object body) {
    assertEquals(actor.study().lesson(), id(body));
    assertEquals(actor.study().module(), number(body, "moduleId"));
    assertEquals(LESSON_CONTENT, field(body, "content"));
  }

  private static void validateProfile(Actor actor, Object body) {
    assertEquals(actor.id(), id(body));
    assertEquals(actor.email(), field(body, "email"));
    assertEquals("ACTIVE", field(body, "status"));
    assertTrue(field(body, "roles") instanceof List<?> roles && roles.contains("STUDENT"));
  }

  private void validateEnrollment(Actor actor, Object body) {
    assertEquals(actor.id(), number(body, "studentId"));
    assertEquals(actor.study().course(), number(body, "courseId"));
    assertEquals("ACTIVE", field(body, "status"));
    assertEquals(enrollmentIds.get(actor.id()).longValue(), id(body));
  }

  private void checkEnrollments() {
    // Exact ownership and unique enrollment for every test student, not just the total.
    assertEquals((long) maxUsers, count("SELECT count(*) FROM enrollments"));
    for (Actor actor : students)
      assertEquals(
          1L,
          count(
              """
                SELECT count(*) FROM enrollments WHERE student_id = ? AND course_id = ? AND id = ?
                AND status = 'ACTIVE' AND ended_at IS NULL
                """,
              actor.id(),
              actor.study().course(),
              enrollmentIds.get(actor.id())));
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private static long id(Object body) {
    return number(body, "id");
  }

  private static long number(Object body, String key) {
    return org.courseflow.integration.support.JsonChecks.requiredInteger(body, key);
  }
}

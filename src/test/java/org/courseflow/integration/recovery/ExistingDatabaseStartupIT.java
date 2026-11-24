package org.courseflow.integration.recovery;

import static org.courseflow.integration.support.JsonChecks.field;
import static org.courseflow.integration.support.JsonChecks.json;
import static org.courseflow.integration.support.JsonChecks.parse;
import static org.courseflow.integration.support.JsonChecks.str;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.courseflow.CourseFlowApplication;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Start another real application context against an already populated database. */
class ExistingDatabaseStartupIT extends AdvancedHttpTest {
    @Value("${spring.datasource.url}") private String databaseUrl;
    @Value("${spring.datasource.username}") private String databaseUser;
    @Value("${spring.datasource.password}") private String databasePassword;
    @Value("${JWT_SECRET_BASE64}") private String jwtSecret;

    @Test
    void secondStartupPreservesBusinessDataAndInvalidatesExistingSessions() throws Exception {
        assertEquals("courseflow_test", jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals("test", databaseUser);
        assertTrue(databaseUrl.matches("jdbc:postgresql://[^/]+/courseflow_test(?:\\?.*)?"),
                "Startup must use the disposable Testcontainers database");

        Graph graph = graph();
        enroll(graph.course(), student);
        long assignment = assignment(graph.lesson());
        String submissionPath = "/api/assignments/" + assignment + "/submissions";
        String content = "Persisted before startup: " + unique();
        Object saved = expect(api("POST", submissionPath, student.token(), Map.of("content", content)), 201);
        long submission = id(saved);
        Object originalSubmission = expect(api("GET", submissionPath + "/me", student.token(), null), 200);
        assertEquals(submission, id(originalSubmission));
        assertEquals(content, field(originalSubmission, "content"));
        String coursePath = "/api/courses/" + graph.course();
        Object originalCourse = expect(api("GET", coursePath, null, null), 200);
        Object originalEnrollment = expect(api("GET", coursePath + "/enrollments/me", student.token(), null), 200);
        Object originalProfile = expect(api("GET", "/api/users/me", student.token(), null), 200);
        long adminId = jdbc.queryForObject("SELECT id FROM users WHERE system_admin", Long.class);
        Map<String, Long> originalCounts = businessCounts();
        assertTrue(count("SELECT count(*) FROM auth_sessions") >= 3);

        // The inherited test context uses ddl-auto=create. Override it for the second
        // startup to match the existing-database development configuration (update).
        // No @SpringBootTest on the new context: the actual ApplicationRunner beans run.
        SpringApplication application = new SpringApplication(CourseFlowApplication.class);
        application.setRegisterShutdownHook(false);
        try (ConfigurableApplicationContext restarted = application.run(
                "--spring.profiles.active=test",
                "--server.port=0",
                "--server.address=127.0.0.1",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + databaseUser,
                "--spring.datasource.password=" + databasePassword,
                "--spring.jpa.hibernate.ddl-auto=update",
                "--JWT_SECRET_BASE64=" + jwtSecret,
                "--COURSEFLOW_ADMIN_EMAIL=" + adminEmail,
                "--COURSEFLOW_ADMIN_PASSWORD=" + adminPassword)) {
            Integer port = restarted.getEnvironment().getProperty("local.server.port", Integer.class);
            assertNotNull(port);
            assertTrue(port > 0);
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5)).build();

            assertEquals(originalCounts, businessCounts(), "Existing business records must not be recreated or deleted");
            assertEquals(1L, count("SELECT count(*) FROM users WHERE system_admin"));
            assertEquals(adminId, jdbc.queryForObject("SELECT id FROM users WHERE system_admin", Long.class).longValue());
            assertEquals(0L, count("SELECT count(*) FROM auth_sessions"), "Startup must invalidate old sessions");
            assertEquals(1L, count("SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'ux_users_single_system_admin'"));
            assertEquals(1L, count("SELECT count(*) FROM pg_constraint WHERE conrelid = 'courses'::regclass AND conname = 'ck_cf_course_duration'"));

            assertEquals("UP", field(call(client, port, "GET", "/actuator/health/readiness", null, null, 200), "status"));
            assertEquals("AUTHENTICATION_REQUIRED", field(call(client, port, "GET", "/api/users/me", student.token(), null, 401), "code"));
            call(client, port, "GET", "/api/users/me", admin, null, 401);
            assertEquals(originalCourse, call(client, port, "GET", coursePath, null, null, 200));

            Object login = call(client, port, "POST", "/api/auth/login", null,
                    Map.of("email", student.email(), "password", PASSWORD), 200);
            assertEquals(student.id(), number(login, "userId"));
            String freshToken = str(field(login, "accessToken"));
            assertTrue(!freshToken.isBlank() && !freshToken.equals(student.token()));
            assertEquals(originalProfile, call(client, port, "GET", "/api/users/me", freshToken, null, 200));
            assertEquals(originalEnrollment, call(client, port, "GET", coursePath + "/enrollments/me", freshToken, null, 200));
            Object recovered = call(client, port, "GET", submissionPath + "/me", freshToken, null, 200);
            assertEquals(originalSubmission, recovered);
            assertEquals(submission, id(recovered));
            assertEquals(content, field(recovered, "content"));
            assertEquals(content, jdbc.queryForObject("SELECT content FROM submissions WHERE id = ?", String.class, submission));
            Object adminLogin = call(client, port, "POST", "/api/auth/login", null,
                    Map.of("email", adminEmail, "password", adminPassword), 200);
            assertEquals(adminId, number(adminLogin, "userId"));
            assertEquals(originalCounts, businessCounts());
        }
        // This class has one test: the original test context is closed by @DirtiesContext.
    }

    private Map<String, Long> businessCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        // Fixed table names, not request-controlled SQL.
        for (String table : List.of("users", "user_accounts", "user_roles", "categories", "courses",
                "modules", "lessons", "assignments", "enrollments", "submissions")) {
            result.put(table, count("SELECT count(*) FROM " + table));
        }
        return result;
    }

    private static Object call(HttpClient client, int port, String method, String path,
                               String token, Object payload, int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(45)).header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher body = payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json(payload));
        HttpResponse<String> response = client.send(request.method(method, body).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), method + " " + path);
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        assertTrue(mediaType.equals("application/json")
                        || (mediaType.startsWith("application/") && mediaType.endsWith("+json")),
                () -> method + " " + path + " returned unexpected Content-Type: " + contentType);
        return parse(response.body());
    }
}

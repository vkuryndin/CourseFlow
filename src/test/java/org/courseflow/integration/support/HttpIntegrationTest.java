package org.courseflow.integration.support;

import static org.courseflow.integration.support.JsonChecks.parse;
import static org.courseflow.integration.support.JsonChecks.str;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Real HTTP requests to the Spring Boot server running in the test JVM. */
public abstract class HttpIntegrationTest extends AbstractIntegrationTest {
  protected static final String PASSWORD = "CourseFlowTest2026!";
  protected static final String NEW_PASSWORD = "CourseFlowChanged2026!";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
  private final Map<String, String> variables = new HashMap<>();

  @LocalServerPort private int port;

  @Value("${COURSEFLOW_ADMIN_EMAIL}")
  protected String adminEmail;

  @Value("${COURSEFLOW_ADMIN_PASSWORD}")
  protected String adminPassword;

  protected record Response(int status, Object body, java.net.http.HttpHeaders headers) {
    public String header(String name) {
      return headers.firstValue(name).orElse(null);
    }
  }

  protected Response request(String method, String path, String body, String... headers)
      throws Exception {
    String resolvedPath = substitute(path);
    if (!resolvedPath.startsWith("/") || resolvedPath.startsWith("//")) {
      throw new IllegalArgumentException("Expected a local request path");
    }
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + resolvedPath))
            .timeout(Duration.ofSeconds(45));
    if (headers.length % 2 != 0) throw new IllegalArgumentException("Unpaired header");
    for (int i = 0; i < headers.length; i += 2) {
      builder.header(headers[i], substitute(headers[i + 1]));
    }
    var publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(substitute(body));
    var response =
        client.send(
            builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    Object parsedBody =
        response.body().isBlank()
            ? null
            : (contentType.contains("/json") || contentType.contains("+json")
                ? parse(response.body())
                : response.body());
    return new Response(response.statusCode(), parsedBody, response.headers());
  }

  protected Response api(String method, String path, String token, Object data) throws Exception {
    String body = data == null ? null : JsonChecks.json(data);
    if (token == null) {
      return request(method, path, body, "Content-Type", "application/json");
    }
    return request(
        method, path, body, "Authorization", "Bearer " + token, "Content-Type", "application/json");
  }

  protected Object expect(Response response, int status) {
    assertEquals(status, response.status(), "Unexpected HTTP status");
    return response.body();
  }

  protected void set(Object key, Object value) {
    variables.put(str(key), str(value));
  }

  protected String get(Object key) {
    return variables.get(str(key));
  }

  protected void clear(Object key) {
    variables.remove(str(key));
  }

  private String substitute(String text) {
    Matcher matcher = PLACEHOLDER.matcher(text);
    return matcher.replaceAll(
        match -> {
          String value = variables.get(match.group(1));
          if (value == null)
            throw new IllegalStateException("Missing scenario variable: " + match.group(1));
          return Matcher.quoteReplacement(value);
        });
  }

  protected void initializeVariables() {
    variables.clear();
    String run = Long.toString(System.currentTimeMillis());
    set("run_id", run);
    set("admin_email", adminEmail);
    set("admin_password", adminPassword);
    set("test_password", PASSWORD);
    set("new_password", NEW_PASSWORD);
    set("student_version", "0");
    set("temp_password_email", "reg.temporary.password." + run + "@courseflow.local");
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    set("course_start", today.plusDays(1));
    set("session_start", today.plusDays(3) + "T10:00:00Z");
    set("session_end", today.plusDays(3) + "T11:00:00Z");
    set("session_new_start", today.plusDays(3) + "T12:00:00Z");
    set("session_new_end", today.plusDays(3) + "T13:00:00Z");
    set("temp_session_start", today.plusDays(4) + "T10:00:00Z");
    set("temp_session_end", today.plusDays(4) + "T11:00:00Z");
    set("due_at", today.plusDays(20) + "T18:00:00Z");
    set("cf620_earlier_due", today.plusDays(19) + "T18:00:00Z");
    set("cf620_later_due", today.plusDays(21) + "T18:00:00Z");
    set("cf620_admin_due", today.plusDays(22) + "T18:00:00Z");
    set("cf620_past_due", today.minusDays(1) + "T18:00:00Z");
    set("student_email", "reg.student." + run + "@courseflow.local");
    set("teacher_email", "reg.teacher." + run + "@courseflow.local");
    set("other_email", "reg.other." + run + "@courseflow.local");
    set("other_updated_email", "reg.other.updated." + run + "@courseflow.local");
  }
}

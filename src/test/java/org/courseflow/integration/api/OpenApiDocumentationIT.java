package org.courseflow.integration.api;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.courseflow.integration.support.HttpIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiDocumentationIT extends HttpIntegrationTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Test
    void allOperationsHaveRussianDocumentationAndUniqueIdentifiers() throws Exception {
        var doc = object(expect(request("GET", "/v3/api-docs", null), 200));
        var paths = object(doc.get("paths"));
        var ids = new HashSet<String>();
        int count = 0;
        for (var path : paths.entrySet()) {
            for (var entry : object(path.getValue()).entrySet()) {
                if (!HTTP_METHODS.contains(entry.getKey())) continue;
                String route = entry.getKey() + " " + path.getKey();
                var operation = object(entry.getValue());
                assertRussian(operation.get("summary"), route);
                assertRussian(operation.get("description"), route);
                var tags = assertInstanceOf(List.class, operation.get("tags"), route);
                assertEquals(1, tags.size(), route);
                assertRussian(tags.get(0), route);
                String id = assertInstanceOf(String.class, operation.get("operationId"), route);
                assertFalse(id.isBlank(), route);
                assertTrue(ids.add(id), "Duplicate operation ID: " + id);
                var responses = object(operation.get("responses"));
                assertTrue(responses.keySet().stream().anyMatch(code -> code.startsWith("2")), route);
                for (var response : responses.entrySet()) {
                    assertRussian(object(response.getValue()).get("description"), route + " " + response.getKey());
                }
                count++;
            }
        }
        assertEquals(107, count);
    }

    @Test
    void documentationPreservesSecuritySchemasAndResponseCodes() throws Exception {
        var doc = object(expect(request("GET", "/v3/api-docs", null), 200));
        var paths = object(doc.get("paths"));
        var login = operation(paths, "/api/auth/login", "post");
        assertEquals(List.of(), login.get("security"));
        var loginResponses = object(login.get("responses"));
        assertTrue(loginResponses.keySet().containsAll(Set.of("200", "400", "401", "415", "429")));
        assertFalse(loginResponses.containsKey("409"));
        assertTrue(object(object(loginResponses.get("429")).get("headers")).containsKey("Retry-After"));
        var enroll = operation(paths, "/api/courses/{courseId}/enrollments", "post");
        assertTrue(object(enroll.get("responses")).keySet().containsAll(Set.of("200", "201", "401", "403", "409")));
        var logout = operation(paths, "/api/auth/logout", "post");
        assertTrue(object(logout.get("responses")).containsKey("204"));
        var schemas = object(object(doc.get("components")).get("schemas"));
        var course = object(object(schemas.get("CreateCourseRequest")).get("properties"));
        assertRussian(object(course.get("duration")).get("description"), "duration");
        Object durationType = object(course.get("duration")).get("type");
        assertTrue("integer".equals(durationType)
                || durationType instanceof List<?> types && types.contains("integer"));
        var errors = object(object(schemas.get("ApiErrorResponse")).get("properties"));
        assertEquals(Set.of("timestamp", "status", "error", "message", "path", "code", "requestId"), errors.keySet());
        var diagnostics = operation(paths, "/api/system/info", "get");
        assertTrue(diagnostics.get("description").toString().contains("ADMIN"));
    }

    private static Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return object(object(paths.get(path)).get(method));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return (Map<String, Object>) assertInstanceOf(Map.class, value);
    }

    private static void assertRussian(Object value, String context) {
        String text = assertInstanceOf(String.class, value, context);
        assertTrue(text.matches("(?s).*\\p{IsCyrillic}.*"), context + ": " + text);
    }
}

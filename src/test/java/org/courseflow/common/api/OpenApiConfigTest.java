package org.courseflow.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

class OpenApiConfigTest {
  private final OpenApiConfig config = new OpenApiConfig();

  private Operation operation(String status) {
    return new Operation()
        .responses(new ApiResponses().addApiResponse(status, new ApiResponse().description("OK")));
  }

  @Test
  void declaresBearerSecurityAndErrorContract() {
    var properties = new Properties();
    properties.setProperty("version", "9.8.7");
    var factory = new StaticListableBeanFactory();
    factory.addBean("buildProperties", new BuildProperties(properties));
    var api = config.courseFlowOpenApi(factory.getBeanProvider(BuildProperties.class));
    assertEquals("9.8.7", api.getInfo().getVersion());
    assertEquals("CourseFlow REST API", api.getInfo().getTitle());
    assertEquals("bearer", api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
    assertTrue(
        api.getComponents()
            .getSchemas()
            .get("ApiErrorResponse")
            .getProperties()
            .containsKey("requestId"));
    assertTrue(api.getSecurity().get(0).containsKey("bearerAuth"));
  }

  @Test
  void customizerDocumentsPublicProtectedAndRateLimitedOperations() {
    var login = operation("200").requestBody(new RequestBody());
    var course = operation("201").requestBody(new RequestBody());
    var publicGet = operation("200");
    var status = operation("200");
    var quiz = operation("200");
    var api =
        new OpenAPI()
            .path("/api/auth/login", new PathItem().post(login))
            .path("/api/courses", new PathItem().get(publicGet).post(course))
            .path("/api/system/status", new PathItem().get(status))
            .path("/api/quizzes/{id}/attempts", new PathItem().get(quiz));

    config.publicOperations().customise(api);

    assertTrue(login.getSecurity().isEmpty());
    assertTrue(login.getResponses().containsKey("401"));
    assertTrue(login.getResponses().containsKey("415"));
    assertNotNull(login.getResponses().get("429").getHeaders().get("Retry-After"));
    assertTrue(publicGet.getSecurity().isEmpty());
    assertFalse(publicGet.getResponses().containsKey("401"));
    assertTrue(course.getResponses().containsKey("403"));
    assertNotNull(course.getResponses().get("201").getHeaders().get("Location"));
    assertTrue(status.getResponses().containsKey("503"));
    assertTrue(quiz.getResponses().containsKey("404"));
    assertTrue(quiz.getResponses().containsKey("409"));
    assertNotNull(quiz.getResponses().get("200").getHeaders().get("X-Request-Id"));
  }

  @Test
  void customizerHandlesMissingPathsOrResponsesAndPreservesExistingError() {
    var api = new OpenAPI();
    config.publicOperations().customise(api);
    assertNotNull(api.getComponents().getSchemas().get("ApiErrorResponse"));
    var post = operation("201");
    post.getResponses().addApiResponse("400", new ApiResponse().description("Custom validation"));
    api.path("/api/auth/register", new PathItem().post(post));
    api.path("/api/empty", new PathItem().get(new Operation()));
    config.publicOperations().customise(api);
    assertEquals("Custom validation", post.getResponses().get("400").getDescription());
    assertFalse(post.getResponses().get("201").getHeaders().containsKey("Location"));
  }
}

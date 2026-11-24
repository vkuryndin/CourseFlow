package org.courseflow.common.api;

import org.courseflow.security.PublicEndpoints;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI courseFlowOpenApi() {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Jwt.class, Authentication.class);
        Components components = new Components().addSecuritySchemes(BEARER,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        registerErrorSchema(components);
        return new OpenAPI()
                .info(new Info().title("CourseFlow REST API").version("0.1.0")
                        .description("Learning management platform. Login to obtain a JWT, then use Authorize. "
                                + "Role and ownership restrictions are enforced by the backend. "
                                + "Paginated endpoints use zero-based page numbers and a maximum size of 100."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public OpenApiCustomizer publicOperations() {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new Components());
            registerErrorSchema(api.getComponents());
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (item.getPost() != null && PublicEndpoints.isPublicPost(path)) {
                    item.getPost().setSecurity(List.of());
                }
                if (item.getGet() != null && PublicEndpoints.isPublicGet(path)) {
                    item.getGet().setSecurity(List.of());
                }
                item.readOperationsMap().forEach((method, operation) -> {
                    if (operation.getResponses() == null) return;
                    addError(operation, "400", "Invalid request parameters or body");
                    addError(operation, "500", "Unexpected server error");
                    if (operation.getSecurity() == null || !operation.getSecurity().isEmpty()) {
                        addError(operation, "401", "Authentication required or token invalid");
                        addError(operation, "403", "Insufficient role or resource access");
                    }
                    if (path.contains("{")) addError(operation, "404", "Resource not found");
                    if (operation.getRequestBody() != null) {
                        addError(operation, "415", "Unsupported request content type");
                    }
                    if (method != PathItem.HttpMethod.GET || path.contains("/quizzes/")) {
                        addError(operation, "409", "Business or concurrency conflict");
                    }
                    if (method == PathItem.HttpMethod.POST && PublicEndpoints.isRateLimitedPost(path)) {
                        addError(operation, "429", "Rate limit exceeded");
                    }
                    if (path.equals("/api/auth/login")) addError(operation, "401", "Invalid credentials");
                    if (path.equals("/api/system/status")) addError(operation, "503", "Database unavailable");
                    if (operation.getResponses().containsKey("429")) {
                        operation.getResponses().get("429").addHeaderObject("Retry-After",
                                new Header().description("Seconds until a retry is allowed")
                                        .schema(new io.swagger.v3.oas.models.media.IntegerSchema()));
                    }
                    if (method == PathItem.HttpMethod.POST && operation.getResponses().containsKey("201")
                            && !path.equals("/api/auth/register") && !path.endsWith("/questions")) {
                        operation.getResponses().get("201").addHeaderObject("Location",
                                new Header().description("Relative URI of the created resource")
                                        .schema(new StringSchema()));
                    }
                    operation.getResponses().values().forEach(response -> response.addHeaderObject(
                            RequestIdFilter.HEADER, new Header().description("Server-generated request identifier")
                                    .schema(new StringSchema().format("uuid"))));
                });
            });
        };
    }

    // Register the actual error contract explicitly, including for the final generated document.
    private void registerErrorSchema(Components components) {
        Schema<?> error = new io.swagger.v3.oas.models.media.ObjectSchema()
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("status", new io.swagger.v3.oas.models.media.IntegerSchema().format("int32"))
                .addProperty("error", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("path", new StringSchema())
                .addProperty("code", new StringSchema())
                .addProperty("requestId", new StringSchema());
        components.addSchemas("ApiErrorResponse", error);
    }

    private void addError(Operation operation, String code, String description) {
        if (operation.getResponses().containsKey(code)) return;
        operation.getResponses().addApiResponse(code, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))));
    }

}

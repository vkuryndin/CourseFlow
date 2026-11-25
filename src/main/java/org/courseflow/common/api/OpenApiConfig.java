package org.courseflow.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {
  private static final String BEARER = "bearerAuth";

  @Bean
  public OpenAPI courseFlowOpenApi(ObjectProvider<BuildProperties> buildProperties) {
    BuildProperties build = buildProperties.getIfAvailable();
    String version =
        build == null
            ? OpenApiConfig.class.getPackage().getImplementationVersion()
            : build.getVersion();
    SpringDocUtils.getConfig().addRequestWrapperToIgnore(Jwt.class, Authentication.class);
    Components components =
        new Components()
            .addSecuritySchemes(
                BEARER,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Токен доступа из ответа на вход."));
    registerErrorSchema(components);
    return new OpenAPI()
        .info(
            new Info()
                .title("CourseFlow REST API")
                .version(version == null ? "unknown" : version)
                .description(
                    "Образовательная платформа. Для защищённых операций войдите и укажите JWT в Authorize. "
                        + "Нумерация страниц с 0, размер страницы — до 100."))
        .components(components)
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  @Bean
  public OpenApiCustomizer publicOperations() {
    return api -> {
      if (api.getComponents() == null) {
        api.setComponents(new Components());
      }
      registerErrorSchema(api.getComponents());
      api.getComponents().getSchemas().forEach(OpenApiFields::describe);
      if (api.getPaths() == null) {
        return;
      }
      var tags = new TreeSet<String>();
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            boolean isPublic =
                                method == PathItem.HttpMethod.GET
                                        && PublicEndpoints.isPublicGet(path)
                                    || method == PathItem.HttpMethod.POST
                                        && PublicEndpoints.isPublicPost(path);
                            if (isPublic) {
                              operation.setSecurity(List.of());
                            }
                            var doc = OpenApiDocumentation.find(method.name(), path);
                            if (doc != null) {
                              operation.setTags(List.of(doc.tag()));
                              tags.add(doc.tag());
                              operation.setSummary(doc.summary());
                              operation.setDescription(doc.description());
                              operation.setOperationId(doc.operationId());
                              describeParameters(path, operation, doc);
                            }
                            describeResponses(path, method, operation, isPublic, doc);
                          }));
      if (!tags.isEmpty()) {
        api.setTags(tags.stream().map(name -> new Tag().name(name)).toList());
      }
    };
  }

  private static final Map<String, String> PARAMETER_DESCRIPTIONS =
      Map.ofEntries(
          Map.entry("page", "Номер страницы с 0"),
          Map.entry("size", "Размер страницы (1–100)"),
          Map.entry("q", "Строка поиска"),
          Map.entry("search", "Устаревший аналог q"),
          Map.entry("direction", "asc — по возрастанию, desc — по убыванию"),
          Map.entry("categoryId", "ID категории"),
          Map.entry("teacherId", "ID преподавателя"),
          Map.entry("courseId", "ID курса"),
          Map.entry("moduleId", "ID модуля"),
          Map.entry("lessonId", "ID урока"),
          Map.entry("sessionId", "ID занятия"),
          Map.entry("tagId", "ID тега"),
          Map.entry("assignmentId", "ID задания"),
          Map.entry("submissionId", "ID ответа на задание"),
          Map.entry("quizId", "ID теста"),
          Map.entry("questionId", "ID вопроса"),
          Map.entry("attemptId", "ID попытки"),
          Map.entry("studentId", "ID студента"),
          Map.entry("status", "Фильтр по статусу"),
          Map.entry("role", "Фильтр по роли"),
          Map.entry("enrollmentStatus", "Статус записи на курс"),
          Map.entry(
              "reviewStatus",
              "NOT_SUBMITTED — все несданные задания; OVERDUE — только просроченные"),
          Map.entry("type", "Тип: задание или тест"),
          Map.entry("columnPage", "Номер страницы столбцов с 0"),
          Map.entry("columnSize", "Столбцов на странице (1–100)"),
          Map.entry(
              "includeDetails",
              "Включить детали; требует courseId. По умолчанию true при наличии courseId"),
          Map.entry("detailPage", "Номер страницы деталей с 0"),
          Map.entry("detailSize", "Элементов в списке деталей (1–100)"));

  private void describeParameters(
      String path, Operation operation, OpenApiDocumentation.Endpoint doc) {
    if (operation.getParameters() != null) {
      operation
          .getParameters()
          .forEach(
              parameter -> {
                String name = parameter.getName();
                if (name == null) {
                  return;
                }
                if (name.equals("sortBy")) {
                  parameter.setDescription(
                      doc.sortFields().isEmpty()
                          ? "Поле сортировки"
                          : "Сортировка: " + doc.sortFields());
                } else if (name.equals("id")) {
                  String description =
                      switch (path.split("/")[2]) {
                        case "categories" -> "ID категории";
                        case "courses" -> "ID курса";
                        case "tags" -> "ID тега";
                        case "teachers" -> "ID преподавателя";
                        case "users" -> "ID пользователя";
                        default -> "Идентификатор";
                      };
                  parameter.setDescription(description);
                } else if (PARAMETER_DESCRIPTIONS.containsKey(name)) {
                  parameter.setDescription(PARAMETER_DESCRIPTIONS.get(name));
                }
              });
    }
    if (operation.getRequestBody() != null) {
      operation.getRequestBody().setDescription("Данные запроса");
    }
  }

  private void describeResponses(
      String path,
      PathItem.HttpMethod method,
      Operation operation,
      boolean isPublic,
      OpenApiDocumentation.Endpoint doc) {
    var responses = operation.getResponses();
    if (responses == null) {
      return;
    }
    // Keep generated response schemas and existing explicitly documented errors.
    // Only supply descriptions and applicable HTTP codes.
    if (doc != null) {
      for (String code : doc.successCodes().split(",")) {
        ApiResponse response = responses.get(code);
        if (response != null) {
          response.setDescription(
              switch (code) {
                case "200" ->
                    doc.successCodes().contains("201")
                        ? "Существующая запись или попытка"
                        : "Успешно";
                case "201" -> "Создано";
                case "202" -> "Запрос принят";
                case "204" -> "Выполнено, без тела ответа";
                default -> "Успешно";
              });
        }
      }
    }
    if (doc == null
        || operation.getRequestBody() != null
        || path.contains("{")
        || operation.getParameters() != null && !operation.getParameters().isEmpty()) {
      addError(operation, "400", "Некорректные параметры или данные запроса");
    }
    addError(operation, "500", "Внутренняя ошибка сервера");
    if (!isPublic) {
      addError(operation, "401", "Требуется действительный JWT");
      addError(operation, "403", "Недостаточно прав доступа");
    }
    if (doc != null ? doc.notFound() : path.contains("{")) {
      addError(operation, "404", "Объект не найден");
    }
    if (responses.containsKey("200") || responses.containsKey("201")) {
      addError(operation, "406", "Формат ответа не поддерживается");
    }
    if (operation.getRequestBody() != null) {
      addError(operation, "415", "Формат тела запроса не поддерживается");
    }
    if (doc != null
        ? doc.conflict()
        : method != PathItem.HttpMethod.GET || path.contains("/quizzes/")) {
      addError(operation, "409", "Конфликт бизнес-правил или версий данных");
    }
    if (method == PathItem.HttpMethod.POST && PublicEndpoints.isRateLimitedPost(path)) {
      addError(operation, "429", "Превышен лимит запросов");
    }
    if (path.equals("/api/auth/login")) {
      addError(operation, "401", "Неверный email или пароль");
    }
    if (path.equals("/api/system/status")) {
      addError(operation, "503", "База данных недоступна");
    }
    if (responses.containsKey("429")) {
      responses
          .get("429")
          .addHeaderObject(
              "Retry-After",
              new Header()
                  .description("Через сколько секунд можно повторить запрос")
                  .schema(new IntegerSchema()));
    }
    if (responses.containsKey("201")
        && (doc != null
            ? doc.location()
            : method == PathItem.HttpMethod.POST
                && !path.equals("/api/auth/register")
                && !path.endsWith("/questions"))) {
      responses
          .get("201")
          .addHeaderObject(
              "Location",
              new Header()
                  .description("Относительный URI созданного объекта")
                  .schema(new StringSchema()));
    }
    responses
        .values()
        .forEach(
            response ->
                response.addHeaderObject(
                    RequestIdFilter.HEADER,
                    new Header()
                        .description("Идентификатор запроса")
                        .schema(new StringSchema().format("uuid"))));
  }

  private void registerErrorSchema(Components components) {
    Schema<?> error =
        new io.swagger.v3.oas.models.media.ObjectSchema()
            .addProperty("timestamp", new StringSchema().format("date-time"))
            .addProperty("status", new IntegerSchema().format("int32"))
            .addProperty("error", new StringSchema())
            .addProperty("message", new StringSchema())
            .addProperty("path", new StringSchema())
            .addProperty("code", new StringSchema())
            .addProperty("requestId", new StringSchema());
    OpenApiFields.describe("ApiErrorResponse", error);
    components.addSchemas("ApiErrorResponse", error);
  }

  private void addError(Operation operation, String code, String description) {
    if (operation.getResponses().containsKey(code)) {
      return;
    }
    operation
        .getResponses()
        .addApiResponse(
            code,
            new ApiResponse()
                .description(description)
                .content(
                    new Content()
                        .addMediaType(
                            "application/json",
                            new MediaType()
                                .schema(
                                    new Schema<>()
                                        .$ref("#/components/schemas/ApiErrorResponse")))));
  }
}

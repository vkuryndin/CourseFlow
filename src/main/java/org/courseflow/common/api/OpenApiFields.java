package org.courseflow.common.api;

import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Set;

/** Short descriptions of JSON fields; never changes the serialization or validation contract. */
final class OpenApiFields {
    private OpenApiFields() {}

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("accessToken", "Токен доступа JWT"),
            Map.entry("accessTokenLifetimeSeconds", "Срок действия JWT, с"),
            Map.entry("active", "Активные"),
            Map.entry("activeCourseCount", "Активные курсы"),
            Map.entry("activeEnrollmentCount", "Активные записи"),
            Map.entry("activeOnActiveCourses", "Активные записи на активные курсы"),
            Map.entry("activeProfiles", "Активные профили Spring"),
            Map.entry("activeStudentCount", "Активные студенты"),
            Map.entry("admins", "Администраторы"),
            Map.entry("answers", "Ответы на вопросы"),
            Map.entry("application", "Приложение"),
            Map.entry("architecture", "Архитектура процессора"),
            Map.entry("archived", "Архивные"),
            Map.entry("archivedCourseCount", "Архивные курсы"),
            Map.entry("assessmentId", "ID задания или теста"),
            Map.entry("assignmentId", "ID задания"),
            Map.entry("assignmentTitle", "Название задания"),
            Map.entry("assignments", "Задания"),
            Map.entry("attempt", "Попытка тестирования"),
            Map.entry("attemptId", "ID попытки"),
            Map.entry("attemptNumber", "Номер попытки"),
            Map.entry("attemptsUsed", "Использованные попытки"),
            Map.entry("availableProcessors", "Доступные процессоры"),
            Map.entry("avatarUrl", "URL аватара"),
            Map.entry("averageRating", "Средняя оценка курса"),
            Map.entry("bestScore", "Лучший результат, %"),
            Map.entry("bio", "О себе"),
            Map.entry("buildTime", "Время сборки"),
            Map.entry("cacheSeconds", "Срок кеширования, с"),
            Map.entry("categoryId", "ID категории"),
            Map.entry("code", "Код ошибки"),
            Map.entry("collectedAt", "Время сбора данных"),
            Map.entry("columnPage", "Номер страницы столбцов с 0"),
            Map.entry("columnSize", "Столбцов на странице (1–100)"),
            Map.entry("columns", "Столбцы журнала"),
            Map.entry("comment", "Комментарий к отзыву"),
            Map.entry("completedAssessments", "Выполненные задания и тесты"),
            Map.entry("connectionTimeoutMillis", "Таймаут соединения, мс"),
            Map.entry("content", "Содержимое"),
            Map.entry("correct", "Правильный вариант"),
            Map.entry("courseId", "ID курса"),
            Map.entry("courseStatus", "Статус курса"),
            Map.entry("courseTitle", "Название курса"),
            Map.entry("courses", "Курсы"),
            Map.entry("createdAt", "Дата и время создания"),
            Map.entry("currentPassword", "Текущий пароль (до 72 байт UTF-8)"),
            Map.entry("database", "База данных"),
            Map.entry("databaseName", "Имя базы данных"),
            Map.entry("databaseUser", "Пользователь базы данных"),
            Map.entry("defaultProfiles", "Профили Spring по умолчанию"),
            Map.entry("deleted", "Удалённые"),
            Map.entry("description", "Описание"),
            Map.entry("detailPage", "Номер страницы деталей с 0"),
            Map.entry("detailSize", "Элементов в списке деталей (1–100)"),
            Map.entry("detailsIncluded", "Детали включены"),
            Map.entry("displayName", "Отображаемое имя"),
            Map.entry("driver", "JDBC-драйвер"),
            Map.entry("driverVersion", "Версия драйвера"),
            Map.entry("dueAt", "Срок сдачи"),
            Map.entry("duration", "Продолжительность курса, дни"),
            Map.entry("email", "Электронная почта"),
            Map.entry("endedAt", "Время прекращения записи"),
            Map.entry("endsAt", "Окончание занятия"),
            Map.entry("enrolledAt", "Дата записи на курс"),
            Map.entry("enrollmentStatus", "Статус записи на курс"),
            Map.entry("enrollments", "Записи на курсы"),
            Map.entry("error", "Название HTTP-ошибки"),
            Map.entry("expiresAt", "Время истечения срока"),
            Map.entry("feedback", "Комментарий преподавателя"),
            Map.entry("finishedAt", "Время завершения попытки"),
            Map.entry("first", "Первая страница"),
            Map.entry("format", "Формат занятия"),
            Map.entry("freeMemoryBytes", "Свободная память JVM, байты"),
            Map.entry("gitCommit", "Идентификатор коммита"),
            Map.entry("hibernateVersion", "Версия Hibernate"),
            Map.entry("host", "Адрес SMTP-сервера"),
            Map.entry("hostName", "Имя хоста"),
            Map.entry("id", "Идентификатор"),
            Map.entry("idle", "Свободные соединения"),
            Map.entry("inactive", "Неактивные"),
            Map.entry("javaVendor", "Поставщик Java"),
            Map.entry("javaVersion", "Версия Java"),
            Map.entry("jvmName", "Название JVM"),
            Map.entry("jvmStartedAt", "Время запуска JVM"),
            Map.entry("last", "Последняя страница"),
            Map.entry("lessonId", "ID урока"),
            Map.entry("lessonTitle", "Название урока"),
            Map.entry("localAddress", "Локальный адрес сервера"),
            Map.entry("localPort", "Локальный порт сервера"),
            Map.entry("mail", "Почтовая конфигурация"),
            Map.entry("maxAttempts", "Максимум попыток"),
            Map.entry("maxScore", "Максимальный балл"),
            Map.entry("maximumSize", "Максимальный размер пула"),
            Map.entry("meetingUrl", "Ссылка на онлайн-занятие"),
            Map.entry("message", "Сообщение об ошибке"),
            Map.entry("minimumIdle", "Минимум свободных соединений"),
            Map.entry("moduleId", "ID модуля"),
            Map.entry("moduleTitle", "Название модуля"),
            Map.entry("name", "Название"),
            Map.entry("newPassword", "Новый пароль (8–72 символа, до 72 байт UTF-8)"),
            Map.entry("optionId", "ID варианта ответа"),
            Map.entry("optionIds", "ID выбранных вариантов"),
            Map.entry("optionText", "Текст варианта ответа"),
            Map.entry("options", "Варианты ответа"),
            Map.entry("osName", "Операционная система"),
            Map.entry("osVersion", "Версия ОС"),
            Map.entry("page", "Номер страницы с 0"),
            Map.entry("password", "Пароль (до 72 байт UTF-8)"),
            Map.entry("passwordChangeRequired", "Необходима смена пароля"),
            Map.entry("path", "Путь запроса"),
            Map.entry("pending", "Ожидающие соединение"),
            Map.entry("pendingSubmissionCount", "Ответы на проверке"),
            Map.entry("pendingSubmissions", "Ответы на проверке"),
            Map.entry("performancePercent", "Успеваемость, %"),
            Map.entry("pool", "Пул соединений"),
            Map.entry("port", "Порт SMTP-сервера"),
            Map.entry("position", "Позиция в списке"),
            Map.entry("product", "СУБД"),
            Map.entry("progressPercent", "Прогресс, %"),
            Map.entry("queryTimeMillis", "Время диагностического запроса, мс"),
            Map.entry("questionId", "ID вопроса"),
            Map.entry("questionText", "Текст вопроса"),
            Map.entry("quizId", "ID теста"),
            Map.entry("quizTitle", "Название теста"),
            Map.entry("quizzes", "Тесты"),
            Map.entry("rating", "Оценка курса (1–5)"),
            Map.entry("recentQuizResults", "Последние результаты тестов"),
            Map.entry("requestId", "Идентификатор запроса"),
            Map.entry("results", "Результаты"),
            Map.entry("reviewCount", "Количество отзывов"),
            Map.entry("roles", "Роли пользователя"),
            Map.entry("runtime", "Среда выполнения"),
            Map.entry("schema", "Схема БД"),
            Map.entry("schemaManagement", "Режим управления схемой"),
            Map.entry("score", "Полученный балл"),
            Map.entry("selectedAnswers", "Выбранные ответы"),
            Map.entry("serverAddress", "Адрес сервера БД"),
            Map.entry("serverEndpoint", "Адрес и порт приложения"),
            Map.entry("serverPort", "Порт сервера БД"),
            Map.entry("sessionId", "ID занятия"),
            Map.entry("size", "Размер страницы (1–100)"),
            Map.entry("sizeBytes", "Размер базы данных, байты"),
            Map.entry("snapshot", "Снимок диагностики"),
            Map.entry("springBootVersion", "Версия Spring Boot"),
            Map.entry("sslEnabled", "SSL включён"),
            Map.entry("startDate", "Дата начала курса"),
            Map.entry("startTlsEnabled", "STARTTLS включён"),
            Map.entry("startTlsRequired", "STARTTLS обязателен"),
            Map.entry("startsAt", "Начало занятия"),
            Map.entry("status", "Статус"),
            Map.entry("studentId", "ID студента"),
            Map.entry("studentName", "Имя студента"),
            Map.entry("studentStatus", "Статус студента"),
            Map.entry("students", "Студенты"),
            Map.entry("submissionId", "ID ответа на задание"),
            Map.entry("submittedAt", "Время сдачи"),
            Map.entry("systemAdmin", "Системный администратор"),
            Map.entry("tableCount", "Количество таблиц"),
            Map.entry("takenAt", "Время начала попытки"),
            Map.entry("teacherId", "ID преподавателя"),
            Map.entry("teachers", "Преподаватели"),
            Map.entry("text", "Текст"),
            Map.entry("timeLimit", "Лимит времени теста, мин"),
            Map.entry("timeZone", "Часовой пояс"),
            Map.entry("timestamp", "Дата и время ошибки"),
            Map.entry("title", "Название"),
            Map.entry("token", "Токен сброса пароля"),
            Map.entry("tokenType", "Тип токена"),
            Map.entry("total", "Всего"),
            Map.entry("totalAssessments", "Всего заданий и тестов"),
            Map.entry("totalAssignments", "Всего заданий"),
            Map.entry("totalColumnPages", "Всего страниц столбцов"),
            Map.entry("totalColumns", "Всего столбцов"),
            Map.entry("totalCourseCount", "Всего курсов"),
            Map.entry("totalElements", "Всего элементов"),
            Map.entry("totalMemoryBytes", "Выделенная память JVM, байты"),
            Map.entry("totalPages", "Всего страниц"),
            Map.entry("totalQuizzes", "Всего тестов"),
            Map.entry("totalUpcomingDeadlines", "Всего предстоящих сроков сдачи"),
            Map.entry("type", "Тип"),
            Map.entry("upcomingDeadlines", "Предстоящие сроки сдачи"),
            Map.entry("upcomingSessions", "Предстоящие занятия"),
            Map.entry("uptimeSeconds", "Время работы JVM, с"),
            Map.entry("userId", "ID пользователя"),
            Map.entry("users", "Пользователи"),
            Map.entry("version", "Версия объекта для оптимистической блокировки"),
            Map.entry("videoUrl", "URL видео"),
            Map.entry("withdrawn", "Отписанные")
    );

    private static final Set<String> CREATE_OR_UPDATE = Set.of(
            "CreateQuestionRequest", "CreateAssignmentRequest", "CreateLessonSessionRequest",
            "CategoryCreateRequest", "TagRequest");

    static void describe(String schemaName, Schema<?> schema) {
        if (schema.getProperties() == null) return;
        schema.getProperties().forEach((name, property) -> {
            String description = DESCRIPTIONS.get(name);
            if (description != null) property.setDescription(description(schemaName, name, description));
            if (schemaName.endsWith("Request")
                    && !("version".equals(name) && CREATE_OR_UPDATE.contains(schemaName))) {
                addExample(name, property);
            }
        });
    }

    private static String description(String schemaName, String field, String fallback) {
        if ("version".equals(field) && CREATE_OR_UPDATE.contains(schemaName)) {
            return "Версия объекта: обязательна при PUT, не передаётся при POST.";
        }
        if ("version".equals(field) && schemaName.equals("Application")) return "Версия приложения";
        if ("version".equals(field) && schemaName.equals("Database")) return "Версия СУБД";
        if ("active".equals(field) && schemaName.equals("Pool")) return "Занятые соединения";
        if ("content".equals(field) && schemaName.startsWith("PageResponse")) return "Элементы страницы";
        if ("content".equals(field) && (schemaName.contains("Lesson"))) return "Материал урока";
        if ("content".equals(field) && schemaName.contains("Submission")) return "Ответ студента";
        if ("score".equals(field) && schemaName.contains("Quiz")) return "Результат теста, %";
        if ("password".equals(field) && !schemaName.equals("LoginRequest")) {
            return "Пароль (8–72 символа, до 72 байт UTF-8)";
        }
        if ("passwordChangeRequired".equals(field) && schemaName.equals("AdminCreateUserRequest")) {
            return "Смена пароля при первом входе; по умолчанию true";
        }
        if ("meetingUrl".equals(field) && schemaName.endsWith("Request")) {
            return "Обязательна для ONLINE; для OFFLINE не передаётся";
        }
        return fallback;
    }

    private static void addExample(String field, Schema<?> property) {
        if (property.getExample() != null || property.get$ref() != null) return;
        switch (field) {
            case "email" -> property.setExample("student@example.com");
            case "password", "currentPassword" -> property.setExample("ExamplePassword2026!");
            case "newPassword" -> property.setExample("NewExamplePassword2026!");
            case "description" -> property.setExample("Практические основы разработки");
            case "displayName" -> property.setExample("Иван Петров");
            case "title" -> property.setExample("Основы Java");
            case "name" -> property.setExample("Программирование");
            case "duration" -> property.setExample(42);
            case "timeLimit" -> property.setExample(30);
            case "position", "categoryId", "teacherId", "questionId" -> property.setExample(1);
            case "version" -> property.setExample(0);
            case "rating" -> property.setExample(5);
            case "maxScore" -> property.setExample(100);
            case "maxAttempts" -> property.setExample(3);
            default -> { }
        }
    }
}

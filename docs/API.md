# CourseFlow — REST API

[README](../README.md) · [Архитектура](ARCHITECTURE.md) · [Настройка](SETUP.md)

## 1. Общие сведения

API использует REST и JSON. Базовый путь `/api`. В dev backend доступен на `http://localhost:8080`, через Vite — `http://localhost:5173`, через Nginx на AWS — `http://3.79.152.70:8081`. OpenAPI в dev: `/v3/api-docs`, `/v3/api-docs.yaml`; Swagger UI: `/swagger-ui/index.html`. В профиле `aws` Swagger UI и OpenAPI отключены.

## 2. Аутентификация и авторизация

`POST /api/auth/login` принимает `email` и `password`. Ответ `LoginResponse`: accessToken, tokenType, expiresAt, userId, displayName, roles, passwordChangeRequired. Сервер проверяет состояние JWT-сессии. Неавторизованным доступны только явно разрешённые маршруты. Роли STUDENT, TEACHER, ADMIN могут сочетаться. Владение курсом, запись на него и доступ к объектам проверяются отдельно; роль сама по себе не гарантирует доступ.

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "admin@courseflow.local",
  "password": "<пароль системного администратора>"
}
```

```http
GET http://localhost:8080/api/users/me
Authorization: Bearer <accessToken>
```

При `passwordChangeRequired=true` защищённые операции ограничены сменой пароля и выходом. Восстановление пароля: forgot-password → письмо → reset-password; токен восстановления действителен 30 минут. Вход и восстановление пароля защищены ограничением частоты запросов. JWT действует один час с момента выпуска; продления по активности и refresh token нет.

После успешного `POST /api/auth/reset-password` (204) клиент выполняет обычный `POST /api/auth/login` с email и новым паролем. Ответ восстановления не содержит email владельца токена. Клиент использует временную подсказку из `localStorage` (30 минут) либо запрашивает адрес. Если вход не выполнен, повторно менять пароль не требуется.

## 3. Форматы и обработка ошибок

Параметры списков зависят от endpoint. Общие: page (от 0), size (1–100), sortBy, direction (`asc` / `desc`); поиск `q` либо `search` и фильтры по конкретному ресурсу. Пример из CourseController:

```http
GET http://localhost:8080/api/courses?page=0&size=20&q=Java&sortBy=id&direction=asc
```

Дополнительно: categoryId, teacherId, status. Сервер проверяет разрешённые поля сортировки. PageResponse содержит `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.

Единый формат `ApiErrorResponse`:

```json
{
  "timestamp": "2026-11-25T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/courses",
  "code": "VALIDATION_FAILED",
  "requestId": "<id>"
}
```

Пример структуры; значения полей зависят от операции. `X-Request-Id` используется для корреляции логов; DTO валидируются через Bean Validation. Успешное создание может возвращать 201 и Location; некоторые операции используют 204. Повторное архивирование курса и удаление отсутствующей связи с тегом идемпотентны. Версионность редактируемых сущностей защищает от потери изменений.

| HTTP | Значение |
|---|---|
| 200 / 201 / 204 | Успех / создание / ответ без тела |
| 400 | Некорректный JSON, параметры, валидация |
| 401 | Нет действительного токена или неверные учётные данные |
| 403 | Недостаточно прав |
| 404 | Ресурс или маршрут не найден (для разрешённого запроса) |
| 405 / 406 / 415 | Метод / формат ответа / Content-Type не поддерживается |
| 409 | Бизнес-конфликт, конфликт обновления или ограничение целостности |
| 429 | Превышен лимит запросов; интервал повторной попытки указан в Retry-After |
| 500 / 503 | Внутренняя ошибка / недоступность сервиса |

## 4. Перечень операций REST API

В таблицах приведены HTTP-методы и маршруты приложения. Пути в фигурных скобках — параметры. «Публичный» означает доступ без JWT; для остальных применяется политика `security/EndpointAuthorization.java` и проверки сервисов. DTO, поля запроса и точные статусы каждого метода приведены в Swagger и контроллерах. Источники: `src/main/java/org/courseflow/<module>/controller/`, `common/system/`.

### 4.1. Авторизация

| Метод | Путь | Доступ без JWT |
|---|---|---|
| POST | `/api/users/{id}/password-reset` | Нет |
| POST | `/api/auth/register` | Да |
| POST | `/api/auth/login` | Да |
| POST | `/api/auth/logout` | Нет |
| POST | `/api/auth/change-password` | Нет |
| POST | `/api/auth/forgot-password` | Да |
| POST | `/api/auth/reset-password` | Да |

### 4.2. Пользователи и профили

| Метод | Путь | Доступ без JWT |
|---|---|---|
| GET | `/api/users/me/profile` | Нет |
| PUT | `/api/users/me/profile` | Нет |
| GET | `/api/teachers` | Да |
| GET | `/api/teachers/{id}` | Да |
| GET | `/api/users/me` | Нет |
| GET | `/api/users/{id}` | Нет |
| GET | `/api/users` | Нет |
| POST | `/api/users` | Нет |
| PUT | `/api/users/{id}/roles` | Нет |
| PUT | `/api/users/{id}` | Нет |
| POST | `/api/users/{id}/deactivate` | Нет |
| POST | `/api/users/{id}/activate` | Нет |
| DELETE | `/api/users/{id}` | Нет |

### 4.3. Каталог

| Метод | Путь | Доступ без JWT |
|---|---|---|
| POST | `/api/categories` | Нет |
| PUT | `/api/categories/{id}` | Нет |
| DELETE | `/api/categories/{id}` | Нет |
| GET | `/api/categories/{id}` | Да |
| GET | `/api/categories` | Да |
| POST | `/api/courses` | Нет |
| PUT | `/api/courses/{id}` | Нет |
| GET | `/api/courses/{id}` | Да |
| GET | `/api/courses` | Да |
| POST | `/api/courses/{id}/archive` | Нет |
| DELETE | `/api/courses/{id}` | Нет |
| POST | `/api/courses/{courseId}/tags/{tagId}` | Нет |
| DELETE | `/api/courses/{courseId}/tags/{tagId}` | Нет |
| GET | `/api/courses/{courseId}/tags` | Да |
| PUT | `/api/courses/{courseId}/teacher` | Нет |
| GET | `/api/tags` | Да |
| GET | `/api/tags/{id}` | Да |
| POST | `/api/tags` | Нет |
| PUT | `/api/tags/{id}` | Нет |
| DELETE | `/api/tags/{id}` | Нет |

### 4.4. Учебные материалы

| Метод | Путь | Доступ без JWT |
|---|---|---|
| POST | `/api/courses/{courseId}/modules/{moduleId}/lessons` | Нет |
| GET | `/api/courses/{courseId}/modules/{moduleId}/lessons` | Нет |
| GET | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | Нет |
| PUT | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | Нет |
| PATCH | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/position` | Нет |
| DELETE | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | Нет |
| POST | `/api/lessons/{lessonId}/sessions` | Нет |
| GET | `/api/lessons/{lessonId}/sessions` | Нет |
| GET | `/api/lessons/{lessonId}/sessions/{sessionId}` | Нет |
| PUT | `/api/lessons/{lessonId}/sessions/{sessionId}` | Нет |
| PATCH | `/api/lessons/{lessonId}/sessions/{sessionId}/time` | Нет |
| DELETE | `/api/lessons/{lessonId}/sessions/{sessionId}` | Нет |
| POST | `/api/courses/{courseId}/modules` | Нет |
| GET | `/api/courses/{courseId}/modules` | Да |
| GET | `/api/courses/{courseId}/modules/{moduleId}` | Да |
| PUT | `/api/courses/{courseId}/modules/{moduleId}` | Нет |
| PATCH | `/api/courses/{courseId}/modules/{moduleId}/position` | Нет |
| DELETE | `/api/courses/{courseId}/modules/{moduleId}` | Нет |

### 4.5. Обучение и успеваемость

| Метод | Путь | Доступ без JWT |
|---|---|---|
| POST | `/api/lessons/{lessonId}/assignments` | Нет |
| GET | `/api/lessons/{lessonId}/assignments` | Нет |
| GET | `/api/lessons/{lessonId}/assignments/{assignmentId}` | Нет |
| PUT | `/api/lessons/{lessonId}/assignments/{assignmentId}` | Нет |
| PATCH | `/api/lessons/{lessonId}/assignments/{assignmentId}/deadline` | Нет |
| DELETE | `/api/lessons/{lessonId}/assignments/{assignmentId}` | Нет |
| POST | `/api/courses/{courseId}/enrollments` | Нет |
| GET | `/api/courses/{courseId}/enrollments/me` | Нет |
| DELETE | `/api/courses/{courseId}/enrollments/me` | Нет |
| DELETE | `/api/courses/{courseId}/enrollments/students/{studentId}` | Нет |
| GET | `/api/courses/{courseId}/enrollments` | Нет |
| GET | `/api/courses/{courseId}/gradebook` | Нет |
| GET | `/api/users/me/enrollments` | Нет |
| GET | `/api/users/me/progress` | Нет |
| GET | `/api/users/me/assignments` | Нет |
| GET | `/api/users/me/submissions` | Нет |
| GET | `/api/users/me/quiz-attempts` | Нет |
| POST | `/api/assignments/{assignmentId}/submissions` | Нет |
| GET | `/api/assignments/{assignmentId}/submissions/me` | Нет |
| GET | `/api/assignments/{assignmentId}/submissions` | Нет |
| PUT | `/api/assignments/{assignmentId}/submissions/{submissionId}/grade` | Нет |

### 4.6. Тестирование знаний

| Метод | Путь | Доступ без JWT |
|---|---|---|
| POST | `/api/quizzes/{quizId}/attempts` | Нет |
| GET | `/api/quizzes/{quizId}/attempts/{attemptId}` | Нет |
| POST | `/api/quizzes/{quizId}/attempts/{attemptId}/submit` | Нет |
| GET | `/api/quizzes/{quizId}/attempts/me` | Нет |
| GET | `/api/quizzes/{quizId}/attempts` | Нет |
| GET | `/api/quizzes/{quizId}/attempts/{attemptId}/result` | Нет |
| GET | `/api/quizzes/{quizId}/attempts/{attemptId}/manage` | Нет |
| POST | `/api/modules/{moduleId}/quiz` | Нет |
| GET | `/api/modules/{moduleId}/quiz` | Нет |
| GET | `/api/quizzes/{quizId}` | Нет |
| POST | `/api/quizzes/{quizId}/questions` | Нет |
| GET | `/api/quizzes/{quizId}/questions` | Нет |
| GET | `/api/quizzes/{quizId}/questions/manage` | Нет |
| PUT | `/api/quizzes/{quizId}` | Нет |
| DELETE | `/api/quizzes/{quizId}` | Нет |
| PUT | `/api/quizzes/{quizId}/questions/{questionId}` | Нет |
| DELETE | `/api/quizzes/{quizId}/questions/{questionId}` | Нет |

### 4.7. Отзывы

| Метод | Путь | Доступ без JWT |
|---|---|---|
| POST | `/api/courses/{courseId}/reviews` | Нет |
| PUT | `/api/courses/{courseId}/reviews/me` | Нет |
| DELETE | `/api/courses/{courseId}/reviews/me` | Нет |
| GET | `/api/courses/{courseId}/reviews/me` | Нет |
| GET | `/api/courses/{courseId}/reviews` | Да |
| GET | `/api/courses/{courseId}/reviews/rating` | Да |

### 4.8. Дашборды

| Метод | Путь | Доступ без JWT |
|---|---|---|
| GET | `/api/dashboard/admin` | Нет |
| GET | `/api/dashboard/student` | Нет |
| GET | `/api/dashboard/teacher` | Нет |

### 4.9. Системная информация

| Метод | Путь | Доступ без JWT |
|---|---|---|
| GET | `/api/system/info` | Нет |
| GET | `/api/system/status` | Нет |

`GET /api/system/info` доступен администратору и возвращает `SystemInfoResponse`: снимок приложения, JVM, PostgreSQL, пула соединений и настроек почты, локальный адрес backend и срок жизни JWT. Снимок кешируется на 60 секунд, недоступные показатели могут быть `null`. Пароли и ключи не возвращаются; доступность SMTP-соединения не проверяется.

Всего 107 операций прикладного API. `frontend/src/shared/contract.json` содержит локальную проекцию OpenAPI (схемы, тела запросов и параметры операций) для клиентской валидации. Полная спецификация со статусами ответов, описаниями и правилами авторизации доступна на `/v3/api-docs` в профиле `dev`.

## 5. Actuator и диагностика

| Метод | Путь | Доступ |
|---|---|---|
| GET | `/actuator/health` | Публичный, только статус |
| GET | `/actuator/health/liveness` | Публичный |
| GET | `/actuator/health/readiness` | Публичный, включает DB |
| GET | `/actuator/metrics` | ADMIN |
| GET | `/actuator/metrics/{name}` | ADMIN |

`/api/system/status` доступен аутентифицированным пользователям, `/api/system/info` — ADMIN. Метаданные системы не содержат паролей, ключей JWT и полной JDBC-строки. Nginx проксирует только разрешённые пути actuator.

## 6. Примеры и тестирование

- `requests.http` — набор ручных REST-запросов.
- `requests_auto.http` — автоматизированные последовательности HTTP Client с проверками.
- Swagger UI — схемы DTO, описание операций, интерактивные вызовы (dev).

Для сценариев с изменением данных используется отдельная тестовая среда. См. [TESTING.md](TESTING.md).

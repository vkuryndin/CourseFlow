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

Набор параметров зависит от операции API. Общие: page (от 0), size (1–100), sortBy, direction (`asc` / `desc`); поиск `q` либо `search` и фильтры по конкретному ресурсу. Пример из CourseController:

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
| 200 / 201 / 202 / 204 | Успех / создание / запрос принят / ответ без тела |
| 400 | Некорректный JSON, параметры, валидация |
| 401 | Нет действительного токена или неверные учётные данные |
| 403 | Недостаточно прав |
| 404 | Ресурс или маршрут не найден (для разрешённого запроса) |
| 405 / 406 / 415 | Метод / формат ответа / Content-Type не поддерживается |
| 409 | Бизнес-конфликт, конфликт обновления или ограничение целостности |
| 429 | Превышен лимит запросов; интервал повторной попытки указан в Retry-After |
| 500 / 503 | Внутренняя ошибка / недоступность сервиса |

## 4. Перечень операций REST API

В таблицах приведены маршруты, назначение, условия доступа и успешные HTTP-статусы. Пути в фигурных скобках — параметры. «Без JWT» означает публичный маршрут; «JWT и права на курс» — ADMIN, преподаватель данного курса или студент с активной записью. Дополнительно применяются ограничения состояния ресурсов, владения и принадлежности связанных объектов. Преподаватель создаёт курсы только для себя. Для JWT с обязательной сменой пароля действуют ограничения раздела 2. Схемы DTO и параметры доступны в Swagger UI; общие ошибки перечислены в разделе 3.

### 4.1. Авторизация

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| POST | `/api/users/{id}/password-reset` | Запросить отправку ссылки для сброса пароля пользователя. | ADMIN | 202 |
| POST | `/api/auth/register` | Регистрация. Создаёт аккаунт с ролью STUDENT. | Без JWT | 201 |
| POST | `/api/auth/login` | Вход. Возвращает JWT со сроком действия 1 час. | Без JWT | 200 |
| POST | `/api/auth/logout` | Выход. Отзывает текущую сессию. | JWT (любая роль) | 204 |
| POST | `/api/auth/change-password` | Изменить пароль | JWT (любая роль) | 204 |
| POST | `/api/auth/forgot-password` | Запросить сброс пароля. Ответ не раскрывает наличие аккаунта. | Без JWT | 202 |
| POST | `/api/auth/reset-password` | Установить новый пароль. По одноразовому токену. | Без JWT | 204 |

### 4.2. Пользователи и профили

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| GET | `/api/users/me/profile` | Мой профиль | JWT (любая роль) | 200 |
| PUT | `/api/users/me/profile` | Изменить профиль | JWT (любая роль) | 200 |
| GET | `/api/teachers` | Список преподавателей | Без JWT | 200 |
| GET | `/api/teachers/{id}` | Получить преподавателя | Без JWT | 200 |
| GET | `/api/users/me` | Мой аккаунт | JWT (любая роль) | 200 |
| GET | `/api/users/{id}` | Получить пользователя | ADMIN | 200 |
| GET | `/api/users` | Список пользователей | ADMIN | 200 |
| POST | `/api/users` | Создать пользователя | ADMIN | 201 |
| PUT | `/api/users/{id}/roles` | Изменить роли | ADMIN | 200 |
| PUT | `/api/users/{id}` | Изменить пользователя | ADMIN | 200 |
| POST | `/api/users/{id}/deactivate` | Деактивировать пользователя | ADMIN | 200 |
| POST | `/api/users/{id}/activate` | Активировать пользователя | ADMIN | 200 |
| DELETE | `/api/users/{id}` | Удалить пользователя. Учебная история сохраняется. | ADMIN | 204 |

### 4.3. Каталог

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| POST | `/api/categories` | Создать категорию | ADMIN | 201 |
| PUT | `/api/categories/{id}` | Изменить категорию | ADMIN | 200 |
| DELETE | `/api/categories/{id}` | Удалить категорию | ADMIN | 204 |
| GET | `/api/categories/{id}` | Получить категорию | Без JWT | 200 |
| GET | `/api/categories` | Список категорий | Без JWT | 200 |
| POST | `/api/courses` | Создать курс | ADMIN или TEACHER (свой курс) | 201 |
| PUT | `/api/courses/{id}` | Изменить курс | ADMIN или преподаватель курса | 200 |
| GET | `/api/courses/{id}` | Получить курс | Без JWT | 200 |
| GET | `/api/courses` | Каталог курсов | Без JWT | 200 |
| POST | `/api/courses/{id}/archive` | Архивировать курс | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/courses/{id}` | Удалить курс | ADMIN или преподаватель курса | 204 |
| POST | `/api/courses/{courseId}/tags/{tagId}` | Добавить тег к курсу. Повторный запрос безопасен. | ADMIN или преподаватель курса | 204 |
| DELETE | `/api/courses/{courseId}/tags/{tagId}` | Удалить тег курса | ADMIN или преподаватель курса | 204 |
| GET | `/api/courses/{courseId}/tags` | Теги курса | Без JWT | 200 |
| PUT | `/api/courses/{courseId}/teacher` | Назначить преподавателя | ADMIN | 200 |
| GET | `/api/tags` | Список тегов | Без JWT | 200 |
| GET | `/api/tags/{id}` | Получить тег | Без JWT | 200 |
| POST | `/api/tags` | Создать тег | ADMIN | 201 |
| PUT | `/api/tags/{id}` | Изменить тег | ADMIN | 200 |
| DELETE | `/api/tags/{id}` | Удалить тег | ADMIN | 204 |

### 4.4. Учебные материалы

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| POST | `/api/courses/{courseId}/modules/{moduleId}/lessons` | Создать урок | ADMIN или преподаватель курса | 201 |
| GET | `/api/courses/{courseId}/modules/{moduleId}/lessons` | Уроки модуля | JWT и права на курс | 200 |
| GET | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | Получить урок | JWT и права на курс | 200 |
| PUT | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | Изменить урок | ADMIN или преподаватель курса | 200 |
| PATCH | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/position` | Изменить порядок урока | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | Удалить урок | ADMIN или преподаватель курса | 204 |
| POST | `/api/lessons/{lessonId}/sessions` | Создать занятие | ADMIN или преподаватель курса | 201 |
| GET | `/api/lessons/{lessonId}/sessions` | Расписание урока | JWT и права на курс | 200 |
| GET | `/api/lessons/{lessonId}/sessions/{sessionId}` | Получить занятие | JWT и права на курс | 200 |
| PUT | `/api/lessons/{lessonId}/sessions/{sessionId}` | Изменить занятие | ADMIN или преподаватель курса | 200 |
| PATCH | `/api/lessons/{lessonId}/sessions/{sessionId}/time` | Перенести занятие | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/lessons/{lessonId}/sessions/{sessionId}` | Удалить занятие | ADMIN или преподаватель курса | 204 |
| POST | `/api/courses/{courseId}/modules` | Создать модуль | ADMIN или преподаватель курса | 201 |
| GET | `/api/courses/{courseId}/modules` | Модули курса | Без JWT | 200 |
| GET | `/api/courses/{courseId}/modules/{moduleId}` | Получить модуль | Без JWT | 200 |
| PUT | `/api/courses/{courseId}/modules/{moduleId}` | Изменить модуль | ADMIN или преподаватель курса | 200 |
| PATCH | `/api/courses/{courseId}/modules/{moduleId}/position` | Изменить порядок модуля | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/courses/{courseId}/modules/{moduleId}` | Удалить модуль | ADMIN или преподаватель курса | 204 |

### 4.5. Обучение и успеваемость

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| POST | `/api/lessons/{lessonId}/assignments` | Создать задание | ADMIN или преподаватель курса | 201 |
| GET | `/api/lessons/{lessonId}/assignments` | Задания урока | JWT и права на курс | 200 |
| GET | `/api/lessons/{lessonId}/assignments/{assignmentId}` | Получить задание | JWT и права на курс | 200 |
| PUT | `/api/lessons/{lessonId}/assignments/{assignmentId}` | Изменить задание | ADMIN или преподаватель курса | 200 |
| PATCH | `/api/lessons/{lessonId}/assignments/{assignmentId}/deadline` | Продлить срок сдачи. Новый срок позже текущего и в будущем. Оценки сохраняются. | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/lessons/{lessonId}/assignments/{assignmentId}` | Удалить задание | ADMIN или преподаватель курса | 204 |
| POST | `/api/courses/{courseId}/enrollments` | Записаться на курс. Повторный запрос возвращает или восстанавливает запись. | STUDENT | 200 / 201 |
| GET | `/api/courses/{courseId}/enrollments/me` | Моя запись на курс | STUDENT | 200 |
| DELETE | `/api/courses/{courseId}/enrollments/me` | Отписаться от курса | STUDENT | 204 |
| DELETE | `/api/courses/{courseId}/enrollments/students/{studentId}` | Отписать студента | ADMIN | 204 |
| GET | `/api/courses/{courseId}/enrollments` | Список студентов курса | ADMIN или преподаватель курса | 200 |
| GET | `/api/courses/{courseId}/gradebook` | Электронный журнал | ADMIN или преподаватель курса | 200 |
| GET | `/api/users/me/enrollments` | Мои курсы | STUDENT | 200 |
| GET | `/api/users/me/progress` | Мой прогресс | STUDENT | 200 |
| GET | `/api/users/me/assignments` | Мои задания | STUDENT | 200 |
| GET | `/api/users/me/submissions` | Мои ответы на задания | STUDENT | 200 |
| GET | `/api/users/me/quiz-attempts` | Мои попытки тестирования | STUDENT | 200 |
| POST | `/api/assignments/{assignmentId}/submissions` | Сдать задание | STUDENT | 201 |
| GET | `/api/assignments/{assignmentId}/submissions/me` | Мой ответ на задание | STUDENT | 200 |
| GET | `/api/assignments/{assignmentId}/submissions` | Ответы студентов | ADMIN или преподаватель курса | 200 |
| PUT | `/api/assignments/{assignmentId}/submissions/{submissionId}/grade` | Оценить ответ | ADMIN или преподаватель курса | 200 |

### 4.6. Тестирование знаний

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| POST | `/api/quizzes/{quizId}/attempts` | Начать попытку. Возвращает текущую активную попытку или создаёт новую. | STUDENT | 200 / 201 |
| GET | `/api/quizzes/{quizId}/attempts/{attemptId}` | Получить свою попытку | STUDENT | 200 |
| POST | `/api/quizzes/{quizId}/attempts/{attemptId}/submit` | Отправить ответы. Повторная отправка завершённой попытки не допускается. | STUDENT | 200 |
| GET | `/api/quizzes/{quizId}/attempts/me` | Мои попытки | STUDENT | 200 |
| GET | `/api/quizzes/{quizId}/attempts` | Результаты студентов | ADMIN или преподаватель курса | 200 |
| GET | `/api/quizzes/{quizId}/attempts/{attemptId}/result` | Мой результат | STUDENT | 200 |
| GET | `/api/quizzes/{quizId}/attempts/{attemptId}/manage` | Подробный результат студента | ADMIN или преподаватель курса | 200 |
| POST | `/api/modules/{moduleId}/quiz` | Создать тест | ADMIN или преподаватель курса | 201 |
| GET | `/api/modules/{moduleId}/quiz` | Тест модуля | JWT и права на курс | 200 |
| GET | `/api/quizzes/{quizId}` | Получить тест | JWT и права на курс | 200 |
| POST | `/api/quizzes/{quizId}/questions` | Добавить вопрос | ADMIN или преподаватель курса | 201 |
| GET | `/api/quizzes/{quizId}/questions` | Вопросы теста. Студенту доступно без правильных ответов во время активной попытки. | JWT и права на курс | 200 |
| GET | `/api/quizzes/{quizId}/questions/manage` | Вопросы с правильными ответами | ADMIN или преподаватель курса | 200 |
| PUT | `/api/quizzes/{quizId}` | Изменить тест | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/quizzes/{quizId}` | Удалить тест | ADMIN или преподаватель курса | 204 |
| PUT | `/api/quizzes/{quizId}/questions/{questionId}` | Изменить вопрос | ADMIN или преподаватель курса | 200 |
| DELETE | `/api/quizzes/{quizId}/questions/{questionId}` | Удалить вопрос | ADMIN или преподаватель курса | 204 |

### 4.7. Отзывы

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| POST | `/api/courses/{courseId}/reviews` | Оставить отзыв | STUDENT | 201 |
| PUT | `/api/courses/{courseId}/reviews/me` | Изменить свой отзыв | STUDENT | 200 |
| DELETE | `/api/courses/{courseId}/reviews/me` | Удалить свой отзыв | STUDENT | 204 |
| GET | `/api/courses/{courseId}/reviews/me` | Мой отзыв | STUDENT | 200 |
| GET | `/api/courses/{courseId}/reviews` | Отзывы о курсе | Без JWT | 200 |
| GET | `/api/courses/{courseId}/reviews/rating` | Рейтинг курса | Без JWT | 200 |

### 4.8. Дашборды

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| GET | `/api/dashboard/admin` | Панель администратора | ADMIN | 200 |
| GET | `/api/dashboard/student` | Панель студента | STUDENT | 200 |
| GET | `/api/dashboard/teacher` | Панель преподавателя | TEACHER | 200 |

### 4.9. Системная информация

| Метод | Путь | Назначение | Доступ | Успех (HTTP) |
|---|---|---|---|---|
| GET | `/api/system/info` | Диагностика системы. Кеш 60 секунд. Без паролей; SMTP-соединение не проверяется. | ADMIN | 200 |
| GET | `/api/system/status` | Состояние системы | JWT (любая роль) | 200 |

`GET /api/system/info` доступен администратору и возвращает `SystemInfoResponse`: снимок приложения, JVM, PostgreSQL, пула соединений и настроек почты, локальный адрес backend и срок жизни JWT. Снимок кешируется на 60 секунд, недоступные показатели могут быть `null`. Пароли и ключи не возвращаются; доступность SMTP-соединения не проверяется.

Всего 107 операций прикладного API. `frontend/src/shared/contract.json` содержит локальный клиентский контракт (схемы, тела запросов и параметры операций). `npm run check:contract` проверяет его внутреннюю согласованность, но не сравнивает с OpenAPI запущенного backend. Серверная спецификация доступна на `/v3/api-docs` в профиле `dev`. Бизнес-ограничения определяются реализацией сервисов.

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

### Основной учебный цикл

Примеры используют переменные `baseUrl`, `teacher_token`, `student_token` и идентификаторы объектов, полученные в предыдущих ответах. Категория, преподаватель, урок с заданием и тест с вопросами должны существовать; курс должен быть активным. Дата начала курса указывается с учётом текущей даты. Полные последовательности подготовки данных и негативных проверок находятся в `requests_auto.http`.

**Создание курса (преподаватель)** — HTTP 201. Из ответа используется `id` курса.

```http
POST {{baseUrl}}/api/courses
Authorization: Bearer {{teacher_token}}
Content-Type: application/json

{
  "title": "Основы Java",
  "description": "Практический курс",
  "categoryId": {{category_id}},
  "teacherId": {{teacher_id}},
  "duration": 56,
  "startDate": "{{course_start}}"
}
```

**Запись на курс (студент)** — HTTP 201 при создании, 200 при существующей или восстановленной записи.

```http
POST {{baseUrl}}/api/courses/{{course_id}}/enrollments
Authorization: Bearer {{student_token}}
```

**Сдача работы (студент)** — HTTP 201; из ответа используются `id` и `version` работы.

```http
POST {{baseUrl}}/api/assignments/{{assignment_id}}/submissions
Authorization: Bearer {{student_token}}
Content-Type: application/json

{"content": "Решение задания"}
```

**Оценивание (преподаватель курса)** — HTTP 200; `version` берётся из актуального ответа, балл не превышает максимальный балл задания.

```http
PUT {{baseUrl}}/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade
Authorization: Bearer {{teacher_token}}
Content-Type: application/json

{"version": {{submission_version}}, "score": 80, "feedback": "Работа проверена"}
```

**Начало попытки и отправка ответов (студент)** — HTTP 201 или 200 при начале, HTTP 200 при отправке. Идентификатор попытки берётся из первого ответа, идентификаторы вопросов и вариантов — из списка вопросов. Результат рассчитывается сервером.

```http
POST {{baseUrl}}/api/quizzes/{{quiz_id}}/attempts
Authorization: Bearer {{student_token}}

###
GET {{baseUrl}}/api/quizzes/{{quiz_id}}/questions
Authorization: Bearer {{student_token}}

###
POST {{baseUrl}}/api/quizzes/{{quiz_id}}/attempts/{{attempt_id}}/submit
Authorization: Bearer {{student_token}}
Content-Type: application/json

{"answers": [{"questionId": {{question_id}}, "optionIds": [{{option_id}}]}]}
```

Для сценариев с изменением данных используется отдельная тестовая среда. См. [TESTING.md](TESTING.md).

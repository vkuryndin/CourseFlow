# CourseFlow — тестирование и качество кода

[README](../README.md) · [Архитектура](ARCHITECTURE.md) · [Настройка среды](SETUP.md)

## 1. Стратегия тестирования

Тесты разделены по уровню и назначению. Модульные проверки изолируют бизнес-логику, интеграционные запускают контекст Spring и реальный PostgreSQL, архитектурные контролируют зависимости, нагрузочные моделируют одновременную работу пользователей. Клиентские тесты проверяют преобразование данных, интерфейс и навигацию.

| Уровень | Инструменты | Область проверки |
|---|---|---|
| Модульный | JUnit, Mockito | Сервисы, доменная модель, контроллеры, DTO, валидация, безопасность |
| Интеграционный | Spring Boot, PostgreSQL Testcontainers | REST API, CRUD, бизнес-операции, ограничения БД |
| ORM | JPA, Hibernate, PostgreSQL | Связи, каскады, ленивая загрузка, число запросов |
| Конкурентный | JUnit, PostgreSQL | Блокировки, изоляция, параллельные операции, откат |
| Архитектурный | ArchUnit, Node.js Test Runner | Границы модулей, зависимости слоёв, циклы и HTTP-транспорт |
| Нагрузочный | JUnit, параллельные HTTP-клиенты | Задержки, пропускная способность, устойчивость |
| Клиентский | Vitest | Формы, календарь, пагинация, контракты, электронный журнал |
| Браузерный (E2E) | Playwright | Пользовательские сценарии с управляемыми ответами API |
| Проверка API | IntelliJ HTTP Client, Swagger UI | Ручные запросы и последовательная REST-регрессия |

Testcontainers запускает PostgreSQL 18 в Docker и передаёт параметры JDBC в тестовый контекст. Профиль `test` создаёт схему заново. Поддерживаются локальный Docker, удалённый Docker через SSH и GitHub Actions. Порядок настройки описан в [SETUP.md](SETUP.md).

## 2. Основные серверные проверки

- CRUD и сохранение графов сущностей, каскады, FK, UNIQUE, CHECK, удаление зависимостей.
- Ленивые коллекции: воспроизведение `LazyInitializationException`, загрузка внутри транзакции, `JOIN FETCH`.
- Эффективность SQL-запросов при росте числа объектов, оптимизация выборок и электронного журнала.
- Оптимистические и пессимистические блокировки, взаимные блокировки и таймауты PostgreSQL, уровни изоляции READ COMMITTED, REPEATABLE READ и SERIALIZABLE.
- Аутентификация, права доступа к объектам, отзыв сессий, ограничение частоты запросов и защита от подмены proxy-заголовков.
- Валидация границ длины полей, числовых значений и времени; HTTP-статусы и контракты API.
- Конкурентная регистрация, запись студентов, сдача заданий, прохождение тестов, оценивание, очистка просроченных попыток.
- Восстановление после исчерпания пула соединений, недоступности БД и прерывания транзакции; сохранение данных при повторном запуске.

## 3. Нагрузочное тестирование

`PerformanceIT` содержит девять основных сценариев: чтение каталога и уроков, параллельную запись на курсы, сдачу заданий, прохождение тестов, электронный журнал и смешанные нагрузки. В них моделируются до 100 пользователей одновременно.

`AdvancedPerformanceIT` добавляет пять сценариев: ступенчатое увеличение нагрузки, насыщение без пауз между запросами, резкий всплеск и восстановление, продолжительную нагрузку и интенсивную аутентификацию с действующим ограничителем запросов.

Набор проверяет ожидаемые ответы, задержки, пропускную способность и сохранность данных. CSV-отчёты формируются в `build/reports/performance/`. Нагрузочные испытания проводятся в тестовой среде с PostgreSQL Testcontainers.

## 4. Контроль архитектуры и качества кода

| Инструмент | Назначение |
|---|---|
| ArchUnit 1.4.1 | Анализ зависимостей Java-классов и архитектурных ограничений |
| Spotless 8.10.4 | Форматирование Java по Google Java Format 1.28.0 |
| SpotBugs 6.5.13 | Статический анализ Java; исключения описаны в `config/spotbugs/exclude.xml` |
| Checkstyle 12.3.1 | Проверка соглашений и стиля, правила в `config/checkstyle/checkstyle.xml` |
| JaCoCo | Отчёты покрытия модульными и архитектурными тестами задачи `test` |
| ESLint 9.22.0 | Статический анализ TypeScript/React |
| CodeQL | Анализ Java/Kotlin и JavaScript/TypeScript на потенциальные уязвимости |

Checkstyle формирует отчёт о предупреждениях; текущая настройка Gradle допускает предупреждения (`maxWarnings = Int.MAX_VALUE`). SpotBugs проверяет основной код в CI. JaCoCo формирует HTML и XML без заданного минимального порога покрытия.

## 5. Запуск проверок

Backend из корня проекта (PowerShell):

```powershell
.\gradlew.bat clean test spotlessCheck spotbugsMain checkstyleMain bootJar
.\gradlew.bat integrationTest
.\gradlew.bat performanceTest
```

Для Linux/macOS используется `bash ./gradlew` с теми же задачами. `test` исключает теги `integration` и `performance`. Отдельные задачи Gradle запускают соответствующие наборы. Форматирование Java выполняется задачей `spotlessApply`.

Frontend:

```bash
cd frontend
npm ci
node scripts/sync-version.mjs --check
npm run lint
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

`npm test` запускает архитектурные тесты и Vitest. Playwright использует Vite на порту 5173 и управляемые ответы API. Для проверки собранного frontend предусмотрена конфигурация `playwright.production.config.ts`:

```bash
npx playwright test --config=playwright.production.config.ts
```

Дополнительные проверки frontend:

```bash
node --test scripts/structure.test.cjs scripts/bundle-report.test.cjs
node scripts/check-contract.cjs
# Linux с установленным nginx
python3 scripts/check-proxy.py
```

Для ручной проверки доступны `requests.http`, `requests_auto.http` и Swagger UI в профиле `dev`. HTTP Client использует последовательность запросов с тестовыми данными.

## 6. Сводка тестов и покрытия

| Набор | Число сценариев |
|---|---:|
| Модульные тесты backend | 598 |
| Архитектурные тесты backend | 15 |
| Интеграционные тесты (включая динамические и унаследованный сценарий) | 677 |
| Нагрузочные тесты | 14 |
| **Всего backend** | **1304** |
| Модульные тесты frontend | 33 |
| Архитектурные тесты frontend в `npm test` | 3 |
| Браузерные тесты Playwright | 29 |
| Дополнительные Node.js-тесты структуры и сборки | 7 |
| Проверки реального Nginx | 6 |

Число сценариев рассчитано по исходному коду: 850 методов `@Test`, 394 сценария полной REST-регрессии, 59 динамических проверок ограничителя запросов и один повтор унаследованного теста. Нагрузочные и архитектурные методы входят в число `@Test`. Браузерный набор включает 25 объявлений тестов, два из которых разворачиваются в 4 и 2 сценария.

Результат локальной регрессии: проверки backend и frontend пройдены; нагрузочный набор из 14 сценариев также успешно выполнен. Точное число исполнений и пропусков при конкретном запуске фиксируется в JUnit XML.

Покрытие по отчёту JaCoCo для задачи `test`:

| Метрика | Покрыто | Всего | Покрытие |
|---|---:|---:|---:|
| Строки | 4817 | 5346 | 90,10% |
| Ветви | 1078 | 1507 | 71,53% |
| Инструкции | 23574 | 26561 | 88,75% |
| Методы | 1110 | 1218 | 91,13% |
| Классы | 275 | 293 | 93,86% |

## 7. Отчёты и автоматизация

| Отчёт | Расположение |
|---|---|
| Модульные и архитектурные тесты | `build/reports/tests/test/index.html` |
| Интеграционные тесты | `build/reports/tests/integrationTest/index.html` |
| Нагрузочные тесты | `build/reports/tests/performanceTest/index.html`, `build/reports/performance/` |
| JaCoCo | `build/reports/jacoco/test/html/index.html`, `build/reports/jacoco/test/jacocoTestReport.xml` |
| Checkstyle и SpotBugs | `build/reports/checkstyle/`, `build/reports/spotbugs/` |
| JUnit XML | `build/test-results/<task>/` |

[CI](https://github.com/vkuryndin/CourseFlow/actions/workflows/ci.yml) автоматически выполняет серверные модульные, архитектурные и интеграционные тесты, клиентские тесты, линтеры и сборку. Отчёты backend доступны в артефакте `backend-reports`. [Нагрузочные тесты](https://github.com/vkuryndin/CourseFlow/actions/workflows/performance.yml) запускаются вручную и сохраняют `performance-reports`. [CodeQL](https://github.com/vkuryndin/CourseFlow/actions/workflows/codeql.yml) анализирует обе части приложения отдельным процессом. Playwright и дополнительные локальные проверки выполняются отдельно.

## 8. Каталог тестов

Ниже перечислены тесты по классам и функциональным областям. Ссылки ведут к исходному коду; имена методов и оригинальные названия динамических сценариев сохранены для поиска в IDE и отчётах. В таблицах приведены назначение и ключевые условия из тестового кода.

### 8.1. Аутентификация и учётные записи (78)

#### [SessionAccessTest](../src/test/java/org/courseflow/auth/api/SessionAccessTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [rolesAreSortedAndStartupClearsSessions](../src/test/java/org/courseflow/auth/api/SessionAccessTest.java#L19) | Roles are sorted and startup clears sessions. `service.activeRoles(id, 10L, NOW, true) = List.of("STUDENT", "TEACHER")`; Вызов `repo.deleteAllInBatch()` |

#### [PasswordResetExecutorConfigTest](../src/test/java/org/courseflow/auth/config/PasswordResetExecutorConfigTest.java)

Конфигурация компонентов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [executorHasBoundedQueueAndFixedWorkers](../src/test/java/org/courseflow/auth/config/PasswordResetExecutorConfigTest.java#L12) | Executor has bounded queue and fixed workers. `executor.getCorePoolSize() = 2`; `executor.getMaxPoolSize() = 2`; `executor.getThreadPoolExecutor().getQueue().remainingCapacity() = 64` |

#### [AdminPasswordResetControllerTest](../src/test/java/org/courseflow/auth/controller/AdminPasswordResetControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [resetPasswordDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/AdminPasswordResetControllerTest.java#L20) | Делегирование сервису и соответствие HTTP-контракту: `resetPassword`. `actual.getStatusCode().value() = 202`; `actual.getBody()` — null; Вызов `passwordResetService.sendResetLink()` |

#### [AuthControllerTest](../src/test/java/org/courseflow/auth/controller/AuthControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [registerDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/AuthControllerTest.java#L24) | Делегирование сервису и соответствие HTTP-контракту: `register`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; Вызов `operations.register()` |
| [loginDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/AuthControllerTest.java#L37) | Делегирование сервису и соответствие HTTP-контракту: `login`. `actual = expected`; Вызов `operations.login()` |
| [logoutDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/AuthControllerTest.java#L49) | Делегирование сервису и соответствие HTTP-контракту: `logout`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `logoutService.logout()` |

#### [AuthHttpOperationsTest](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [successfulLoginReleasesReservation](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java#L59) | Successful login releases reservation. `operations.login(login) = response`; Вызов `limiter.checkLogin()`; Вызов `auth.login()` |
| [wrongPasswordConsumesReservationAndUnexpectedFailureDoesNot](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java#L72) | Wrong password consumes reservation and unexpected failure does not. Исключение `BadCredentialsException`; Исключение `IllegalStateException`; Вызов `limiter.completeLogin()` |
| [rateLimitedLoginNeverCallsAuthenticationService](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java#L84) | Rate limited login never calls authentication service. Исключение `RateLimitExceededException`; Вызов `metrics.rateLimited()`; Отсутствие вызовов `auth` |
| [registrationAndPasswordChangesApplyLimitsBeforeBusinessOperations](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java#L92) | Registration and password changes apply limits before business operations. `operations.register(request) = response`; Вызов `limiter.checkRegistration()`; Вызов `auth.register()` |
| [forgotPasswordEnqueuesOnlyAcceptedRequests](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java#L111) | Forgot password enqueues only accepted requests. Вызов `dispatcher.enqueue()`; Вызов `metrics.resetRequested()`; Вызов `limiter.checkForgotPassword()` |
| [rejectedForgotPasswordIsCountedWithoutDispatching](../src/test/java/org/courseflow/auth/controller/AuthHttpOperationsTest.java#L121) | Rejected forgot password is counted without dispatching. Исключение `RateLimitExceededException`; Вызов `metrics.resetRequested()`; Вызов `metrics.rateLimited()` |

#### [PasswordControllerTest](../src/test/java/org/courseflow/auth/controller/PasswordControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [changePasswordDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/PasswordControllerTest.java#L18) | Делегирование сервису и соответствие HTTP-контракту: `changePassword`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `operations.changePassword()` |

#### [PasswordResetControllerTest](../src/test/java/org/courseflow/auth/controller/PasswordResetControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [forgotPasswordDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/PasswordResetControllerTest.java#L17) | Делегирование сервису и соответствие HTTP-контракту: `forgotPassword`. `actual.getStatusCode().value() = 202`; `actual.getBody()` — null; Вызов `operations.forgotPassword()` |
| [resetPasswordDelegatesAndReturnsContract](../src/test/java/org/courseflow/auth/controller/PasswordResetControllerTest.java#L28) | Делегирование сервису и соответствие HTTP-контракту: `resetPassword`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `operations.resetPassword()` |

#### [AuthDomainTest](../src/test/java/org/courseflow/auth/entity/AuthDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [passwordChangeFlagClearedOnReplacement](../src/test/java/org/courseflow/auth/entity/AuthDomainTest.java#L16) | Password change flag cleared on replacement. `account.getUser() = user`; `account.getCreatedAt() = NOW`; `account.getPasswordHash() = "new-hash"` |
| [invalidHashDoesNotClearFlag](../src/test/java/org/courseflow/auth/entity/AuthDomainTest.java#L31) | Invalid hash does not clear flag. Исключение `IllegalArgumentException`; `account.getPasswordHash() = "hash"`; `account.isPasswordChangeRequired()` — истина |
| [sessionExpiryAndRevocation](../src/test/java/org/courseflow/auth/entity/AuthDomainTest.java#L42) | Session expiry and revocation. `session.getUserAccount() = account`; `session.getCreatedAt() = NOW`; `session.getExpiresAt() = end` |
| [resetTokenIsSingleUseAndExpiresAtBoundary](../src/test/java/org/courseflow/auth/entity/AuthDomainTest.java#L59) | Reset token is single use and expires at boundary. `token.getUserAccount() = account`; `token.getTokenHash() = "…"`; `token.getExpiresAt() = end` |

#### [AccountCreatorTest](../src/test/java/org/courseflow/auth/service/AccountCreatorTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [rejectsExistingEmailBeforeHashing](../src/test/java/org/courseflow/auth/service/AccountCreatorTest.java#L42) | Отклонение операции: Existing Email Before Hashing. Исключение `BusinessConflictException`; Отсутствие вызовов `encoder` |
| [checksEmailAgainInsideTransaction](../src/test/java/org/courseflow/auth/service/AccountCreatorTest.java#L59) | Checks email again inside transaction. Исключение `BusinessConflictException`; Отсутствие вызовов `accounts` |
| [createsUserAndAccountWithRequiredPasswordFlag](../src/test/java/org/courseflow/auth/service/AccountCreatorTest.java#L77) | Creates user and account with required password flag. `actual = 10L`; `captor.getValue().getUser() = user`; `captor.getValue().getPasswordHash() = "hash"` |

#### [AdminPasswordResetServiceTest](../src/test/java/org/courseflow/auth/service/AdminPasswordResetServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [activeUserReceivesResetLink](../src/test/java/org/courseflow/auth/service/AdminPasswordResetServiceTest.java#L19) | Active user receives reset link. Вызов `resets.requestReset()` |
| [inactiveOrEmailLessUserRejected](../src/test/java/org/courseflow/auth/service/AdminPasswordResetServiceTest.java#L27) | Отклонение операции: inactive Or Email Less User. Исключение `BusinessConflictException`; Отсутствие вызовов `resets` |

#### [AuthRateLimiterTest](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [failedCredentialsAreRetainedAcrossIpAndCase](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L22) | Failed credentials are retained across ip and case. Исключение `RateLimitExceededException`; `ex.getRetryAfterSeconds() = 60`; `assertDoesNotThrow(() -> limiter.checkLogin("other", "user@example.test"))` |
| [successfulLoginRefundsOnlyOwnReservation](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L44) | Successful login refunds only own reservation. Исключение `RateLimitExceededException` |
| [duplicateCompletionCannotRefundAnotherReservation](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L56) | Duplicate completion cannot refund another reservation. Исключение `RateLimitExceededException` |
| [expiredReservationCannotRefundNewWindow](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L69) | Expired reservation cannot refund new window. Исключение `RateLimitExceededException` |
| [rejectsForeignReservation](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L82) | Отклонение операции: Foreign Reservation. Исключение `IllegalArgumentException` |
| [loginIpBudgetCountsSuccessfulLoginsToo](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L91) | Login ip budget counts successful logins too. Исключение `RateLimitExceededException` |
| [independentRegistrationAndRecoveryBudgets](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L100) | Independent registration and recovery budgets. Исключение `RateLimitExceededException` |
| [changePasswordLimitsUserAcrossIps](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L114) | Change password limits user across ips. Исключение `RateLimitExceededException`; `assertDoesNotThrow(() -> limiter.checkChangePassword("…", 2L))` |
| [changePasswordLimitsIpAcrossUsers](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L123) | Change password limits ip across users. Исключение `RateLimitExceededException` |
| [emailBudgetCannotBeEvictedByRotatingKeys](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L131) | Email budget cannot be evicted by rotating keys. Исключение `RateLimitExceededException`; `assertDoesNotThrow(() -> limiter.checkLogin("new-ip", "new@example.test"))` |
| [simultaneousReservationsNeverExceedTwelve](../src/test/java/org/courseflow/auth/service/AuthRateLimiterTest.java#L142) | Simultaneous reservations never exceed twelve. `admitted = 12` |

#### [AuthServiceTest](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [unknownEmailStillRunsPasswordCheck](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java#L57) | Unknown email still runs password check. Исключение `BadCredentialsException`; Вызов `encoder.matches()`; Отсутствие вызовов `sessions` |
| [wrongPasswordDoesNotCreateSession](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java#L68) | Wrong password does not create session. Исключение `BadCredentialsException`; Отсутствие вызовов `sessions` |
| [loginChecksHashAgainAfterAcquiringLock](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java#L80) | Login checks hash again after acquiring lock. Исключение `BadCredentialsException`; Отсутствие вызовов `sessions` |
| [inactiveUserCannotLogin](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java#L92) | Inactive user cannot login. Исключение `BadCredentialsException`; Отсутствие вызовов `sessions` |
| [loginCreatesOneHourSessionAndReturnsFlag](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java#L104) | Login creates one hour session and returns flag. `result.accessToken() = "jwt"`; `result.tokenType() = "Bearer"`; `result.userId() = 10L` |
| [longPasswordRejectedBeforeAccountLookup](../src/test/java/org/courseflow/auth/service/AuthServiceTest.java#L124) | Long password rejected before account lookup. Исключение `BadRequestException`; Отсутствие вызовов `accounts` |

#### [CredentialLifecycleTest](../src/test/java/org/courseflow/auth/service/CredentialLifecycleTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [revokesSessionsByUserId](../src/test/java/org/courseflow/auth/service/CredentialLifecycleTest.java#L29) | Revokes sessions by user id. Вызов `sessions.revokeActiveSessionsByUserId()` |
| [invalidatesTokensOnlyIfAccountExists](../src/test/java/org/courseflow/auth/service/CredentialLifecycleTest.java#L36) | Invalidates tokens only if account exists. Отсутствие вызовов `tokens`; Вызов `tokens.invalidateUnusedTokensForAccount()` |
| [locksAccountAndDeletesCredentialsInOrder](../src/test/java/org/courseflow/auth/service/CredentialLifecycleTest.java#L47) | Locks account and deletes credentials in order. Вызов `accounts.findByUserIdForUpdate()`; Вызов `sessions.deleteAllByAccountId()`; Вызов `tokens.deleteAllByAccountId()` |
| [missingAccountCannotBeDeleted](../src/test/java/org/courseflow/auth/service/CredentialLifecycleTest.java#L60) | Missing account cannot be deleted. Исключение `BusinessConflictException`; Отсутствие вызовов `sessions` |

#### [ExpiredCredentialsCleanupTest](../src/test/java/org/courseflow/auth/service/ExpiredCredentialsCleanupTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [cleanupUsesOneDayRetention](../src/test/java/org/courseflow/auth/service/ExpiredCredentialsCleanupTest.java#L16) | Cleanup uses one day retention. Вызов `sessions.deleteExpiredBefore()`; Вызов `tokens.deleteExpiredBefore()` |

#### [LogoutServiceTest](../src/test/java/org/courseflow/auth/service/LogoutServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [revokesExistingSession](../src/test/java/org/courseflow/auth/service/LogoutServiceTest.java#L24) | Revokes existing session. `a.isRevoked()` — истина |
| [rejectsMissingSession](../src/test/java/org/courseflow/auth/service/LogoutServiceTest.java#L35) | Отклонение операции: Missing Session. Исключение `InvalidSessionException` |

#### [PasswordResetDispatcherTest](../src/test/java/org/courseflow/auth/service/PasswordResetDispatcherTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [dispatchesWithoutSynchronousAccountLookupAndRestoresMdc](../src/test/java/org/courseflow/auth/service/PasswordResetDispatcherTest.java#L29) | Dispatches without synchronous account lookup and restores mdc. `MDC.get("requestId") = "request"`; `MDC.get("requestId") = "worker"`; Отсутствие вызовов `resets` |
| [queueRejectionReturnsRetryAfterAndDoesNotInvokeService](../src/test/java/org/courseflow/auth/service/PasswordResetDispatcherTest.java#L54) | Queue rejection returns retry after and does not invoke service. Исключение `RateLimitExceededException`; `assertThrows( RateLimitExceededException.class, () -> dispatcher.enqueue("student@example.test")) .getRetryAfterSeconds() = 60`; Вызов `metrics.resetRejected()` |
| [workerFailureRecordedWithoutEscapingRunnable](../src/test/java/org/courseflow/auth/service/PasswordResetDispatcherTest.java#L67) | Worker failure recorded without escaping runnable. Вызов `executor.execute()`; `assertDoesNotThrow(task.getValue()::run)`; Вызов `metrics.resetProcessingFailed()` |

#### [PasswordResetServiceCoverageTest](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [smtpFailureDoesNotExposeAccountExistence](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L84) | Smtp failure does not expose account existence. Вызов `metrics.resetMailFailed()`; Вызов `metrics.resetMailSent()` (`never()`) |
| [deactivatedAccountDoesNotReceiveToken](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L93) | Deactivated account does not receive token. Отсутствие вызовов `tokens` |
| [changedEmailDoesNotReceiveToken](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L101) | Changed email does not receive token. Отсутствие вызовов `tokens` |
| [developmentMailIncludesTestToken](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L109) | Development mail includes test token. `message.getValue().getText().contains("For testing through requests.http")` — истина; Вызов `mail.send()` |
| [productionSuppressesDebugTokenEvenWithDevProfile](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L118) | Production suppresses debug token even with dev profile. `message.getValue().getText().contains("For testing through requests.http")` — ложь; Вызов `mail.send()` |
| [samePasswordRejectedBeforeLock](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L127) | Same password rejected before lock. Исключение `BadRequestException`; Отсутствие вызовов `lifecycle`; Вызов `accounts.findByIdForUpdate()` (`never()`) |
| [expiredTokenRejectedAfterLock](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L138) | Expired token rejected after lock. Исключение `BadRequestException`; `account.getPasswordHash() = "old-hash"`; Отсутствие вызовов `lifecycle` |
| [changedPasswordHashRejectsStaleReset](../src/test/java/org/courseflow/auth/service/PasswordResetServiceCoverageTest.java#L150) | Changed password hash rejects stale reset. Исключение `BadRequestException`; `account.getPasswordHash() = "changed-hash"`; `token.getUsedAt()` — null |

#### [PasswordResetServiceTest](../src/test/java/org/courseflow/auth/service/PasswordResetServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [unknownEmailDoesNotSendMail](../src/test/java/org/courseflow/auth/service/PasswordResetServiceTest.java#L74) | Unknown email does not send mail. Вызов `accounts.findAccountIdByUserEmail()`; Отсутствие вызовов `tokens` |
| [cooldownPreventsSendingAnotherToken](../src/test/java/org/courseflow/auth/service/PasswordResetServiceTest.java#L83) | Cooldown prevents sending another token. Вызов `tokens.saveAndFlush()` (`never()`); Отсутствие вызовов `mail` |
| [storesHashedTokenAndSendsResetLink](../src/test/java/org/courseflow/auth/service/PasswordResetServiceTest.java#L94) | Stores hashed token and sends reset link. `token.getValue().getTokenHash().length() = 64`; `token.getValue().getExpiresAt() = NOW.plusSeconds(1800)`; `message.getValue().getTo()[0] = "student@example.test"` |
| [invalidTokenDoesNotHashNewPassword](../src/test/java/org/courseflow/auth/service/PasswordResetServiceTest.java#L111) | Invalid token does not hash new password. Исключение `BadRequestException`; Отсутствие вызовов `encoder` |
| [resetChangesPasswordAndRevokesSessions](../src/test/java/org/courseflow/auth/service/PasswordResetServiceTest.java#L119) | Reset changes password and revokes sessions. `account.getPasswordHash() = "new-hash"`; `token.getUsedAt() = NOW`; Вызов `tokens.invalidateUnusedTokensForAccount()` |

#### [PasswordServiceTest](../src/test/java/org/courseflow/auth/service/PasswordServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [passwordChangeUpdatesHashAndRevokesCredentials](../src/test/java/org/courseflow/auth/service/PasswordServiceTest.java#L51) | Password change updates hash and revokes credentials. `account.getPasswordHash() = "new-hash"`; `account.isPasswordChangeRequired()` — ложь; Вызов `tokens.invalidateUnusedTokensForAccount()` |
| [rejectsWrongOrSamePassword](../src/test/java/org/courseflow/auth/service/PasswordServiceTest.java#L63) | Отклонение операции: Wrong Or Same Password. Исключение `BadRequestException`; Отсутствие вызовов `tx` |
| [stalePasswordHashCannotOverwriteConcurrentReset](../src/test/java/org/courseflow/auth/service/PasswordServiceTest.java#L77) | Stale password hash cannot overwrite concurrent reset. Исключение `BadRequestException`; `account.getPasswordHash() = "other"`; Отсутствие вызовов `tokens` |
| [inactiveAccountRejected](../src/test/java/org/courseflow/auth/service/PasswordServiceTest.java#L90) | Отклонение операции: inactive Account. Исключение `AccessDeniedException`; Отсутствие вызовов `tokens` |
| [missingAccountRejected](../src/test/java/org/courseflow/auth/service/PasswordServiceTest.java#L102) | Отказ при отсутствии учётной записи. Исключение `ResourceNotFoundException` |

#### [RegistrationServiceTest](../src/test/java/org/courseflow/auth/service/RegistrationServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [registrationAlwaysCreatesStudentWithoutTemporaryPassword](../src/test/java/org/courseflow/auth/service/RegistrationServiceTest.java#L38) | Registration always creates student without temporary password. `response.id() = 10L`; `response.roles() = Set.of(UserRole.STUDENT)`; `response.email() = "student@example.test"` |

#### [SystemAdminBootstrapServiceTest](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [existingAdminSkipsCreation](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L41) | Existing admin skips creation. Вызов `jdbc.execute()`; Отсутствие вызовов `accounts` |
| [blankEmailRejected](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L49) | Отклонение операции: blank Email. Исключение `IllegalStateException`; Отсутствие вызовов `accounts` |
| [blankPasswordRejected](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L56) | Отклонение операции: blank Password. Исключение `IllegalStateException` |
| [shortPasswordRejected](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L62) | Отклонение операции: short Password. Исключение `IllegalStateException` |
| [longUtf8PasswordRejected](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L68) | Отклонение операции: long Utf 8 Password. Исключение `IllegalStateException` |
| [duplicateEmailRejected](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L74) | Отклонение дубликата: email. Исключение `IllegalStateException`; Отсутствие вызовов `accounts` |
| [createsAdminWithEncodedPassword](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L82) | Creates admin with encoded password. `captor.getValue().getUser() = admin`; `captor.getValue().getPasswordHash() = "…"`; `captor.getValue().isPasswordChangeRequired()` — ложь |
| [runnerDelegatesInitialization](../src/test/java/org/courseflow/auth/service/SystemAdminBootstrapServiceTest.java#L98) | Runner delegates initialization. Вызов `initializer.initialize()` |

### 8.2. Пользователи, профили и роли (69)

#### [UserDirectoryCoverageTest](../src/test/java/org/courseflow/identity/api/UserDirectoryCoverageTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [requireUserReturnsRepositoryEntity](../src/test/java/org/courseflow/identity/api/UserDirectoryCoverageTest.java#L19) | Require user returns repository entity. `directory.requireUser(10L) = user` |
| [requireStudentReturnsEntity](../src/test/java/org/courseflow/identity/api/UserDirectoryCoverageTest.java#L26) | Require student returns entity. `directory.requireStudentReference(10L) = user` |
| [teacherReferenceUsesWriteLock](../src/test/java/org/courseflow/identity/api/UserDirectoryCoverageTest.java#L33) | Teacher reference uses write lock. `directory.lockTeacherReference(20L) = user` |
| [emailExistenceDelegates](../src/test/java/org/courseflow/identity/api/UserDirectoryCoverageTest.java#L40) | Email existence delegates. `directory.emailInUse("…")` — истина; `directory.emailInUse("…")` — ложь |
| [systemAdminExistenceDelegates](../src/test/java/org/courseflow/identity/api/UserDirectoryCoverageTest.java#L47) | System admin existence delegates. `directory.systemAdminExists()` — истина; `directory.systemAdminExists()` — ложь |

#### [UserDirectoryTest](../src/test/java/org/courseflow/identity/api/UserDirectoryTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [missingUserAndTeacherRejected](../src/test/java/org/courseflow/identity/api/UserDirectoryTest.java#L24) | Отказ при отсутствии user and teacher. Исключение `ResourceNotFoundException` |
| [systemAdminCreationSetsRoleAndFlag](../src/test/java/org/courseflow/identity/api/UserDirectoryTest.java#L31) | System admin creation sets role and flag. `admin.getRoles() = Set.of(UserRole.ADMIN)`; `admin.getCreatedAt() = NOW`; `admin.isSystemAdmin()` — истина |
| [normalUserCreationRetainsRequestedRoles](../src/test/java/org/courseflow/identity/api/UserDirectoryTest.java#L41) | Normal user creation retains requested roles. `user.getRoles() = Set.of(UserRole.STUDENT)`; `user.isSystemAdmin()` — ложь |

#### [ProfileControllerTest](../src/test/java/org/courseflow/identity/controller/ProfileControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/ProfileControllerTest.java#L19) | Делегирование сервису и соответствие HTTP-контракту: `get`. `actual = expected`; Вызов `profileService.getMyProfile()` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/ProfileControllerTest.java#L32) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `profileService.updateMyProfile()` |

#### [PublicTeacherControllerTest](../src/test/java/org/courseflow/identity/controller/PublicTeacherControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [findAllDelegatesAndReturnsPage](../src/test/java/org/courseflow/identity/controller/PublicTeacherControllerTest.java#L19) | Find all delegates and returns page. `controller.findAll(0, 20, "Tea") = expected`; Вызов `userService.findPublicTeachers()` |
| [getDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/PublicTeacherControllerTest.java#L29) | Делегирование сервису и соответствие HTTP-контракту: `get`. `actual = expected`; Вызов `userService.getPublicTeacher()` |

#### [UserControllerTest](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getMeDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L29) | Делегирование сервису и соответствие HTTP-контракту: `getMe`. `actual = expected`; Вызов `userService.getById()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L42) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `userService.getById()` |
| [findAllDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L54) | Делегирование сервису и соответствие HTTP-контракту: `findAll`. `actual = expected`; Вызов `userService.findAll()` |
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L72) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/users/999"` |
| [updateRolesDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L89) | Делегирование сервису и соответствие HTTP-контракту: `updateRoles`. `actual = expected`; Вызов `adminUserService.updateRoles()` |
| [updateDetailsDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L104) | Делегирование сервису и соответствие HTTP-контракту: `updateDetails`. `actual = expected`; Вызов `adminUserService.updateDetails()` |
| [deactivateDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L119) | Делегирование сервису и соответствие HTTP-контракту: `deactivate`. `actual = expected`; Вызов `adminUserService.deactivate()` |
| [activateDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L133) | Делегирование сервису и соответствие HTTP-контракту: `activate`. `actual = expected`; Вызов `adminUserService.activate()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/identity/controller/UserControllerTest.java#L147) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `adminUserService.deleteUser()` |

#### [IdentityDomainTest](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [newUserIsActiveAndRolesAreDefensiveCopy](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L17) | New user is active and roles are defensive copy. Исключение `UnsupportedOperationException`; `user.getStatus() = UserStatus.ACTIVE`; `user.getRoles() = Set.of(UserRole.STUDENT)` |
| [deactivateAndReactivate](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L29) | Deactivate and reactivate. `user.getStatus() = UserStatus.INACTIVE`; `user.getStatus() = UserStatus.ACTIVE` |
| [activeUserCannotBeDeleted](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L39) | Active user cannot be deleted. Исключение `IllegalStateException`; `user.getStatus() = UserStatus.ACTIVE` |
| [deletionAnonymizesAndPreventsFurtherChanges](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L47) | Deletion anonymizes and prevents further changes. Исключение `IllegalStateException`; `user.getStatus() = UserStatus.DELETED`; `user.getDeletedAt() = NOW` |
| [systemAdminMustHaveAdminRole](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L64) | System admin must have admin role. Исключение `IllegalStateException`; `user.isSystemAdmin()` — истина |
| [rolesMustNotBeEmptyOrNull](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L77) | Roles must not be empty or null. Исключение `IllegalArgumentException` |
| [updateDetailsAndMultipleRoles](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L88) | Update details and multiple roles. `user.getDisplayName() = "New"`; `user.getEmail() = "new@example.test"`; `user.getRoles() = Set.of(UserRole.STUDENT, UserRole.TEACHER)` |
| [profileUpdateAndAnonymize](../src/test/java/org/courseflow/identity/entity/IdentityDomainTest.java#L99) | Profile update and anonymize. `profile.getUser() = user`; `profile.getBio() = "Bio"`; `profile.getAvatarUrl() = "https://example.test/avatar"` |

#### [AdminUserServiceCoverageTest](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [systemAdminCannotLoseAdminRole](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L55) | System admin cannot lose admin role. Исключение `BusinessConflictException` |
| [systemAdminCannotDeactivate](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L63) | System admin cannot deactivate. Исключение `BusinessConflictException` |
| [systemAdminCannotDelete](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L69) | System admin cannot delete. Исключение `BusinessConflictException` |
| [deletedUserCannotBeModifiedOrActivated](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L75) | Deleted user cannot be modified or activated. Исключение `BusinessConflictException` |
| [missingBootstrapAdminPreventsMutation](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L90) | Missing bootstrap admin prevents mutation. Исключение `IllegalStateException`; Отсутствие вызовов `credentials` |
| [missingTargetUserRejected](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L96) | Отказ при отсутствии target user. Исключение `ResourceNotFoundException` |
| [nameOnlyUpdateDoesNotRevokeSessions](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L102) | Name only update does not revoke sessions. `user.getDisplayName() = "New Name"`; Вызов `credentials.revokeSessions()` (`never()`); Вызов `credentials.invalidateResetTokens()` (`never()`) |
| [adminCanLoseRoleWhenAnotherActiveAdminExists](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L111) | Admin can lose role when another active admin exists. `user.getRoles() = Set.of(UserRole.STUDENT)`; Вызов `credentials.revokeSessions()` |
| [createDefaultsToPasswordChange](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L121) | Create defaults to password change. Вызов `creator.create()` |
| [createAllowsExplicitPasswordChangeOptOut](../src/test/java/org/courseflow/identity/service/AdminUserServiceCoverageTest.java#L137) | Create allows explicit password change opt out. Вызов `creator.create()` |

#### [AdminUserServiceTest](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [cannotRemoveStudentRoleWithActiveEnrollment](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L52) | Cannot remove student role with active enrollment. Исключение `BusinessConflictException`; `user.getRoles() = Set.of(UserRole.STUDENT)`; Вызов `credentials.revokeSessions()` (`never()`) |
| [cannotRemoveTeacherRoleWithAssignedCourse](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L64) | Cannot remove teacher role with assigned course. Исключение `BusinessConflictException` |
| [roleChangeRevokesSessionsButNoOpDoesNot](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L75) | Role change revokes sessions but no op does not. `user.getRoles().contains(UserRole.TEACHER)` — истина; Вызов `credentials.revokeSessions()` (`never()`); Вызов `credentials.revokeSessions()` |
| [emailChangeInvalidatesTokensAndSessions](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L87) | Email change invalidates tokens and sessions. `user.getDisplayName() = "New"`; `user.getEmail() = "new@example.test"`; Вызов `credentials.invalidateResetTokens()` |
| [duplicateEmailDoesNotModifyUser](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L98) | Duplicate email does not modify user. Исключение `BusinessConflictException`; `user.getEmail() = "student@example.test"` |
| [activeCoursePreventsTeacherDeactivation](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L109) | Active course prevents teacher deactivation. Исключение `BusinessConflictException`; `user.getStatus() = UserStatus.ACTIVE` |
| [deactivateAndActivate](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L118) | Deactivate and activate. `user.getStatus() = UserStatus.INACTIVE`; `user.getStatus() = UserStatus.ACTIVE`; Вызов `credentials.revokeSessions()` |
| [cannotDisableLastAdmin](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L129) | Cannot disable last admin. Исключение `BusinessConflictException` |
| [deleteRequiresInactiveUserAndNoEducationalAssignments](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L141) | Delete requires inactive user and no educational assignments. Исключение `BusinessConflictException`; Вызов `credentials.deleteCredentials()` (`never()`) |
| [deletionAnonymizesHistoryAndIsIdempotent](../src/test/java/org/courseflow/identity/service/AdminUserServiceTest.java#L154) | Deletion anonymizes history and is idempotent. `user.getStatus() = UserStatus.DELETED`; `user.getId() = 10L`; `p.getBio()` — null |

#### [ProfileServiceTest](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [missingUserRejected](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java#L28) | Отказ при отсутствии пользователя. Исключение `ResourceNotFoundException` |
| [missingProfileReturnsEmptyResponse](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java#L36) | Missing profile returns empty response. `response.bio()` — null; `response.avatarUrl()` — null |
| [existingProfileReturned](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java#L46) | Existing profile returned. `service.getMyProfile(10L).bio() = "Bio"` |
| [createProfileNormalizesFields](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java#L57) | Create profile normalizes fields. `r.bio() = "Bio"`; `r.avatarUrl() = "https://example.test"` |
| [updateExistingProfileClearsBlankValues](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java#L68) | Update existing profile clears blank values. `r.bio()` — null; `r.avatarUrl()` — null; Вызов `profiles.save()` |
| [inactiveUserAndInvalidUrlRejectedBeforeWrite](../src/test/java/org/courseflow/identity/service/ProfileServiceTest.java#L83) | Inactive user and invalid url rejected before write. Исключение `AccessDeniedException`; Исключение `BadRequestException`; Отсутствие вызовов `profiles` |

#### [StudentAccessTest](../src/test/java/org/courseflow/identity/service/StudentAccessTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [activeStudentAccepted](../src/test/java/org/courseflow/identity/service/StudentAccessTest.java#L21) | Active student accepted. `access.lockActive(10L) = s`; `access.lockForEnrollment(10L) = s` |
| [missingStudentRejected](../src/test/java/org/courseflow/identity/service/StudentAccessTest.java#L30) | Отказ при отсутствии студента. Исключение `ResourceNotFoundException` |
| [inactiveStudentRejected](../src/test/java/org/courseflow/identity/service/StudentAccessTest.java#L35) | Отклонение операции: inactive Student. Исключение `AccessDeniedException`; Исключение `BusinessConflictException` |
| [teacherWithoutStudentRoleRejected](../src/test/java/org/courseflow/identity/service/StudentAccessTest.java#L45) | Отклонение операции: teacher Without Student Role. Исключение `AccessDeniedException` |

#### [UserServiceTest](../src/test/java/org/courseflow/identity/service/UserServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getUserMapsRoles](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L54) | Get user maps roles. `service.getById(10L).roles() = java.util.Set.of(UserRole.STUDENT)` |
| [missingUserRejected](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L60) | Отказ при отсутствии пользователя. Исключение `ResourceNotFoundException` |
| [publicTeacherReturnsName](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L65) | Public teacher returns name. `service.getPublicTeacher(20L).displayName() = "Teacher"` |
| [studentIsNotPublicTeacher](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L71) | Student is not public teacher. Исключение `ResourceNotFoundException` |
| [inactiveTeacherIsNotPublic](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L77) | Inactive teacher is not public. Исключение `ResourceNotFoundException` |
| [publicDirectoryMapsOnlyPublicFieldsAndUsesStableSort](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L85) | Public directory maps only public fields and uses stable sort. `result.content().get(0).id() = 20L`; `result.content().get(0).displayName() = "Teacher"`; `page.getValue().getSort() = org.springframework.data.domain.Sort.by("displayName", "id")` |
| [publicDirectoryRejectsInvalidParameters](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L98) | Public directory rejects invalid parameters. Исключение `org.courseflow.common.exception.BadRequestException`; Отсутствие вызовов `repository` |
| [emptyPageDoesNotFetchRoles](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L112) | Empty page does not fetch roles. `service.findAll(0, 10, null, null, null, "id", "asc").content().isEmpty()` — истина; Вызов `repository.findWithRolesByIds()` (`never()`); Вызов `builder.and()` |
| [filtersAndRoleBatchFetch](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L124) | Filters and role batch fetch. `result.content().get(0).id() = 10L`; Вызов `repository.findWithRolesByIds()`; Вызов `query.distinct()` |
| [blankSearchDoesNotFilter](../src/test/java/org/courseflow/identity/service/UserServiceTest.java#L162) | Blank search does not filter. Отсутствие вызовов `root` |

### 8.3. Каталог курсов (79)

#### [CategoryControllerTest](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java#L22) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/categories/999"` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java#L37) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `categoryService.update()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java#L50) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `categoryService.delete()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java#L61) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `categoryService.getById()` |
| [findAllDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java#L73) | Делегирование сервису и соответствие HTTP-контракту: `findAll`. `actual = expected`; Вызов `categoryService.findAll()` |
| [legacySearchAliasIsUsedWhenQIsAbsent](../src/test/java/org/courseflow/catalog/controller/CategoryControllerTest.java#L91) | Legacy search alias is used when Q is absent. `controller.findAll(0, 20, null, "legacy", "name", "asc") = expected` |

#### [CourseControllerTest](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L35) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/courses/999"` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L55) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `courseService.update()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L73) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `courseQueryService.getById()` |
| [findAllDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L85) | Делегирование сервису и соответствие HTTP-контракту: `findAll`. `actual = expected`; Вызов `courseQueryService.findAll()` |
| [archiveDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L108) | Делегирование сервису и соответствие HTTP-контракту: `archive`. `actual = expected`; Вызов `courseService.archive()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L125) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `courseService.delete()` |
| [addTagDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L141) | Делегирование сервису и соответствие HTTP-контракту: `addTag`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `courseTagService.addTagToCourse()` |
| [removeTagDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L158) | Делегирование сервису и соответствие HTTP-контракту: `removeTag`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `courseTagService.removeTagFromCourse()` |
| [getCourseTagsDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L175) | Делегирование сервису и соответствие HTTP-контракту: `getCourseTags`. `actual = expected`; Вызов `courseTagService.findCourseTags()` |
| [reassignTeacherDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/CourseControllerTest.java#L189) | Делегирование сервису и соответствие HTTP-контракту: `reassignTeacher`. `actual = expected`; Вызов `courseService.reassignTeacher()` |

#### [TagControllerTest](../src/test/java/org/courseflow/catalog/controller/TagControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [findAllDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/TagControllerTest.java#L22) | Делегирование сервису и соответствие HTTP-контракту: `findAll`. `actual = expected`; Вызов `tagService.findAll()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/TagControllerTest.java#L38) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `tagService.getById()` |
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/TagControllerTest.java#L50) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/tags/999"` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/TagControllerTest.java#L65) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `tagService.update()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/catalog/controller/TagControllerTest.java#L78) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `tagService.delete()` |

#### [CatalogDomainTest](../src/test/java/org/courseflow/catalog/entity/CatalogDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [courseDefaultsAndUpdate](../src/test/java/org/courseflow/catalog/entity/CatalogDomainTest.java#L21) | Course defaults and update. `course.getStatus() = CourseStatus.ACTIVE`; `course.getTitle() = "New"`; `course.getDescription() = "Description"` |
| [archiveAndTeacherReassignment](../src/test/java/org/courseflow/catalog/entity/CatalogDomainTest.java#L35) | Archive and teacher reassignment. `course.getTeacher() = replacement`; `course.getStatus() = CourseStatus.ARCHIVED` |
| [moduleMaintainsBothSides](../src/test/java/org/courseflow/catalog/entity/CatalogDomainTest.java#L46) | Module maintains both sides. Исключение `UnsupportedOperationException`; `module.getCourse() = course`; `course.getModules() = List.of(module)` |
| [tagAssociationAndRemoval](../src/test/java/org/courseflow/catalog/entity/CatalogDomainTest.java#L56) | Tag association and removal. `course.getTags() = Set.of(tag)`; `course.getTags().isEmpty()` — истина |
| [categoryAndTagRename](../src/test/java/org/courseflow/catalog/entity/CatalogDomainTest.java#L68) | Category and tag rename. `category.getName() = "SQL"`; `category.getDescription() = "Database"`; `tag.getName() = "JPA"` |

#### [CategoryServiceCoverageTest](../src/test/java/org/courseflow/catalog/service/CategoryServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getExistingCategory](../src/test/java/org/courseflow/catalog/service/CategoryServiceCoverageTest.java#L23) | Получение существующего объекта: категории. `service.getById(30L).name() = "Java"` |
| [blankSearchListsAll](../src/test/java/org/courseflow/catalog/service/CategoryServiceCoverageTest.java#L29) | Blank search lists all. `service.findAll(0, 10, " ", "name", "asc").content().get(0).id() = 30L` |
| [searchTrimsInput](../src/test/java/org/courseflow/catalog/service/CategoryServiceCoverageTest.java#L36) | Search trims input. `service.findAll(0, 10, " JaVa ", "id", "asc").totalElements() = 1` |
| [nullSearchListsAll](../src/test/java/org/courseflow/catalog/service/CategoryServiceCoverageTest.java#L44) | Null search lists all. `service.findAll(0, 10, null, "id", "asc").content().isEmpty()` — истина |

#### [CategoryServiceTest](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createTrimsBothFields](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L28) | Create trims both fields. `r.name() = "Java"`; `r.description() = "Programming"` |
| [duplicateRejected](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L37) | Отклонение дубликата:. Исключение `BusinessConflictException`; Вызов `categories.saveAndFlush()` (`never()`) |
| [updateRequiresCurrentVersion](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L47) | Update requires current version. Исключение `OptimisticLockingFailureException`; `c.getName() = "Java"`; `service.update(30L, new CategoryCreateRequest(0L, " SQL ", " DB ")).name() = "SQL"` |
| [updateRejectsDuplicateName](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L60) | Update rejects duplicate name. Исключение `BusinessConflictException`; `c.getName() = "Java"` |
| [categoryWithCoursesCannotBeDeleted](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L72) | Category with courses cannot be deleted. Исключение `BusinessConflictException`; Вызов `categories.delete()` (`never()`) |
| [unusedCategoryDeleted](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L81) | Удаление неиспользуемого объекта: категории. Вызов `categories.delete()`; Вызов `categories.flush()` |
| [missingCategoryRejected](../src/test/java/org/courseflow/catalog/service/CategoryServiceTest.java#L91) | Отказ при отсутствии категории. Исключение `ResourceNotFoundException` |

#### [CourseGuardTest](../src/test/java/org/courseflow/catalog/service/CourseGuardTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [ownerAndAdminMayEditActiveCourse](../src/test/java/org/courseflow/catalog/service/CourseGuardTest.java#L23) | Owner and admin may edit active course. `guard.lockEditable(40L, 20L, false, "archived") = c`; `guard.lockEditable(40L, 99L, true, "archived") = c` |
| [nonOwnerDenied](../src/test/java/org/courseflow/catalog/service/CourseGuardTest.java#L32) | Non owner denied. Исключение `AccessDeniedException` |
| [archivedCourseCannotBeEditedEvenByAdmin](../src/test/java/org/courseflow/catalog/service/CourseGuardTest.java#L39) | Archived course cannot be edited even by admin. Исключение `BusinessConflictException` |
| [missingCourseRejectedByEveryLookup](../src/test/java/org/courseflow/catalog/service/CourseGuardTest.java#L49) | Missing course rejected by every lookup. Исключение `ResourceNotFoundException` |
| [correctRepositoryMethodsForReadAndSharedLock](../src/test/java/org/courseflow/catalog/service/CourseGuardTest.java#L57) | Correct repository methods for read and shared lock. `guard.find(40L) = c`; `guard.lockShared(40L) = c`; Вызов `repo.findByIdForUpdate()` (`never()`) |

#### [CourseParticipationTest](../src/test/java/org/courseflow/catalog/service/CourseParticipationTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [historicalCoursesCount](../src/test/java/org/courseflow/catalog/service/CourseParticipationTest.java#L16) | Historical courses count. `service.teachesAnyCourse(20L)` — истина; `service.teachesAnyCourse(10L)` — ложь |
| [activeCoursesOnly](../src/test/java/org/courseflow/catalog/service/CourseParticipationTest.java#L23) | Active courses only. `service.teachesActiveCourse(20L)` — истина; `service.teachesActiveCourse(10L)` — ложь |

#### [CourseQueryServiceTest](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getExistingCourse](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L51) | Получение существующего объекта: курса. `service.getById(40L).id() = 40L` |
| [missingCourseRejected](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L57) | Отказ при отсутствии курса. Исключение `ResourceNotFoundException` |
| [invalidCategoryFilter](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L62) | Invalid category filter. Исключение `BadRequestException`; Отсутствие вызовов `repository` |
| [invalidTeacherFilter](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L69) | Invalid teacher filter. Исключение `BadRequestException`; Отсутствие вызовов `repository` |
| [overlongSearchRejected](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L77) | Отклонение операции: overlong Search. Исключение `BadRequestException` |
| [invalidSortRejected](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L84) | Отклонение некорректного значения: сортировки. Исключение `BadRequestException` |
| [unfilteredPageUsesNoPredicates](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L91) | Unfiltered page uses no predicates. `result.content().get(0).id() = 40L`; Вызов `builder.and()`; Отсутствие вызовов `root` |
| [allFiltersBuildLiteralSearchAndForeignKeys](../src/test/java/org/courseflow/catalog/service/CourseQueryServiceTest.java#L104) | All filters build literal search and foreign keys. Вызов `builder.like()`; Вызов `builder.equal()` |

#### [CourseServiceCoverageTest](../src/test/java/org/courseflow/catalog/service/CourseServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [updatesDetailsWithoutChangingTeacher](../src/test/java/org/courseflow/catalog/service/CourseServiceCoverageTest.java#L55) | Updates details without changing teacher. `response.title() = "New"`; `response.description() = "Details"`; `response.duration() = 45` |
| [unknownCategoryCannotBeUsed](../src/test/java/org/courseflow/catalog/service/CourseServiceCoverageTest.java#L69) | Unknown category cannot be used. Исключение `ResourceNotFoundException`; Отсутствие вызовов `courses` |
| [adminCanCreateForTeacher](../src/test/java/org/courseflow/catalog/service/CourseServiceCoverageTest.java#L79) | Admin can create for teacher. `service .create(new CreateCourseRequest("Title", "Text", 30L, 20L, 30, date), 1L, true) .teacherId() = 20L` |
| [deleteTranslatesConstraintViolation](../src/test/java/org/courseflow/catalog/service/CourseServiceCoverageTest.java#L91) | Delete translates constraint violation. Исключение `BusinessConflictException` |

#### [CourseServiceTest](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [teacherCreatesOwnCourse](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L61) | Teacher creates own course. `response.title() = "New"`; `response.id() = 40L` |
| [teacherCannotCreateForOtherTeacher](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L73) | Teacher cannot create for other teacher. Исключение `AccessDeniedException`; Отсутствие вызовов `courses` |
| [inactiveTeacherOrStudentCannotBeAssigned](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L79) | Inactive teacher or student cannot be assigned. Исключение `BusinessConflictException`; Отсутствие вызовов `courses` |
| [regularUpdateCannotChangeTeacher](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L93) | Regular update cannot change teacher. Исключение `AccessDeniedException`; Исключение `BusinessConflictException` |
| [archiveBlocksUpcomingOrOngoingSessionsAndIsIdempotent](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L102) | Archive blocks upcoming or ongoing sessions and is idempotent. Исключение `BusinessConflictException`; `c.getStatus() = CourseStatus.ACTIVE`; `c.getStatus() = CourseStatus.ARCHIVED` |
| [deletionBlockedByEducationalHistory](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L115) | Deletion blocked by educational history. Исключение `BusinessConflictException`; Вызов `courses.delete()` (`never()`) |
| [emptyCourseDeleted](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L127) | Empty course deleted. Вызов `courses.delete()`; Вызов `courses.flush()` |
| [reassignRequiresAdminAndCompatibleSchedule](../src/test/java/org/courseflow/catalog/service/CourseServiceTest.java#L136) | Reassign requires admin and compatible schedule. Исключение `AccessDeniedException`; Исключение `BusinessConflictException`; `c.getTeacher() = replacement` |

#### [CourseTagServiceCoverageTest](../src/test/java/org/courseflow/catalog/service/CourseTagServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [courseTagPageMapsTag](../src/test/java/org/courseflow/catalog/service/CourseTagServiceCoverageTest.java#L25) | Course tag page maps tag. `service.findCourseTags(40L, 0, 10).content().get(0).name() = "JPA"` |
| [missingTagCannotBeAdded](../src/test/java/org/courseflow/catalog/service/CourseTagServiceCoverageTest.java#L34) | Missing tag cannot be added. Исключение `ResourceNotFoundException` |
| [missingTagCannotBeRemoved](../src/test/java/org/courseflow/catalog/service/CourseTagServiceCoverageTest.java#L40) | Missing tag cannot be removed. Исключение `ResourceNotFoundException` |

#### [CourseTagServiceTest](../src/test/java/org/courseflow/catalog/service/CourseTagServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [tagLinksAreIdempotent](../src/test/java/org/courseflow/catalog/service/CourseTagServiceTest.java#L28) | Tag links are idempotent. `c.getTags() = Set.of(tag)`; `c.getTags().isEmpty()` — истина |
| [unknownTagRejected](../src/test/java/org/courseflow/catalog/service/CourseTagServiceTest.java#L43) | Отклонение операции: unknown Tag. Исключение `ResourceNotFoundException` |
| [unknownCourseAndInvalidPageRejected](../src/test/java/org/courseflow/catalog/service/CourseTagServiceTest.java#L51) | Отклонение операции: unknown Course And Invalid Page. Исключение `BadRequestException`; Исключение `ResourceNotFoundException` |

#### [TagServiceTest](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createTrimsName](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L31) | Create trims name. `r.name() = "Java"`; `r.id() = 1L` |
| [blankAndDuplicateNamesRejected](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L40) | Отклонение операции: blank And Duplicate Names. Исключение `BadRequestException`; Исключение `BusinessConflictException`; Вызов `repo.saveAndFlush()` (`never()`) |
| [concurrentDuplicateTranslated](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L49) | Concurrent duplicate translated. Исключение `BusinessConflictException` |
| [updateChecksVersionAndName](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L58) | Update checks version and name. Исключение `OptimisticLockingFailureException`; `t.getName() = "Old"`; `service.update(1L, new TagRequest(0L, " New ")).name() = "New"` |
| [deletionBlockedWhenUsed](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L71) | Deletion blocked when used. Исключение `BusinessConflictException`; Вызов `repo.delete()` (`never()`) |
| [unusedTagDeleted](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L81) | Удаление неиспользуемого объекта: тега. Вызов `repo.delete()`; Вызов `repo.flush()` |
| [missingTagRejected](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L91) | Отказ при отсутствии тега. Исключение `ResourceNotFoundException` |
| [paginationAndSortValidation](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L97) | Pagination and sort validation. Исключение `BadRequestException` |
| [mapsPageAndPreservesSort](../src/test/java/org/courseflow/catalog/service/TagServiceTest.java#L105) | Maps page and preserves sort. `result.content().get(0).name() = "Java"`; `result.totalElements() = 1` |

### 8.4. Учебные материалы и занятия (72)

#### [ContentStructureTest](../src/test/java/org/courseflow/content/api/ContentStructureTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [findsModuleAndLessonOrThrowsNotFound](../src/test/java/org/courseflow/content/api/ContentStructureTest.java#L23) | Finds module and lesson or throws not found. Исключение `ResourceNotFoundException`; `content.requireModule(50L) = m`; `content.requireLesson(60L) = l` |
| [checksParentMembershipAndChildExistence](../src/test/java/org/courseflow/content/api/ContentStructureTest.java#L35) | Checks parent membership and child existence. Исключение `ResourceNotFoundException`; `content.hasModules(40L)` — истина; `content.hasModules(41L)` — ложь |

#### [LessonControllerTest](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java#L29) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/courses/101/modules/102/lessons/999"` |
| [findByModuleDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java#L52) | Делегирование сервису и соответствие HTTP-контракту: `findByModule`. `actual = expected`; Вызов `lessonService.findByModule()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java#L74) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `lessonService.getById()` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java#L94) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `lessonService.update()` |
| [changePositionDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java#L115) | Делегирование сервису и соответствие HTTP-контракту: `changePosition`. `actual = expected`; Вызов `lessonService.changePosition()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonControllerTest.java#L137) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `lessonService.delete()` |

#### [LessonSessionControllerTest](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java#L27) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/lessons/101/sessions/999"` |
| [findByLessonDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java#L48) | Делегирование сервису и соответствие HTTP-контракту: `findByLesson`. `actual = expected`; Вызов `sessionService.findByLesson()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java#L69) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `sessionService.getById()` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java#L87) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `sessionService.update()` |
| [rescheduleDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java#L106) | Делегирование сервису и соответствие HTTP-контракту: `reschedule`. `actual = expected`; Вызов `sessionService.reschedule()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/LessonSessionControllerTest.java#L125) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `sessionService.delete()` |

#### [ModuleControllerTest](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java#L28) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/courses/101/modules/999"` |
| [findByCourseDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java#L49) | Делегирование сервису и соответствие HTTP-контракту: `findByCourse`. `actual = expected`; Вызов `moduleService.findByCourse()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java#L63) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `moduleService.getById()` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java#L76) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `moduleService.update()` |
| [changePositionDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java#L95) | Делегирование сервису и соответствие HTTP-контракту: `changePosition`. `actual = expected`; Вызов `moduleService.changePosition()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/content/controller/ModuleControllerTest.java#L114) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `moduleService.delete()` |

#### [ContentDomainTest](../src/test/java/org/courseflow/content/entity/ContentDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [moduleUpdateAndLessonAssociation](../src/test/java/org/courseflow/content/entity/ContentDomainTest.java#L16) | Module update and lesson association. `module.getTitle() = "New"`; `module.getDescription() = "Description"`; `module.getPosition() = 3` |
| [lessonUpdateAndPosition](../src/test/java/org/courseflow/content/entity/ContentDomainTest.java#L32) | Lesson update and position. `lesson.getTitle() = "New"`; `lesson.getContent() = "Text"`; `lesson.getVideoUrl() = "https://example.test/video"` |
| [sessionAssociationAndReschedule](../src/test/java/org/courseflow/content/entity/ContentDomainTest.java#L44) | Session association and reschedule. `session.getLesson() = lesson`; `lesson.getSessions() = List.of(session)`; `session.getStartsAt() = NOW.plusSeconds(120)` |

#### [ContentAccessServiceTest](../src/test/java/org/courseflow/content/service/ContentAccessServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [adminAndOwnerCanRead](../src/test/java/org/courseflow/content/service/ContentAccessServiceTest.java#L34) | Admin and owner can read. Отсутствие вызовов `enrollments` |
| [otherTeacherAndUnenrolledStudentDenied](../src/test/java/org/courseflow/content/service/ContentAccessServiceTest.java#L43) | Other teacher and unenrolled student denied. Исключение `AccessDeniedException` |
| [enrolledStudentReadsArchivedCourse](../src/test/java/org/courseflow/content/service/ContentAccessServiceTest.java#L53) | Enrolled student reads archived course |
| [lessonAccessResolvesParentCourse](../src/test/java/org/courseflow/content/service/ContentAccessServiceTest.java#L62) | Lesson access resolves parent course. Исключение `ResourceNotFoundException`; Вызов `guard.find()` |

#### [LessonScheduleChecksTest](../src/test/java/org/courseflow/content/service/LessonScheduleChecksTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [lessonSessionsExist](../src/test/java/org/courseflow/content/service/LessonScheduleChecksTest.java#L16) | Lesson sessions exist. `checks.hasSessionsForLesson(60L)` — истина; `checks.hasSessionsForLesson(61L)` — ложь |
| [unfinishedIncludesOngoing](../src/test/java/org/courseflow/content/service/LessonScheduleChecksTest.java#L23) | Unfinished includes ongoing. `checks.hasUnfinishedSessions(40L, NOW)` — истина; `checks.hasUnfinishedSessions(41L, NOW)` — ложь |
| [overlapCountConvertedToBoolean](../src/test/java/org/courseflow/content/service/LessonScheduleChecksTest.java#L30) | Overlap count converted to boolean. `checks.hasReassignmentOverlap(40L, 20L, NOW)` — истина; `checks.hasReassignmentOverlap(40L, 20L, NOW)` — ложь |

#### [LessonServiceCoverageTest](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [summaryPageUsesStableOrder](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L55) | Summary page uses stable order. `service.findByModule(40L, 50L, 0, 10, 10L, auth).content() = List.of(summary)`; Вызов `access.checkCourseAccess()` |
| [detailIncludesContent](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L66) | Detail includes content. `service.getById(40L, 50L, 60L, 10L, auth).content() = "Content"` |
| [missingLessonRejected](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L72) | Отказ при отсутствии урока. Исключение `ResourceNotFoundException` |
| [deletionBlockedBySessions](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L78) | Deletion blocked by sessions. Исключение `BusinessConflictException`; Отсутствие вызовов `assignments`; Вызов `lessons.delete()` (`never()`) |
| [deletionBlockedByAssignments](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L87) | Deletion blocked by assignments. Исключение `BusinessConflictException` |
| [deleteRemovesChildFromParent](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L94) | Delete removes child from parent. `module.getLessons().isEmpty()` — истина; Вызов `lessons.delete()`; Вызов `lessons.flush()` |
| [deleteTranslatesForeignKeyConflict](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L103) | Delete translates foreign key conflict. Исключение `BusinessConflictException` |
| [positionUnchangedDoesNotFlush](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L110) | Position unchanged does not flush. `service .changePosition(40L, 50L, 60L, new ChangeLessonPositionRequest(1, 0L), 20L, false) .position() = 1`; Вызов `lessons.flush()` (`never()`) |
| [positionShiftMovesNeighbour](../src/test/java/org/courseflow/content/service/LessonServiceCoverageTest.java#L121) | Position shift moves neighbour. `service .changePosition(40L, 50L, 60L, new ChangeLessonPositionRequest(2, 0L), 20L, false) .position() = 2`; `neighbour.getPosition() = 1`; Вызов `lessons.flush()` (`times(3)`) |

#### [LessonServiceTest](../src/test/java/org/courseflow/content/service/LessonServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createsLessonWithTrimmedTextAndUrl](../src/test/java/org/courseflow/content/service/LessonServiceTest.java#L50) | Creates lesson with trimmed text and url. `response.title() = "New"`; `response.content() = "Text"`; `response.videoUrl() = "https://example.test/video"` |
| [occupiedPositionAndInvalidVideoRejected](../src/test/java/org/courseflow/content/service/LessonServiceTest.java#L68) | Отклонение операции: occupied Position And Invalid Video. Исключение `BusinessConflictException`; Исключение `BadRequestException`; Вызов `lessons.saveAndFlush()` (`never()`) |
| [updatesLessonAndNoOpPosition](../src/test/java/org/courseflow/content/service/LessonServiceTest.java#L85) | Updates lesson and no op position. `r.title() = "New"`; `service .changePosition(40L, 50L, 60L, new ChangeLessonPositionRequest(1, 0L), 20L, false) .position() = 1`; `r.videoUrl()` — null |
| [wrongParentRejected](../src/test/java/org/courseflow/content/service/LessonServiceTest.java#L102) | Отклонение операции: wrong Parent. Исключение `ResourceNotFoundException` |

#### [LessonSessionServiceCoverageTest](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [findByLessonReturnsOrderedPage](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L61) | Find by lesson returns ordered page. `service.findByLesson(60L, 0, 10, 10L, auth).content().get(0).id() = 90L`; Вызов `access.checkLessonAccess()` |
| [getByIdMapsSession](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L71) | Get by id maps session. `service.getById(60L, 90L, 10L, auth).startsAt() = NOW.plusSeconds(60)` |
| [getMissingSessionRejected](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L77) | Отклонение операции: get Missing Session. Исключение `ResourceNotFoundException` |
| [updateChangesTimeAndFormat](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L83) | Update changes time and format. `result.format() = LessonSessionFormat.ONLINE`; `result.startsAt() = request.startsAt()`; `result.meetingUrl() = request.meetingUrl()` |
| [deleteRemovesSessionFromLesson](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L101) | Delete removes session from lesson. `lesson.getSessions().isEmpty()` — истина; Вызов `sessions.delete()`; Вызов `sessions.flush()` |
| [missingLessonCourseRejected](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L110) | Отказ при отсутствии lesson course. Исключение `ResourceNotFoundException`; Отсутствие вызовов `sessions` |
| [missingEditableSessionRejected](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L116) | Отказ при отсутствии editable session. Исключение `ResourceNotFoundException` |
| [cannotMoveToPast](../src/test/java/org/courseflow/content/service/LessonSessionServiceCoverageTest.java#L122) | Cannot move to past. Исключение `BadRequestException`; Вызов `sessions.flush()` (`never()`) |

#### [LessonSessionServiceTest](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [onlineSessionCreatedAndTeacherLocked](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java#L56) | Online session created and teacher locked. `r.startsAt() = start`; `r.meetingUrl() = "https://example.test/meeting"`; Вызов `users.lockTeacherReference()` |
| [invalidTimeIntervalOrPastStartRejected](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java#L74) | Отклонение некорректного значения: time interval or past start. Исключение `BadRequestException`; Вызов `sessions.saveAndFlush()` (`never()`) |
| [onlineRequiresUrlAndOfflineForbidsUrl](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java#L90) | Online requires url and offline forbids url. Исключение `BadRequestException`; Вызов `sessions.saveAndFlush()` (`never()`) |
| [teacherOverlapRejected](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java#L109) | Отклонение операции: teacher Overlap. Исключение `BusinessConflictException`; Вызов `sessions.saveAndFlush()` (`never()`) |
| [startedSessionCannotBeDeletedOrRescheduled](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java#L122) | Started session cannot be deleted or rescheduled. Исключение `BusinessConflictException`; Вызов `sessions.delete()` (`never()`) |
| [reschedulingFutureSessionPreservesFormat](../src/test/java/org/courseflow/content/service/LessonSessionServiceTest.java#L137) | Rescheduling future session preserves format. `response.startsAt() = end`; `response.format() = LessonSessionFormat.ONLINE`; Вызов `sessions.flush()` |

#### [ModuleServiceCoverageTest](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [findByCourseMapsModules](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java#L40) | Find by course maps modules. `service.findByCourse(40L, 0, 10).content().get(0).id() = 50L`; Вызов `guard.requireExists()` |
| [getByIdMapsModule](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java#L49) | Get by id maps module. `service.getById(40L, 50L).title() = "JPA"` |
| [cannotDeleteWithQuiz](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java#L55) | Cannot delete with quiz. Исключение `BusinessConflictException`; Вызов `repository.delete()` (`never()`) |
| [emptyModuleDeleted](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java#L63) | Empty module deleted. Вызов `repository.delete()`; Вызов `repository.flush()` |
| [deleteTranslatesForeignKeyConflict](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java#L71) | Delete translates foreign key conflict. Исключение `BusinessConflictException` |
| [moveToFreePosition](../src/test/java/org/courseflow/content/service/ModuleServiceCoverageTest.java#L78) | Move to free position. `service .changePosition(40L, 50L, new ChangeModulePositionRequest(3, 0L), 20L, false) .position() = 3`; Вызов `repository.flush()` |

#### [ModuleServiceTest](../src/test/java/org/courseflow/content/service/ModuleServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createsModuleInFreePosition](../src/test/java/org/courseflow/content/service/ModuleServiceTest.java#L43) | Creates module in free position. `r.title() = "New"`; `r.position() = 2` |
| [occupiedPositionRejected](../src/test/java/org/courseflow/content/service/ModuleServiceTest.java#L53) | Отклонение операции: occupied Position. Исключение `BusinessConflictException`; Вызов `repo.saveAndFlush()` (`never()`) |
| [updateAndNoOpMove](../src/test/java/org/courseflow/content/service/ModuleServiceTest.java#L64) | Update and no op move. `service.update(40L, 50L, new UpdateModuleRequest(" New ", null, 0L), 20L, false).title() = "New"`; `service .changePosition(40L, 50L, new ChangeModulePositionRequest(1, 0L), 20L, false) .position() = 1`; Вызов `repo.flush()` |
| [missingModuleAndDeletionDependencies](../src/test/java/org/courseflow/content/service/ModuleServiceTest.java#L79) | Missing module and deletion dependencies. Исключение `ResourceNotFoundException`; Исключение `BusinessConflictException`; Вызов `repo.delete()` |

#### [PositionMoverTest](../src/test/java/org/courseflow/content/service/PositionMoverTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [movesDownWithoutTransientDuplicates](../src/test/java/org/courseflow/content/service/PositionMoverTest.java#L39) | Moves down without transient duplicates. `items.stream().map((i) -> i.position).toList() = List.of(3, 1, 2)` |
| [movesUpWithoutTransientDuplicates](../src/test/java/org/courseflow/content/service/PositionMoverTest.java#L50) | Moves up without transient duplicates. `items.stream().map((i) -> i.position).toList() = List.of(2, 3, 1)` |
| [noOpDoesNotFlush](../src/test/java/org/courseflow/content/service/PositionMoverTest.java#L61) | No op does not flush. Отсутствие вызовов `flush` |
| [movesToFreePosition](../src/test/java/org/courseflow/content/service/PositionMoverTest.java#L70) | Moves to free position. `a.position = 5`; Вызов `flush.run()` |
| [rejectsPositionOverflow](../src/test/java/org/courseflow/content/service/PositionMoverTest.java#L80) | Отклонение операции: Position Overflow. Исключение `BusinessConflictException`; `a.position = 1`; Отсутствие вызовов `flush` |

### 8.5. Запись на курс, задания, оценки и прогресс (109)

#### [AssignmentAccessTest](../src/test/java/org/courseflow/learning/api/AssignmentAccessTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [checksExistenceThroughOwningRepository](../src/test/java/org/courseflow/learning/api/AssignmentAccessTest.java#L13) | Checks existence through owning repository. `access.hasAssignments(10L)` — истина; `access.hasAssignments(11L)` — ложь; Вызов `repository.existsByLesson_Id()` |

#### [AssignmentControllerTest](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java#L27) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/lessons/101/assignments/999"` |
| [findByLessonDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java#L48) | Делегирование сервису и соответствие HTTP-контракту: `findByLesson`. `actual = expected`; Вызов `assignmentService.findByLesson()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java#L74) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `assignmentService.getById()` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java#L93) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `assignmentService.update()` |
| [extendDeadlineDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java#L112) | Делегирование сервису и соответствие HTTP-контракту: `extendDeadline`. `actual = expected`; Вызов `assignmentService.extendDeadline()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/AssignmentControllerTest.java#L132) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `assignmentService.delete()` |

#### [EnrollmentControllerTest](../src/test/java/org/courseflow/learning/controller/EnrollmentControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getMyEnrollmentDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/EnrollmentControllerTest.java#L27) | Делегирование сервису и соответствие HTTP-контракту: `getMyEnrollment`. `actual = expected`; Вызов `enrollmentService.getMyEnrollment()` |
| [withdrawDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/EnrollmentControllerTest.java#L41) | Делегирование сервису и соответствие HTTP-контракту: `withdraw`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `enrollmentService.withdraw()` |
| [withdrawStudentDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/EnrollmentControllerTest.java#L54) | Делегирование сервису и соответствие HTTP-контракту: `withdrawStudent`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `enrollmentService.withdraw()` |
| [getCourseEnrollmentsDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/EnrollmentControllerTest.java#L68) | Делегирование сервису и соответствие HTTP-контракту: `getCourseEnrollments`. `actual = expected`; Вызов `enrollmentService.getCourseEnrollments()` |
| [enrollDistinguishesCreationAndIdempotentRequest](../src/test/java/org/courseflow/learning/controller/EnrollmentControllerTest.java#L96) | Enroll distinguishes creation and idempotent request. `created.getStatusCode().value() = 201`; `created.getHeaders().getLocation().toString() = "/api/courses/1/enrollments/me"`; `created.getBody() = enrollment` |

#### [GradebookControllerTest](../src/test/java/org/courseflow/learning/controller/GradebookControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getGradebookDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/GradebookControllerTest.java#L25) | Делегирование сервису и соответствие HTTP-контракту: `getGradebook`. `actual = expected`; Вызов `gradebookService.getGradebook()` |

#### [MyEnrollmentControllerTest](../src/test/java/org/courseflow/learning/controller/MyEnrollmentControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getMyEnrollmentsDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/MyEnrollmentControllerTest.java#L21) | Делегирование сервису и соответствие HTTP-контракту: `getMyEnrollments`. `actual = expected`; Вызов `enrollmentService.getMyEnrollments()` |

#### [ProgressControllerTest](../src/test/java/org/courseflow/learning/controller/ProgressControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getMyProgressDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/ProgressControllerTest.java#L21) | Делегирование сервису и соответствие HTTP-контракту: `getMyProgress`. `actual = expected`; Вызов `progressService.getMyProgress()` |
| [summaryDefaultAndExplicitDetailsOverride](../src/test/java/org/courseflow/learning/controller/ProgressControllerTest.java#L77) | Summary default and explicit details override. `controller.getMyProgress(null, null, 0, 20, "courseTitle", "asc", null, 0, 50, jwt) = expected`; `controller.getMyProgress(1L, null, 0, 20, "courseTitle", "asc", false, 0, 50, jwt) = expected` |

#### [StudentLearningControllerTest](../src/test/java/org/courseflow/learning/controller/StudentLearningControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getAssignmentsDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/StudentLearningControllerTest.java#L25) | Делегирование сервису и соответствие HTTP-контракту: `getAssignments`. `actual = expected`; Вызов `studentLearningService.getAssignments()` |
| [getSubmissionsDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/StudentLearningControllerTest.java#L44) | Делегирование сервису и соответствие HTTP-контракту: `getSubmissions`. `actual = expected`; Вызов `studentLearningService.getSubmissions()` |
| [getQuizAttemptsDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/StudentLearningControllerTest.java#L63) | Делегирование сервису и соответствие HTTP-контракту: `getQuizAttempts`. `actual = expected`; Вызов `studentLearningService.getQuizAttempts()` |

#### [SubmissionControllerTest](../src/test/java/org/courseflow/learning/controller/SubmissionControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [submitDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/SubmissionControllerTest.java#L27) | Делегирование сервису и соответствие HTTP-контракту: `submit`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/assignments/101/submissions/me"` |
| [getMySubmissionDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/SubmissionControllerTest.java#L46) | Делегирование сервису и соответствие HTTP-контракту: `getMySubmission`. `actual = expected`; Вызов `submissionService.getMySubmission()` |
| [findByAssignmentDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/SubmissionControllerTest.java#L60) | Делегирование сервису и соответствие HTTP-контракту: `findByAssignment`. `actual = expected`; Вызов `submissionService.findByAssignment()` |
| [gradeDelegatesAndReturnsContract](../src/test/java/org/courseflow/learning/controller/SubmissionControllerTest.java#L88) | Делегирование сервису и соответствие HTTP-контракту: `grade`. `actual = expected`; Вызов `submissionService.grade()` |

#### [LearningDomainTest](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [enrollmentLifecyclePreservesOriginalDate](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java#L18) | Enrollment lifecycle preserves original date. `e.getStudent() = user`; `e.getCourse() = course`; `e.getStatus() = EnrollmentStatus.ACTIVE` |
| [extendingDeadlinePreservesDefinitionAndGrade](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java#L37) | Extending deadline preserves definition and grade. `a.getDueAt() = extension`; `a.getTitle() = "Task"`; `a.getDescription() = "Implement"` |
| [rejectDeadlineShorteningOrEqualityOrNull](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java#L52) | Reject deadline shortening or equality or null. Исключение `IllegalArgumentException`; `a.getDueAt() = due` |
| [assignmentDefinitionCanBeUpdated](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java#L63) | Assignment definition can be updated. `a.getTitle() = "New"`; `a.getDescription() = "Text"`; `a.getDueAt() = NOW` |
| [submissionInitialStateAndRegrading](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java#L74) | Submission initial state and regrading. `s.getAssignment() = a`; `s.getStudent() = student`; `s.getContent() = "Work"` |
| [invalidGradeDoesNotOverwriteExistingGrade](../src/test/java/org/courseflow/learning/entity/LearningDomainTest.java#L94) | Invalid grade does not overwrite existing grade. Исключение `IllegalArgumentException`; `s.getScore() = 50`; `s.getFeedback() = "Keep"` |

#### [EnrollmentRepositoryDefaultTest](../src/test/java/org/courseflow/learning/repository/EnrollmentRepositoryDefaultTest.java)

Параметры и результат запросов репозитория.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [activeEnrollmentDelegatesToStatusQuery](../src/test/java/org/courseflow/learning/repository/EnrollmentRepositoryDefaultTest.java#L16) | Active enrollment delegates to status query. `repository.hasActiveEnrollment(10L, 40L)` — истина; `repository.hasActiveEnrollment(11L, 40L)` — ложь |

#### [GradebookQueryRepositoryTest](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java)

Параметры и результат запросов репозитория.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [overdueFilterBindsSearchModuleAndDeadline](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L34) | Overdue filter binds search module and deadline. `page.rows() = List.of(row)`; Вызов `data.setParameter()`; Вызов `total.setParameter()` |
| [submittedFilterBindsSubmissionStatus](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L69) | Submitted filter binds submission status. Вызов `data.setParameter()`; Вызов `em.createQuery()` |
| [notSubmittedFilterDoesNotRequireDeadline](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L81) | Not submitted filter does not require deadline. Вызов `em.createQuery()` |
| [noReviewFilterAndUnsupportedSort](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L90) | No review filter and unsupported sort. Исключение `BadRequestException`; `repository .findStudents(40L, null, null, null, null, 0, 20, "studentId", "asc", NOW) .rows() .isEmpty()` — истина |
| [countsBothAssessmentTypesWithOptionalModule](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L104) | Counts both assessment types with optional module. `repository.countColumns(40L, 50L, AssessmentType.ASSIGNMENT) = 4L`; `repository.countColumns(40L, null, AssessmentType.QUIZ) = 4L`; Вызов `query.setParameter()` |
| [columnPageCrossesAssignmentQuizBoundary](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L114) | Column page crosses assignment quiz boundary. `rows.assignments() = List.of(assignment)`; `rows.quizzes() = List.of(quiz)`; Вызов `a.setFirstResult()` |
| [quizOnlyPageOffsetsPastAssignmentCount](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L131) | Quiz only page offsets past assignment count. `repository.loadColumns(40L, null, 5, 2, 3, 4).assignments().isEmpty()` — истина; Вызов `q.setFirstResult()`; Вызов `q.setMaxResults()` |
| [assignmentOnlyPageAndEmptyPageDoNotQueryQuizzes](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L139) | Assignment only page and empty page do not query quizzes. `repository.loadColumns(40L, null, 0, 1, 3, 4).quizzes().isEmpty()` — истина; `repository.loadColumns(40L, null, 8, 1, 3, 4).assignments().isEmpty()` — истина; Вызов `em.createQuery()` (`org.mockito.Mockito.never()`) |
| [emptyResultIdsAvoidDatabaseCalls](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L149) | Empty result ids avoid database calls. `repository.findAssignmentResults(List.of(), List.of(70L)).isEmpty()` — истина; `repository.findAssignmentResults(List.of(10L), List.of()).isEmpty()` — истина; `repository.findQuizResults(List.of(), List.of(80L), NOW).isEmpty()` — истина |
| [assessmentResultsBindOnlyRequestedStudentsAndColumns](../src/test/java/org/courseflow/learning/repository/GradebookQueryRepositoryTest.java#L158) | Assessment results bind only requested students and columns. `repository.findAssignmentResults(List.of(10L), List.of(70L)) = List.of(assignment)`; `repository.findQuizResults(List.of(10L), List.of(80L), NOW) = List.of(quiz)`; Вызов `a.setParameter()` |

#### [ProgressQueryRepositoryTest](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java)

Параметры и результат запросов репозитория.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [coursePageBindsAllOptionalFilters](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java#L31) | Course page binds all optional filters. `result.rows() = List.of(row)`; `result.total() = 11L`; Вызов `q.setParameter()` |
| [coursePageWithoutFiltersAndInvalidSort](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java#L59) | Course page without filters and invalid sort. Исключение `BadRequestException`; `repository.findCourses(10L, null, null, null, 0, 20, "courseId", "asc").rows().isEmpty()` — истина |
| [emptyCourseIdsDoNotQueryDatabase](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java#L70) | Empty course ids do not query database. `repository.aggregateAssignments(10L, List.of(), NOW).isEmpty()` — истина; `repository.aggregateQuizzes(10L, List.of()).isEmpty()` — истина; `repository.findAssignments(10L, List.of(), NOW, 0, 10, true).isEmpty()` — истина |
| [aggregatesBindCourseIdsAndStudent](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java#L79) | Aggregates bind course ids and student. `repository.aggregateAssignments(10L, List.of(40L), NOW) = List.of(a)`; `repository.aggregateQuizzes(10L, List.of(40L)) = List.of(q)`; Вызов `assignments.setParameter()` |
| [deadlinesOnlyFilterAndDetailPagination](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java#L93) | Deadlines only filter and detail pagination. `repository.findAssignments(10L, List.of(40L), NOW, 2, 5, true) = List.of(row)`; Вызов `q.setParameter()`; Вызов `q.setFirstResult()` |
| [allAssignmentsAndQuizDetails](../src/test/java/org/courseflow/learning/repository/ProgressQueryRepositoryTest.java#L104) | All assignments and quiz details. Вызов `a.setParameter()` (`org.mockito.Mockito.never()`); Вызов `q.setParameter()`; Вызов `q.setFirstResult()` |

#### [AssessmentStatusesTest](../src/test/java/org/courseflow/learning/service/AssessmentStatusesTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [assignmentStatusPrefersActualSubmission](../src/test/java/org/courseflow/learning/service/AssessmentStatusesTest.java#L11) | Assignment status prefers actual submission. `AssessmentStatuses.assignment(SubmissionStatus.GRADED, NOW, NOW) = "GRADED"`; `AssessmentStatuses.assignment(SubmissionStatus.SUBMITTED, NOW, NOW) = "SUBMITTED"` |
| [deadlineBoundary](../src/test/java/org/courseflow/learning/service/AssessmentStatusesTest.java#L17) | Deadline Boundary. `AssessmentStatuses.assignment(null, NOW.plusNanos(1), NOW) = "NOT_SUBMITTED"`; `AssessmentStatuses.assignment(null, NOW, NOW) = "OVERDUE"`; `AssessmentStatuses.assignment(null, NOW.minusNanos(1), NOW) = "OVERDUE"` |
| [quizStates](../src/test/java/org/courseflow/learning/service/AssessmentStatusesTest.java#L24) | Quiz States. `AssessmentStatuses.quiz(0, 1, 0) = "COMPLETED"`; `AssessmentStatuses.quiz(null, 0, 0) = "NOT_STARTED"`; `AssessmentStatuses.quiz(null, 2, 1) = "IN_PROGRESS"` |

#### [AssignmentServiceReadTest](../src/test/java/org/courseflow/learning/service/AssignmentServiceReadTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getAssignmentChecksLessonAccess](../src/test/java/org/courseflow/learning/service/AssignmentServiceReadTest.java#L42) | Get assignment checks lesson access. `service.getById(60L, 70L, 10L, auth).id() = 70L`; Вызов `access.checkLessonAccess()` |
| [assignmentMustBelongToLesson](../src/test/java/org/courseflow/learning/service/AssignmentServiceReadTest.java#L49) | Assignment must belong to lesson. Исключение `ResourceNotFoundException` |
| [missingAssignmentRejected](../src/test/java/org/courseflow/learning/service/AssignmentServiceReadTest.java#L55) | Отказ при отсутствии задания. Исключение `ResourceNotFoundException` |
| [listAssignmentsUsesRequestedSort](../src/test/java/org/courseflow/learning/service/AssignmentServiceReadTest.java#L60) | List assignments uses requested sort. `result.content().get(0).id() = 70L`; `result.totalElements() = 1L` |
| [unsupportedSortRejected](../src/test/java/org/courseflow/learning/service/AssignmentServiceReadTest.java#L70) | Отклонение операции: unsupported Sort. Исключение `BadRequestException`; Отсутствие вызовов `repository` |

#### [AssignmentServiceTest](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createTrimsAndPersists](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L66) | Create trims and persists. `r.title() = "New"`; `r.description() = "Text"`; `r.maxScore() = 50` |
| [createRejectsPastOrEqualDeadline](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L77) | Create rejects past or equal deadline. Исключение `BadRequestException`; Вызов `repo.saveAndFlush()` (`never()`) |
| [teacherCannotModifyOtherCourse](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L89) | Teacher cannot modify other course. Исключение `AccessDeniedException` |
| [archivedCourseCannotBeModified](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L104) | Archived course cannot be modified. Исключение `BusinessConflictException` |
| [extensionDoesNotQueryOrModifySubmissions](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L114) | Extension does not query or modify submissions. `r.dueAt() = newDue`; `r.title() = "Task"`; `r.maxScore() = 100` |
| [extensionRejectsStaleVersionAndShortening](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L129) | Extension rejects stale version and shortening. Исключение `OptimisticLockingFailureException`; Исключение `BadRequestException`; Вызов `repo.flush()` (`never()`) |
| [updateAndDeleteBlockedWhenSubmissionsExist](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L159) | Update and delete blocked when submissions exist. Исключение `BusinessConflictException`; Вызов `repo.delete()` (`never()`) |
| [updateWithoutSubmissions](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L171) | Update without submissions. `r.title() = "New"`; `r.maxScore() = 50`; Вызов `repo.flush()` |
| [deleteWithoutSubmissions](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L182) | Delete without submissions. Вызов `repo.delete()`; Вызов `repo.flush()` |
| [deleteTranslatesDatabaseConflict](../src/test/java/org/courseflow/learning/service/AssignmentServiceTest.java#L192) | Delete translates database conflict. Исключение `BusinessConflictException` |

#### [EnrollmentAccessTest](../src/test/java/org/courseflow/learning/service/EnrollmentAccessTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [activeEnrollmentRequired](../src/test/java/org/courseflow/learning/service/EnrollmentAccessTest.java#L19) | Active enrollment required. Исключение `AccessDeniedException`; `assertDoesNotThrow(() -> access.requireActive(1L, 2L, "not enrolled"))` |
| [historyAndActiveChecksAreDifferent](../src/test/java/org/courseflow/learning/service/EnrollmentAccessTest.java#L26) | History and active checks are different. `access.hasEnrollmentHistory(2L)` — истина; `access.hasActiveEnrollments(1L)` — истина; `access.hasActiveEnrollment(1L, 2L)` — ложь |

#### [EnrollmentServiceReadTest](../src/test/java/org/courseflow/learning/service/EnrollmentServiceReadTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getExistingEnrollment](../src/test/java/org/courseflow/learning/service/EnrollmentServiceReadTest.java#L33) | Получение существующего объекта: записи на курс. `service.getMyEnrollment(40L, 10L).id() = 90L` |
| [courseEnrollmentsAllStatuses](../src/test/java/org/courseflow/learning/service/EnrollmentServiceReadTest.java#L39) | Course enrollments all statuses. `service .getCourseEnrollments(40L, 20L, false, null, 0, 10, "id", "asc") .content() .get(0) .studentName() = "Student"` |
| [courseEnrollmentsFilterStatus](../src/test/java/org/courseflow/learning/service/EnrollmentServiceReadTest.java#L53) | Course enrollments filter status. `service .getCourseEnrollments(40L, 20L, false, EnrollmentStatus.ACTIVE, 0, 10, "id", "asc") .totalElements() = 1L` |
| [studentEnrollmentsAllStatuses](../src/test/java/org/courseflow/learning/service/EnrollmentServiceReadTest.java#L65) | Student enrollments all statuses. `service.getMyEnrollments(10L, null, 0, 10, "id", "asc").content().get(0).courseId() = 40L` |

#### [EnrollmentServiceTest](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createsEnrollmentWithCorrectLinksAndDate](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L50) | Creates enrollment with correct links and date. `result.enrollment().id() = 99L`; `result.enrollment().studentId() = 10L`; `result.enrollment().courseId() = 40L` |
| [repeatEnrollmentIsIdempotent](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L64) | Repeat enrollment is idempotent. `existing.getStatus() = EnrollmentStatus.ACTIVE`; `service.enroll(40L, 10L).created()` — ложь; `existing.getEndedAt()` — null |
| [archivedCourseRejectsEnrollmentBeforeStudentLookup](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L78) | Archived course rejects enrollment before student lookup. Исключение `BusinessConflictException`; Отсутствие вызовов `students` |
| [teacherCannotEnrollInOwnCourse](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L87) | Teacher cannot enroll in own course. Исключение `BusinessConflictException`; Отсутствие вызовов `repo` |
| [withdrawLocksCourseAndStudentBeforeChangingRecord](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L96) | Withdraw locks course and student before changing record. `existing.getStatus() = EnrollmentStatus.WITHDRAWN`; `existing.getEndedAt() = NOW`; Вызов `guard.lockShared()` |
| [missingEnrollmentRejected](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L111) | Отказ при отсутствии записи на курс. Исключение `ResourceNotFoundException` |
| [teacherCannotViewOtherCourseEnrollments](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L119) | Teacher cannot view other course enrollments. Исключение `AccessDeniedException`; Отсутствие вызовов `repo` |
| [myEnrollmentStatusFilterAndPagination](../src/test/java/org/courseflow/learning/service/EnrollmentServiceTest.java#L129) | My enrollment status filter and pagination. Исключение `BadRequestException`; `service.getMyEnrollments(10L, EnrollmentStatus.ACTIVE, 0, 20, "id", "asc").totalElements() = 1` |

#### [GradeAuditTest](../src/test/java/org/courseflow/learning/service/GradeAuditTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [auditRequiresActiveTransaction](../src/test/java/org/courseflow/learning/service/GradeAuditTest.java#L13) | Audit requires active transaction. Исключение `IllegalStateException` |
| [registersCallbackOnlyAndCanBeInvokedAfterCommit](../src/test/java/org/courseflow/learning/service/GradeAuditTest.java#L20) | Registers callback only and can be invoked after commit. `synchronizations.size() = 1`; `assertDoesNotThrow(() -> synchronizations.get(0).afterCommit())` |

#### [GradebookServiceTest](../src/test/java/org/courseflow/learning/service/GradebookServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [assemblesRowsWithIndependentAssessmentIdsAndMissingResults](../src/test/java/org/courseflow/learning/service/GradebookServiceTest.java#L41) | Assembles rows with independent assessment ids and missing results. `result.totalColumns() = 3L`; `result.totalColumnPages() = 1L`; `result.columns().get(2).maxScore() = 100` |
| [quizOnlyPageDoesNotCountAssignmentColumns](../src/test/java/org/courseflow/learning/service/GradebookServiceTest.java#L86) | Quiz only page does not count assignment columns. `result.totalColumnPages() = 3L`; `result.students().content().isEmpty()` — истина; Вызов `content.requireModuleInCourse()` |
| [rejectsForeignTeacherAndQuizReviewFilter](../src/test/java/org/courseflow/learning/service/GradebookServiceTest.java#L118) | Отклонение операции: Foreign Teacher And Quiz Review Filter. Исключение `AccessDeniedException`; Исключение `BadRequestException`; Отсутствие вызовов `queries` |

#### [ProgressServiceTest](../src/test/java/org/courseflow/learning/service/ProgressServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [summaryUsesCompleteCourseTotalsAndDoesNotLoadDetails](../src/test/java/org/courseflow/learning/service/ProgressServiceTest.java#L34) | Summary uses complete course totals and does not load details. `first.totalAssessments() = 5`; `first.completedAssessments() = 3`; `first.progressPercent() = 60` |
| [detailsPreserveTotalsAndMapAssessmentStatuses](../src/test/java/org/courseflow/learning/service/ProgressServiceTest.java#L69) | Details preserve totals and map assessment statuses. `row.totalAssessments() = 12`; `row.progressPercent() = 17`; `row.performancePercent() = 70` |
| [dashboardFiltersActiveCoursesAndHandlesNoResults](../src/test/java/org/courseflow/learning/service/ProgressServiceTest.java#L116) | Dashboard filters active courses and handles no results. `service.getDashboardProgress(10L, 5, NOW).content().isEmpty()` — истина; Вызов `queries.findCourses()` |
| [rejectsDetailsWithoutCourseAndInvalidPagination](../src/test/java/org/courseflow/learning/service/ProgressServiceTest.java#L127) | Отклонение операции: Details Without Course And Invalid Pagination. Исключение `BadRequestException`; Отсутствие вызовов `queries` |

#### [StudentLearningServiceTest](../src/test/java/org/courseflow/learning/service/StudentLearningServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [assignmentsApplyActiveEnrollmentCourseAndStatusFilters](../src/test/java/org/courseflow/learning/service/StudentLearningServiceTest.java#L32) | Assignments apply active enrollment course and status filters. `result.content() = List.of(row)`; `result.totalElements() = 21L`; Вызов `data.setFirstResult()` |
| [submissionHistoryIncludesWithdrawnCoursesAndBindsEnum](../src/test/java/org/courseflow/learning/service/StudentLearningServiceTest.java#L65) | Submission history includes withdrawn courses and binds enum. `service.getSubmissions(10L, null, SubmissionStatus.GRADED, 0, 20).content() = List.of(row)`; Вызов `data.setParameter()`; Вызов `em.createQuery()` |
| [attemptHistoryCalculatesExpiryAtOneInstant](../src/test/java/org/courseflow/learning/service/StudentLearningServiceTest.java#L81) | Attempt history calculates expiry at one instant. `service.getQuizAttempts(10L, 40L, QuizSubmissionStatus.EXPIRED, 0, 20).content().isEmpty()` — истина; Вызов `data.setParameter()`; Вызов `em.createQuery()` |
| [optionalFiltersCanBeOmitted](../src/test/java/org/courseflow/learning/service/StudentLearningServiceTest.java#L94) | Optional filters can be omitted. `service.getAssignments(10L, null, null, 0, 20).content().isEmpty()` — истина; `service.getSubmissions(10L, null, null, 0, 20).content().isEmpty()` — истина; `service.getQuizAttempts(10L, null, null, 0, 20).content().isEmpty()` — истина |
| [rejectsInvalidCourseIdBeforeQuerying](../src/test/java/org/courseflow/learning/service/StudentLearningServiceTest.java#L112) | Отклонение операции: Invalid Course Id Before Querying. Исключение `BadRequestException`; Отсутствие вызовов `em` |

#### [SubmissionServiceReadTest](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getOwnSubmission](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L58) | Get own submission. `service.getMySubmission(70L, 10L).content() = "Work"` |
| [missingAssignmentRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L66) | Отказ при отсутствии задания. Исключение `ResourceNotFoundException`; Отсутствие вызовов `submissions` |
| [missingSubmissionRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L72) | Отказ при отсутствии работы. Исключение `ResourceNotFoundException` |
| [listWithStatusAndScoreSort](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L78) | List with status and score sort. `result.content().get(0).score() = 90`; Вызов `data.setParameter()`; Вызов `count.setParameter()` |
| [listWithoutStatusUsesIdSort](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L93) | List without status uses id sort. `service.findByAssignment(70L, 20L, false, null, 0, 10, "id", "asc").totalElements() = 1L`; Вызов `data.setParameter()` (`never()`) |
| [listOtherSupportedSortFields](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L103) | List other supported sort fields. `service .findByAssignment(70L, 20L, false, null, 0, 10, "submittedAt", "asc") .content() .isEmpty()` — истина; `service .findByAssignment(70L, 20L, false, null, 0, 10, "status", "asc") .content() .isEmpty()` — истина |
| [unsupportedSortRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceReadTest.java#L120) | Отклонение операции: unsupported Sort. Исключение `BadRequestException`; Отсутствие вызовов `em` |

#### [SubmissionServiceTest](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [submitLocksBeforeLoadingAssignmentAndTrimsContent](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L69) | Submit locks before loading assignment and trims content. `r.content() = "Work"`; `r.status() = SubmissionStatus.SUBMITTED`; Вызов `assignments.findCourseIdByAssignmentId()` |
| [duplicateSubmissionRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L87) | Отклонение дубликата: работы. Исключение `BusinessConflictException`; Вызов `submissions.saveAndFlush()` (`never()`) |
| [deadlineBoundaryRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L96) | Отклонение операции: deadline Boundary. Исключение `BusinessConflictException`; Отсутствие вызовов `submissions` |
| [ownCourseAndArchivedCourseRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L105) | Отклонение операции: own Course And Archived Course. Исключение `BusinessConflictException`; Отсутствие вызовов `submissions` |
| [enrollmentAccessRequired](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L115) | Enrollment access required. Исключение `AccessDeniedException`; Отсутствие вызовов `submissions` |
| [databaseConflictTranslated](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L126) | Database conflict translated. Исключение `BusinessConflictException` |
| [missingAssignmentOrSubmissionRejected](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L135) | Отказ при отсутствии assignment or submission. Исключение `ResourceNotFoundException` |
| [initialGradeAndRegradeAuditPreviousValue](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L143) | Initial grade and regrade audit previous value. `work.getScore() = 80`; `work.getFeedback() = "Good"`; Вызов `audit.afterSuccessfulCommit()` |
| [rejectedGradeDoesNotEmitAudit](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L156) | Rejected grade does not emit audit. Исключение `BadRequestException`; Исключение `OptimisticLockingFailureException`; `work.getScore()` — null |
| [failedFlushDoesNotEmitAudit](../src/test/java/org/courseflow/learning/service/SubmissionServiceTest.java#L171) | Failed flush does not emit audit. Исключение `DataIntegrityViolationException`; Отсутствие вызовов `audit` |

### 8.6. Тестирование знаний (78)

#### [QuizAccessTest](../src/test/java/org/courseflow/assessment/api/QuizAccessTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [checksExistenceThroughOwningRepository](../src/test/java/org/courseflow/assessment/api/QuizAccessTest.java#L13) | Checks existence through owning repository. `access.hasQuiz(10L)` — истина; `access.hasQuiz(11L)` — ложь; Вызов `repository.existsByModule_Id()` |

#### [QuizAttemptControllerTest](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getMyAttemptDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L33) | Делегирование сервису и соответствие HTTP-контракту: `getMyAttempt`. `actual = expected`; Вызов `quizAttemptQueryService.getMyAttempt()` |
| [getMyAttemptsDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L48) | Делегирование сервису и соответствие HTTP-контракту: `getMyAttempts`. `actual = expected`; Вызов `quizAttemptQueryService.getMyAttempts()` |
| [getQuizResultsDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L70) | Делегирование сервису и соответствие HTTP-контракту: `getQuizResults`. `actual = expected`; Вызов `quizAttemptQueryService.getQuizResults()` |
| [getMyResultDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L99) | Делегирование сервису и соответствие HTTP-контракту: `getMyResult`. `actual = expected`; Вызов `quizAttemptQueryService.getMyResult()` |
| [getResultForManagementDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L114) | Делегирование сервису и соответствие HTTP-контракту: `getResultForManagement`. `actual = expected`; Вызов `quizAttemptQueryService.getResultForManagement()` |
| [startCreatesNewAttemptWithLocation](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L133) | Start creates new attempt with location. `result.getStatusCode().value() = 201`; `result.getHeaders().getLocation().toString() = "/api/quizzes/1/attempts/9"`; `result.getBody() = attempt` |
| [startReturnsExistingAttemptOrRejectsLimit](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L148) | Start returns existing attempt or rejects limit. Исключение `BusinessConflictException`; `result.getStatusCode().value() = 200`; `result.getBody() = attempt` |
| [submitReturnsResultOrRejectsExpiredAttempt](../src/test/java/org/courseflow/assessment/controller/QuizAttemptControllerTest.java#L164) | Submit returns result or rejects expired attempt. Исключение `BusinessConflictException`; `controller.submit(1L, 2L, request, jwt) = attempt` |

#### [QuizControllerTest](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L30) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/quizzes/999"` |
| [getByModuleDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L51) | Делегирование сервису и соответствие HTTP-контракту: `getByModule`. `actual = expected`; Вызов `quizService.getByModule()` |
| [getByIdDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L68) | Делегирование сервису и соответствие HTTP-контракту: `getById`. `actual = expected`; Вызов `quizService.getById()` |
| [createQuestionDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L85) | Делегирование сервису и соответствие HTTP-контракту: `createQuestion`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; Вызов `questionService.createQuestion()` |
| [getQuestionsDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L104) | Делегирование сервису и соответствие HTTP-контракту: `getQuestions`. `actual = expected`; Вызов `questionService.getQuestions()` |
| [getQuestionsForManagementDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L121) | Делегирование сервису и соответствие HTTP-контракту: `getQuestionsForManagement`. `actual = expected`; Вызов `questionService.getQuestionsForManagement()` |
| [updateQuizDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L139) | Делегирование сервису и соответствие HTTP-контракту: `updateQuiz`. `actual = expected`; Вызов `quizService.updateQuiz()` |
| [deleteQuizDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L157) | Делегирование сервису и соответствие HTTP-контракту: `deleteQuiz`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `quizService.deleteQuiz()` |
| [updateQuestionDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L173) | Делегирование сервису и соответствие HTTP-контракту: `updateQuestion`. `actual = expected`; Вызов `questionService.updateQuestion()` |
| [deleteQuestionDelegatesAndReturnsContract](../src/test/java/org/courseflow/assessment/controller/QuizControllerTest.java#L193) | Делегирование сервису и соответствие HTTP-контракту: `deleteQuestion`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `questionService.deleteQuestion()` |

#### [QuizAttemptResponseTest](../src/test/java/org/courseflow/assessment/dto/QuizAttemptResponseTest.java)

Формирование и содержимое DTO.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [readOnlyProjectionReflectsExpiryWithoutMutatingEntity](../src/test/java/org/courseflow/assessment/dto/QuizAttemptResponseTest.java#L16) | Read only projection reflects expiry without mutating entity. `before.status() = QuizSubmissionStatus.IN_PROGRESS`; `after.status() = QuizSubmissionStatus.EXPIRED`; `after.finishedAt() = attempt.getExpiresAt()` |
| [completedAttemptDoesNotBecomeExpired](../src/test/java/org/courseflow/assessment/dto/QuizAttemptResponseTest.java#L29) | Completed attempt does not become expired. `response.status() = QuizSubmissionStatus.SUBMITTED`; `response.score() = 80` |

#### [AssessmentDomainTest](../src/test/java/org/courseflow/assessment/entity/AssessmentDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [quizDefinitionAndQuestionAssociation](../src/test/java/org/courseflow/assessment/entity/AssessmentDomainTest.java#L20) | Quiz definition and question association. `quiz.getMaxAttempts() = 2`; `quiz.getTitle() = "New"`; `quiz.getTimeLimit() = 15` |
| [quizRejectsInvalidTimeLimitAndAttempts](../src/test/java/org/courseflow/assessment/entity/AssessmentDomainTest.java#L43) | Quiz rejects invalid time limit and attempts. Исключение `IllegalArgumentException`; `quiz.getTimeLimit() = 10` |
| [attemptValidUntilButNotAtExpiry](../src/test/java/org/courseflow/assessment/entity/AssessmentDomainTest.java#L55) | Attempt valid until but not at expiry. `a.getQuiz() = quiz`; `a.getStudent() = student`; `a.getTakenAt() = NOW` |
| [completedAttemptKeepsSelectionsAndCannotBeCompletedAgain](../src/test/java/org/courseflow/assessment/entity/AssessmentDomainTest.java#L75) | Completed attempt keeps selections and cannot be completed again. Исключение `IllegalStateException`; `selection.getQuizSubmission() = a`; `selection.getQuestion() = q` |
| [invalidAttemptNumberAndScoreRejected](../src/test/java/org/courseflow/assessment/entity/AssessmentDomainTest.java#L97) | Отклонение некорректного значения: attempt number and score. Исключение `IllegalArgumentException`; Исключение `IllegalStateException`; `a.getStatus() = QuizSubmissionStatus.IN_PROGRESS` |

#### [ExpiredQuizAttemptsCleanupTest](../src/test/java/org/courseflow/assessment/service/ExpiredQuizAttemptsCleanupTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [expiresOnlyBoundedBatchAtCurrentTime](../src/test/java/org/courseflow/assessment/service/ExpiredQuizAttemptsCleanupTest.java#L16) | Expires only bounded batch at current time. Вызов `jdbc.update()` |

#### [QuestionServiceCoverageTest](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createPersistsTrimmedQuestionAndOptions](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L92) | Create persists trimmed question and options. `result.id() = 90L`; `result.text() = "New question"`; `result.options().get(0).text() = "A"` |
| [multipleChoiceWithTwoCorrectAndOneWrong](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L105) | Multiple choice with two correct and one wrong. `result.options().size() = 3`; `result.options().stream().filter(o -> o.correct()).count() = 2L` |
| [multipleChoiceNeedsTwoCorrect](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L123) | Multiple choice needs two correct. Исключение `BadRequestException`; Отсутствие вызовов `em` |
| [multipleChoiceNeedsIncorrectOption](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L140) | Multiple choice needs incorrect option. Исключение `BadRequestException`; Отсутствие вызовов `em` |
| [duplicateTrimmedOptionsRejected](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L153) | Отклонение дубликата: trimmed options. Исключение `BadRequestException`; Отсутствие вызовов `em` |
| [missingQuizRejected](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L166) | Отказ при отсутствии теста. Исключение `ResourceNotFoundException` |
| [activeAttemptCanReadQuestions](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L173) | Active attempt can read questions. `service.getQuestions(80L, 10L, authentication("STUDENT")).get(0).text() = "Q"`; Вызов `attempts.existsByQuiz_IdAndStudent_IdAndStatusAndExpiresAtAfter()` |
| [teacherAndAdminDoNotNeedAttempt](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L188) | Teacher and admin do not need attempt. `service.getQuestions(80L, 20L, authentication("TEACHER")).isEmpty()` — истина; `service.getQuestions(80L, 99L, authentication("ADMIN")).isEmpty()` — истина; Отсутствие вызовов `attempts` |
| [managementShowsCorrectOptions](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L196) | Management shows correct options. `service.getQuestionsForManagement(80L, 20L, false).get(0).options().get(0).correct()` — истина; Вызов `quizzes.requireQuizManagementAccess()` |
| [updateRejectsOccupiedPosition](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L206) | Update rejects occupied position. Исключение `BusinessConflictException`; Отсутствие вызовов `em` |
| [updateClearsContextAndReloadsQuestion](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L217) | Update clears context and reloads question. `result.text() = "New question"`; `result.options().size() = 2`; Вызов `em.lock()` |
| [deleteRemovesOptionsBeforeQuestion](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L237) | Delete removes options before question. Вызов `em.createQuery()`; Вызов `options.executeUpdate()`; Вызов `question.executeUpdate()` |
| [missingQuestionCannotBeDeleted](../src/test/java/org/courseflow/assessment/service/QuestionServiceCoverageTest.java#L259) | Missing question cannot be deleted. Исключение `ResourceNotFoundException`; Отсутствие вызовов `em` |

#### [QuestionServiceTest](../src/test/java/org/courseflow/assessment/service/QuestionServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [cannotCreateQuestionAfterAttempts](../src/test/java/org/courseflow/assessment/service/QuestionServiceTest.java#L53) | Cannot create question after attempts. Исключение `BusinessConflictException`; Отсутствие вызовов `em` |
| [singleChoiceRequiresExactlyOneCorrectOption](../src/test/java/org/courseflow/assessment/service/QuestionServiceTest.java#L68) | Single choice requires exactly one correct option. Исключение `BadRequestException`; Отсутствие вызовов `em` |
| [duplicatePositionAndQuestionLimitRejected](../src/test/java/org/courseflow/assessment/service/QuestionServiceTest.java#L91) | Отклонение дубликата: position and question limit. Исключение `BusinessConflictException`; Отсутствие вызовов `em` |
| [studentCannotReadQuestionsWithoutActiveAttempt](../src/test/java/org/courseflow/assessment/service/QuestionServiceTest.java#L105) | Student cannot read questions without active attempt. Исключение `BusinessConflictException`; Отсутствие вызовов `questions` |
| [questionProjectionHidesCorrectnessAndSortsOptions](../src/test/java/org/courseflow/assessment/service/QuestionServiceTest.java#L116) | Question projection hides correctness and sorts options. `student.options().stream().map(QuestionResponse.OptionResponse::id).toList() = List.of(10L, 20L)`; `teacher.options().get(0).correct()` — истина; `teacher.options().get(1).correct()` — ложь |

#### [QuizAttemptQueryServiceTest](../src/test/java/org/courseflow/assessment/service/QuizAttemptQueryServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [expiredReadDoesNotMutateStoredAttempt](../src/test/java/org/courseflow/assessment/service/QuizAttemptQueryServiceTest.java#L46) | Expired read does not mutate stored attempt. `result.status() = QuizSubmissionStatus.EXPIRED`; `result.finishedAt() = NOW`; `attempt.getStatus() = QuizSubmissionStatus.IN_PROGRESS` |
| [missingQuizAndForeignAttemptAreNotFound](../src/test/java/org/courseflow/assessment/service/QuizAttemptQueryServiceTest.java#L61) | Missing quiz and foreign attempt are not found. Исключение `ResourceNotFoundException`; Отсутствие вызовов `em` |
| [historyBindsStudentStatusAndPagination](../src/test/java/org/courseflow/assessment/service/QuizAttemptQueryServiceTest.java#L69) | History binds student status and pagination. `result.content().get(0).id() = 90L`; Вызов `q.setParameter()`; Вызов `q.setMaxResults()` |
| [managementResultsRequireOwnershipOrAdmin](../src/test/java/org/courseflow/assessment/service/QuizAttemptQueryServiceTest.java#L88) | Management results require ownership or admin. Исключение `AccessDeniedException`; Исключение `BadRequestException`; Отсутствие вызовов `em` |
| [studentAndTeacherDetailsIncludeSelectedAnswers](../src/test/java/org/courseflow/assessment/service/QuizAttemptQueryServiceTest.java#L103) | Student and teacher details include selected answers. Исключение `AccessDeniedException`; `studentResult.attempt().score() = 80`; `studentResult.selectedAnswers() = List.of(answer)` |

#### [QuizAttemptServiceTest](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [startsNewAttempt](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L66) | Starts new attempt. `result.attempt().attemptNumber() = 1`; `result.attempt().expiresAt() = NOW.plusSeconds(600)` |
| [activeAttemptIsReused](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L78) | Active attempt is reused. `result.attempt().id() = 90L`; Вызов `attempts.saveAndFlush()` (`never()`) |
| [expiresPreviousAttemptAndHonorsLimit](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L90) | Expires previous attempt and honors limit. `previous.getStatus() = QuizSubmissionStatus.EXPIRED`; Вызов `attempts.saveAndFlush()` (`never()`) |
| [noQuestionsAndArchivedCourseRejectStart](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L102) | No questions and archived course reject start. Исключение `BusinessConflictException`; Отсутствие вызовов `attempts` |
| [submitsCorrectAnswersAndPersistsSelections](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L113) | Submits correct answers and persists selections. `result.score() = 100`; `result.status() = QuizSubmissionStatus.SUBMITTED`; `a.getSelections().size() = 1` |
| [expiredAttemptReturnsEmptyWithoutWritingSelections](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L134) | Expired attempt returns empty without writing selections. `a.getStatus() = QuizSubmissionStatus.EXPIRED`; `service.submit(80L, 90L, 10L, new SubmitQuizRequest(List.of())).isEmpty()` — истина; Отсутствие вызовов `em` |
| [duplicateSubmissionRejected](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L145) | Отклонение дубликата: работы. Исключение `BusinessConflictException`; Отсутствие вызовов `em` |
| [missingQuizOrForeignAttemptRejected](../src/test/java/org/courseflow/assessment/service/QuizAttemptServiceTest.java#L158) | Отказ при отсутствии quiz or foreign attempt. Исключение `ResourceNotFoundException` |

#### [QuizGraderTest](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [validatesAnswerAndScoresCorrect](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L30) | Validates answer and scores correct. `answers = Map.of(1L, Set.of(10L))`; `QuizGrader.score(List.of(q), answers) = 100` |
| [rejectsForeignQuestion](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L39) | Отклонение операции: Foreign Question. Исключение `BadRequestException` |
| [rejectsDuplicateQuestion](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L48) | Отклонение операции: Duplicate Question. Исключение `BadRequestException` |
| [rejectsDuplicateOptions](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L59) | Отклонение операции: Duplicate Options. Исключение `BadRequestException` |
| [singleChoiceRejectsMultipleOptions](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L68) | Single choice rejects multiple options. Исключение `BadRequestException` |
| [rejectsOptionFromOtherQuestion](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L77) | Отклонение операции: Option From Other Question. Исключение `BadRequestException` |
| [multipleChoiceRequiresExactSet](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L86) | Multiple choice requires exact set. `QuizGrader.score(List.of(q), Map.of(1L, Set.of(10L, 12L))) = 100`; `QuizGrader.score(List.of(q), Map.of(1L, Set.of(10L))) = 0`; `QuizGrader.score(List.of(q), Map.of(1L, Set.of(10L, 11L, 12L))) = 0` |
| [omittedAnswersAndFloorRounding](../src/test/java/org/courseflow/assessment/service/QuizGraderTest.java#L96) | Omitted answers and floor rounding. `QuizGrader.score(qs, Map.of(1L, Set.of(10L), 2L, Set.of(20L))) = 66`; `QuizGrader.score(qs, Map.of()) = 0`; `QuizGrader.percentage(0, 0) = 0` |

#### [QuizServiceCoverageTest](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getQuizById](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L48) | Get quiz by id. `service.getById(80L, 10L, authentication).id() = 80L`; Вызов `access.checkCourseAccess()` |
| [getQuizByModule](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L55) | Get quiz by module. `service.getByModule(50L, 10L, authentication).id() = 80L` |
| [missingQuizByModule](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L62) | Missing quiz by module. Исключение `ResourceNotFoundException` |
| [missingQuizById](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L69) | Missing quiz by id. Исключение `ResourceNotFoundException` |
| [managementReadDoesNotLockCourse](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L74) | Management read does not lock course. `service.requireQuizManagementAccess(80L, 20L, false, false) = quiz`; Вызов `guard.find()`; Вызов `guard.lock()` (`never()`) |
| [archivedQuizCannotBeModified](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L82) | Archived quiz cannot be modified. Исключение `BusinessConflictException`; Отсутствие вызовов `em` |
| [missingQuizCourseRejected](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L90) | Отказ при отсутствии quiz course. Исключение `ResourceNotFoundException` |
| [deleteOrdersBulkOperations](../src/test/java/org/courseflow/assessment/service/QuizServiceCoverageTest.java#L95) | Delete orders bulk operations. Вызов `em.createQuery()`; Вызов `options.executeUpdate()`; Вызов `questions.executeUpdate()` |

#### [QuizServiceTest](../src/test/java/org/courseflow/assessment/service/QuizServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createsQuizWithDefaultAttempts](../src/test/java/org/courseflow/assessment/service/QuizServiceTest.java#L49) | Creates quiz with default attempts. `r.title() = "New"`; `r.timeLimit() = 5`; `r.maxAttempts() = 2` |
| [rejectsDuplicateQuizAndNonOwner](../src/test/java/org/courseflow/assessment/service/QuizServiceTest.java#L60) | Отклонение операции: Duplicate Quiz And Non Owner. Исключение `AccessDeniedException`; Исключение `BusinessConflictException`; Вызов `quizzes.saveAndFlush()` (`never()`) |
| [quizImmutableAfterAttempt](../src/test/java/org/courseflow/assessment/service/QuizServiceTest.java#L74) | Quiz immutable after attempt. Исключение `BusinessConflictException` |
| [updateQuizBeforeAttempts](../src/test/java/org/courseflow/assessment/service/QuizServiceTest.java#L85) | Update quiz before attempts. `r.title() = "New"`; `r.timeLimit() = 15`; `r.maxAttempts() = 3` |

### 8.7. Отзывы и рейтинг (18)

#### [ReviewAccessTest](../src/test/java/org/courseflow/review/api/ReviewAccessTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [checksExistenceThroughOwningRepository](../src/test/java/org/courseflow/review/api/ReviewAccessTest.java#L13) | Checks existence through owning repository. `access.hasReviews(10L)` — истина; `access.hasReviews(11L)` — ложь; Вызов `repository.existsByCourse_Id()` |

#### [CourseReviewControllerTest](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createDelegatesAndReturnsContract](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java#L25) | Делегирование сервису и соответствие HTTP-контракту: `create`. `actual.getStatusCode().value() = 201`; `actual.getBody() = expected`; `actual.getHeaders().getLocation().toString() = "/api/courses/101/reviews/me"` |
| [updateDelegatesAndReturnsContract](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java#L42) | Делегирование сервису и соответствие HTTP-контракту: `update`. `actual = expected`; Вызов `courseReviewService.update()` |
| [deleteDelegatesAndReturnsContract](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java#L57) | Делегирование сервису и соответствие HTTP-контракту: `delete`. `actual.getStatusCode().value() = 204`; `actual.getBody()` — null; Вызов `courseReviewService.delete()` |
| [getMyReviewDelegatesAndReturnsContract](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java#L70) | Делегирование сервису и соответствие HTTP-контракту: `getMyReview`. `actual = expected`; Вызов `courseReviewService.getMyReview()` |
| [findByCourseDelegatesAndReturnsContract](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java#L84) | Делегирование сервису и соответствие HTTP-контракту: `findByCourse`. `actual = expected`; Вызов `courseReviewService.findByCourse()` |
| [getRatingDelegatesAndReturnsContract](../src/test/java/org/courseflow/review/controller/CourseReviewControllerTest.java#L102) | Делегирование сервису и соответствие HTTP-контракту: `getRating`. `actual = expected`; Вызов `courseReviewService.getRating()` |

#### [ReviewDomainTest](../src/test/java/org/courseflow/review/entity/ReviewDomainTest.java)

Состояние и инварианты доменной модели.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [reviewStoresBothLinksAndCanBeUpdated](../src/test/java/org/courseflow/review/entity/ReviewDomainTest.java#L15) | Review stores both links and can be updated. `r.getCourse() = c`; `r.getStudent() = s`; `r.getCreatedAt() = NOW` |
| [ratingsOutsideRangeAreRejected](../src/test/java/org/courseflow/review/entity/ReviewDomainTest.java#L29) | Отклонение операции: ratings Outside Range Are. Исключение `IllegalArgumentException`; `r.getRating() = 3`; `r.getComment() = "Keep"` |

#### [CourseReviewServiceReadTest](../src/test/java/org/courseflow/review/service/CourseReviewServiceReadTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [ownReviewIncludesRating](../src/test/java/org/courseflow/review/service/CourseReviewServiceReadTest.java#L39) | Own review includes rating. `service.getMyReview(40L, 10L).rating() = 5` |
| [publicReviewMapsName](../src/test/java/org/courseflow/review/service/CourseReviewServiceReadTest.java#L45) | Public review maps name. `result.content().get(0).studentName() = "Student"`; `result.content().get(0).comment() = "Good"`; `result.content().get(0).rating() = 5` |
| [ratingWithReviews](../src/test/java/org/courseflow/review/service/CourseReviewServiceReadTest.java#L56) | Rating with reviews. `result.reviewCount() = 2L`; `result.averageRating() = 4.5` |
| [emptyRatingIsNull](../src/test/java/org/courseflow/review/service/CourseReviewServiceReadTest.java#L65) | Empty rating is null. `result.reviewCount() = 0L`; `result.averageRating()` — null |

#### [CourseReviewServiceTest](../src/test/java/org/courseflow/review/service/CourseReviewServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createTrimsCommentAndStoresRating](../src/test/java/org/courseflow/review/service/CourseReviewServiceTest.java#L53) | Create trims comment and stores rating. `response.rating() = 5`; `response.comment() = "Great"` |
| [duplicateReviewRejected](../src/test/java/org/courseflow/review/service/CourseReviewServiceTest.java#L64) | Отклонение дубликата: отзыва. Исключение `BusinessConflictException`; Вызов `repo.saveAndFlush()` (`never()`) |
| [reviewCannotBeModifiedAfterWithdrawalOrArchive](../src/test/java/org/courseflow/review/service/CourseReviewServiceTest.java#L75) | Review cannot be modified after withdrawal or archive. Исключение `AccessDeniedException`; Исключение `BusinessConflictException`; Отсутствие вызовов `repo` |
| [updateAndDeleteOwnReview](../src/test/java/org/courseflow/review/service/CourseReviewServiceTest.java#L91) | Update and delete own review. `review.getRating() = 3`; `review.getComment()` — null; Вызов `repo.delete()` |
| [missingReviewRejected](../src/test/java/org/courseflow/review/service/CourseReviewServiceTest.java#L104) | Отказ при отсутствии отзыва. Исключение `ResourceNotFoundException` |

### 8.8. Показатели и дашборды (8)

#### [AdminDashboardControllerTest](../src/test/java/org/courseflow/dashboard/controller/AdminDashboardControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getDashboardDelegatesAndReturnsContract](../src/test/java/org/courseflow/dashboard/controller/AdminDashboardControllerTest.java#L18) | Делегирование сервису и соответствие HTTP-контракту: `getDashboard`. `actual = expected`; Вызов `adminDashboardService.getDashboard()` |

#### [StudentDashboardControllerTest](../src/test/java/org/courseflow/dashboard/controller/StudentDashboardControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getDashboardDelegatesAndReturnsContract](../src/test/java/org/courseflow/dashboard/controller/StudentDashboardControllerTest.java#L20) | Делегирование сервису и соответствие HTTP-контракту: `getDashboard`. `actual = expected`; Вызов `studentDashboardService.getDashboard()` |

#### [TeacherDashboardControllerTest](../src/test/java/org/courseflow/dashboard/controller/TeacherDashboardControllerTest.java)

HTTP-контракт контроллера: делегирование, параметры, статусы и результат.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [getDashboardDelegatesAndReturnsContract](../src/test/java/org/courseflow/dashboard/controller/TeacherDashboardControllerTest.java#L20) | Делегирование сервису и соответствие HTTP-контракту: `getDashboard`. `actual = expected`; Вызов `teacherDashboardService.getDashboard()` |

#### [AdminDashboardServiceTest](../src/test/java/org/courseflow/dashboard/service/AdminDashboardServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [aggregatesCountsAndMissingGroupsAsZero](../src/test/java/org/courseflow/dashboard/service/AdminDashboardServiceTest.java#L36) | Aggregates counts and missing groups as zero. `response.users().total() = 7`; `response.users().inactive() = 0`; `response.roles().students() = 4` |

#### [StudentDashboardServiceTest](../src/test/java/org/courseflow/dashboard/service/StudentDashboardServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [dashboardUsesTotalCourseCountAndLimitsUpcomingDeadlines](../src/test/java/org/courseflow/dashboard/service/StudentDashboardServiceTest.java#L23) | Dashboard uses total course count and limits upcoming deadlines. `result.activeCourseCount() = 12L`; `result.courses().content() = List.of(summary)`; `result.upcomingDeadlines() = List.of(deadline)` |

#### [TeacherDashboardServiceTest](../src/test/java/org/courseflow/dashboard/service/TeacherDashboardServiceTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [combinesCourseCountsPendingSubmissionsAndRecentResults](../src/test/java/org/courseflow/dashboard/service/TeacherDashboardServiceTest.java#L27) | Combines course counts pending submissions and recent results. `dashboard.totalCourseCount() = 5L`; `dashboard.activeCourseCount() = 3L`; `dashboard.archivedCourseCount() = 2L` |
| [teacherWithoutCoursesHasZeroCountsAndEmptyLists](../src/test/java/org/courseflow/dashboard/service/TeacherDashboardServiceTest.java#L60) | Teacher without courses has zero counts and empty lists. `dashboard.totalCourseCount() = 0L`; `dashboard.pendingSubmissionCount() = 0L`; `dashboard.courses().isEmpty()` — истина |

#### [UpcomingSessionsTest](../src/test/java/org/courseflow/dashboard/service/UpcomingSessionsTest.java)

Поведение сервиса и бизнес-правила.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [studentAndTeacherUseDifferentScopeAndMapRows](../src/test/java/org/courseflow/dashboard/service/UpcomingSessionsTest.java#L24) | Student and teacher use different scope and map rows. `service.forStudent(10L, NOW, 5).get(0).format() = "ONLINE"`; `service.forTeacher(20L, NOW, 5).size() = 1`; Вызов `query.setParameter()` |

### 8.9. Общая инфраструктура, валидация и обработка ошибок (66)

#### [SpotBugsRegressionTest](../src/test/java/org/courseflow/common/SpotBugsRegressionTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [pageContentIsAnIndependentReadOnlySnapshot](../src/test/java/org/courseflow/common/SpotBugsRegressionTest.java#L29) | Page content is an independent read only snapshot. Исключение `UnsupportedOperationException`; `page.content() = List.of("first")` |
| [quizRequestPreservesNullsForValidationAndCopiesNestedLists](../src/test/java/org/courseflow/common/SpotBugsRegressionTest.java#L38) | Quiz request preserves nulls for validation and copies nested lists. Исключение `UnsupportedOperationException`; `request.answers().size() = 1`; `answer.optionIds().size() = 1` |
| [rolesAreCopiedWithoutDiscardingInvalidElements](../src/test/java/org/courseflow/common/SpotBugsRegressionTest.java#L56) | Roles are copied without discarding invalid elements. Исключение `UnsupportedOperationException`; `request.roles().size() = 2`; `request.roles().contains(UserRole.STUDENT)` — истина |
| [logoutRejectsMissingAndMalformedSessionIdsBeforeRepositoryAccess](../src/test/java/org/courseflow/common/SpotBugsRegressionTest.java#L70) | Logout rejects missing and malformed session ids before repository access. Исключение `InvalidSessionException`; Отсутствие вызовов `repository` |
| [authorityWithoutStringDoesNotMatchRole](../src/test/java/org/courseflow/common/SpotBugsRegressionTest.java#L81) | Authority without string does not match role. `CurrentUser.isAdmin(authentication)` — ложь |

#### [JpqlPagesTest](../src/test/java/org/courseflow/common/api/JpqlPagesTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [bindsParametersAndAppliesPaginationToDataOnly](../src/test/java/org/courseflow/common/api/JpqlPagesTest.java#L24) | Binds parameters and applies pagination to data only. `r.rows() = List.of("row")`; `r.total() = 21`; `r.response().totalPages() = 3` |
| [invalidPaginationDoesNotExecuteQueries](../src/test/java/org/courseflow/common/api/JpqlPagesTest.java#L60) | Invalid pagination does not execute queries. Исключение `BadRequestException`; Отсутствие вызовов `em` |

#### [OpenApiConfigTest](../src/test/java/org/courseflow/common/api/OpenApiConfigTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [declaresBearerSecurityAndErrorContract](../src/test/java/org/courseflow/common/api/OpenApiConfigTest.java#L27) | Declares bearer security and error contract. `api.getInfo().getVersion() = "9.8.7"`; `api.getInfo().getTitle() = "CourseFlow REST API"`; `api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme() = "bearer"` |
| [customizerDocumentsPublicProtectedAndRateLimitedOperations](../src/test/java/org/courseflow/common/api/OpenApiConfigTest.java#L46) | Customizer documents public protected and rate limited operations. `login.getSecurity().isEmpty()` — истина; `login.getResponses().containsKey("…")` — истина; `login.getResponses().get("429").getHeaders().get("Retry-After")` — значение присутствует |
| [customizerHandlesMissingPathsOrResponsesAndPreservesExistingError](../src/test/java/org/courseflow/common/api/OpenApiConfigTest.java#L76) | Customizer handles missing paths or responses and preserves existing error. `post.getResponses().get("400").getDescription() = "Custom validation"`; `api.getComponents().getSchemas().get("ApiErrorResponse")` — значение присутствует; `post.getResponses().get("…").getHeaders().containsKey("…")` — ложь |

#### [PaginationTest](../src/test/java/org/courseflow/common/api/PaginationTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [validBoundaries](../src/test/java/org/courseflow/common/api/PaginationTest.java#L19) | Valid Boundaries. `assertDoesNotThrow(() -> Pagination.validate(0, 1))`; `assertDoesNotThrow(() -> Pagination.validate(1, 100))`; `assertDoesNotThrow(() -> Pagination.validate(Integer.MAX_VALUE, 1))` |
| [rejectsNegativePage](../src/test/java/org/courseflow/common/api/PaginationTest.java#L26) | Отклонение операции: Negative Page. Исключение `BadRequestException` |
| [rejectsInvalidSize](../src/test/java/org/courseflow/common/api/PaginationTest.java#L31) | Отклонение операции: Invalid Size. Исключение `BadRequestException` |
| [rejectsOffsetOverflow](../src/test/java/org/courseflow/common/api/PaginationTest.java#L38) | Отклонение операции: Offset Overflow. Исключение `BadRequestException` |
| [normalizesDirection](../src/test/java/org/courseflow/common/api/PaginationTest.java#L43) | Normalizes Direction. `Pagination.direction("ASC") = "asc"`; `Pagination.direction("DeSc") = "desc"` |
| [rejectsInvalidDirection](../src/test/java/org/courseflow/common/api/PaginationTest.java#L49) | Отклонение операции: Invalid Direction. Исключение `BadRequestException` |
| [enforcesAllowlist](../src/test/java/org/courseflow/common/api/PaginationTest.java#L56) | Enforces Allowlist. Исключение `BadRequestException`; `assertDoesNotThrow(() -> Pagination.requireSortField("name", Set.of("name"), "bad"))` |
| [stableSortUsesIdTieBreaker](../src/test/java/org/courseflow/common/api/PaginationTest.java#L66) | Stable sort uses id tie breaker. `Pagination.stableSort("name", "desc").toList() = List.of(Sort.Order.desc("name"), Sort.Order.asc("id"))`; `Pagination.stableSort("id", "desc").toList() = List.of(Sort.Order.desc("id"))` |
| [jpqlOrderingUsesTieBreakerOnlyWhenNeeded](../src/test/java/org/courseflow/common/api/PaginationTest.java#L74) | Jpql ordering uses tie breaker only when needed. `Pagination.orderBy("u.name", "DESC", "u.id") = " ORDER BY u.name desc, u.id ASC"`; `Pagination.orderBy("u.id", "asc", "u.id") = " ORDER BY u.id asc"` |
| [literalSearchEscapesWildcards](../src/test/java/org/courseflow/common/api/PaginationTest.java#L80) | Literal search escapes wildcards. `SearchPatterns.containsLiteral("a\\b%c_d") = "%a\\\\b\\%c\\_d%"`; `SearchPatterns.containsLiteral("") = "%%"` |
| [pageResponsePreservesMetadata](../src/test/java/org/courseflow/common/api/PaginationTest.java#L86) | Page response preserves metadata. `response.content() = List.of("a", "b")`; `response.page() = 1`; `response.size() = 2` |

#### [RequestIdFilterTest](../src/test/java/org/courseflow/common/api/RequestIdFilterTest.java)

Контракт межмодульного взаимодействия.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [createsServerSideRequestIdAndCleansMdc](../src/test/java/org/courseflow/common/api/RequestIdFilterTest.java#L19) | Creates server side request id and cleans mdc. `id ≠ "untrusted"`; `RequestIdFilter.requestId(request) = id`; `MDC.get("requestId") = id` |
| [restoresPreviousMdcEvenOnFailure](../src/test/java/org/courseflow/common/api/RequestIdFilterTest.java#L40) | Restores previous mdc even on failure. Исключение `ServletException`; `MDC.get("requestId") = "previous"` |

#### [EditVersionTest](../src/test/java/org/courseflow/common/exception/EditVersionTest.java)

Преобразование исключений в ответы API.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [acceptsMatchingVersion](../src/test/java/org/courseflow/common/exception/EditVersionTest.java#L12) | Accepts matching version. `assertDoesNotThrow(() -> EditVersion.require(3L, 3))` |
| [requiresVersion](../src/test/java/org/courseflow/common/exception/EditVersionTest.java#L17) | Requires Version. Исключение `BadRequestException` |
| [rejectsStaleAndFutureVersions](../src/test/java/org/courseflow/common/exception/EditVersionTest.java#L22) | Отклонение операции: Stale And Future Versions. Исключение `OptimisticLockingFailureException` |
| [retryAfterIsPreserved](../src/test/java/org/courseflow/common/exception/EditVersionTest.java#L28) | Retry after is preserved. `new RateLimitExceededException(10).getRetryAfterSeconds() = 10` |

#### [GlobalExceptionHandlerBoundaryTest](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java)

Преобразование исключений в ответы API.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [concurrencyAndConstraintErrorsAreConflictsWithoutDatabaseDetails](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L42) | Concurrency and constraint errors are conflicts without database details. `constraint.getBody().message().contains("SQL details")` — ложь |
| [rateLimitReturnsRetryAfter](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L53) | Rate limit returns retry after. `result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER) = "42"` |
| [unavailableAndOtherHttpErrorsPreserveStatus](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L60) | Unavailable and other http errors preserve status |
| [malformedAndUnsupportedRequestsHaveDistinctCodes](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L77) | Malformed and unsupported requests have distinct codes |
| [missingHeaderParameterAndWrongTypeIdentifyField](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L97) | Missing header parameter and wrong type identify field. `handler.handleMissingHeader(header, request).getBody().message() = "Missing header: Authorization"`; `handler.handleMissingParameter(parameter, request).getBody().message() = "Missing parameter: courseId"`; `handler.handleTypeMismatch(mismatch, request).getBody().message() = "Invalid value for parameter: page"` |
| [validationErrorsAreSortedDeduplicatedAndNeverEchoRejectedValue](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L116) | Validation errors are sorted deduplicated and never echo rejected value. `result.getBody().message() = "…"` |
| [passwordByteLengthValidationUsesBadRequestCode](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L134) | Password byte length validation uses bad request code. `result.getBody().message() = "Too long"` |
| [unknownEndpointAndUnexpectedErrorsHideInternalDetails](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L153) | Unknown endpoint and unexpected errors hide internal details. `result.getBody().message() = "Internal server error"` |
| [methodNotAllowedPreservesAllowHeader](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerBoundaryTest.java#L164) | Method not allowed preserves allow header. `result.getHeaders().getFirst(HttpHeaders.ALLOW).contains("GET")` — истина |

#### [GlobalExceptionHandlerTest](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerTest.java)

Преобразование исключений в ответы API.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [mapsExpectedBusinessErrors](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerTest.java#L29) | Maps expected business errors |
| [hidesSensitiveAccessDeniedAndCredentialMessages](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerTest.java#L42) | Hides sensitive access denied and credential messages. `denied.getBody().message() = "Access denied"`; `invalid.getBody().message() = "…"` |
| [invalidSessionReturnsBearerChallenge](../src/test/java/org/courseflow/common/exception/GlobalExceptionHandlerTest.java#L53) | Invalid session returns bearer challenge. `invalid.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE) = "Bearer"` |

#### [CourseFlowMetricsTest](../src/test/java/org/courseflow/common/system/CourseFlowMetricsTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [eachBusinessEventIncrementsItsOwnCounter](../src/test/java/org/courseflow/common/system/CourseFlowMetricsTest.java#L9) | Each business event increments its own counter. `registry.get("courseflow.auth.rate_limited").counter().count() = 2.0`; `registry.get("…" + suffix).counter().count() = 1.0`; `registry.getMeters().size() = 7` |

#### [DatabaseInvariantInitializerTest](../src/test/java/org/courseflow/common/system/DatabaseInvariantInitializerTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [acquiresAdvisoryLockBeforeIssuingSchemaStatements](../src/test/java/org/courseflow/common/system/DatabaseInvariantInitializerTest.java#L18) | Acquires advisory lock before issuing schema statements. `initializer.getOrder() = Ordered.HIGHEST_PRECEDENCE`; `statements.get(0) = "SELECT pg_advisory_xact_lock(62130001)"`; `statements.stream().anyMatch(s -> s.contains("ux_tags_lower_name"))` — истина |
| [failedInitializationDoesNotContinueSilently](../src/test/java/org/courseflow/common/system/DatabaseInvariantInitializerTest.java#L33) | Failed initialization does not continue silently. Исключение `IllegalStateException`; Вызов `jdbc.execute()`; Отсутствие дополнительных вызовов `jdbc` |

#### [SystemInfoControllerTest](../src/test/java/org/courseflow/common/system/SystemInfoControllerTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [returnsLocalEndpointAndTokenLifetimeWithoutCachingHttpResponse](../src/test/java/org/courseflow/common/system/SystemInfoControllerTest.java#L13) | Returns local endpoint and token lifetime without caching http response. `response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL) = "no-store"`; `body.snapshot() = snapshot`; `body.serverEndpoint().localAddress() = "127.0.0.1"` |

#### [SystemInfoServiceTest](../src/test/java/org/courseflow/common/system/SystemInfoServiceTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [readsActualDatabaseIdentityAndCachesSnapshot](../src/test/java/org/courseflow/common/system/SystemInfoServiceTest.java#L24) | Reads actual database identity and caches snapshot. `service.snapshot() = snapshot`; `snapshot.collectedAt() = CLOCK.instant()`; `snapshot.cacheSeconds() = 60` |
| [databaseFailureDoesNotExposeExceptionOrBreakOtherSections](../src/test/java/org/courseflow/common/system/SystemInfoServiceTest.java#L97) | Database failure does not expose exception or break other sections. `snapshot.database().status() = "UNAVAILABLE"`; `snapshot.mail().status() = "NOT_CHECKED"`; `snapshot.database().serverAddress()` — null |
| [optionalSqlFailurePreservesIdentityAndOtherMetric](../src/test/java/org/courseflow/common/system/SystemInfoServiceTest.java#L111) | Optional sql failure preserves identity and other metric. `db.status() = "PARTIAL"`; `db.databaseName() = "courseflow"`; `db.tableCount() = 22L` |

#### [SystemStatusControllerTest](../src/test/java/org/courseflow/common/system/SystemStatusControllerTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [returnsConnectedStatusAfterDatabaseCheck](../src/test/java/org/courseflow/common/system/SystemStatusControllerTest.java#L11) | Returns connected status after database check. `response.application() = "CourseFlow"`; `response.status() = "UP"`; `response.database() = "CONNECTED"` |

#### [SystemStatusServiceTest](../src/test/java/org/courseflow/common/system/SystemStatusServiceTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [healthyConnectionIsClosed](../src/test/java/org/courseflow/common/system/SystemStatusServiceTest.java#L19) | Healthy connection is closed. Вызов `c.close()` |
| [invalidConnectionIs503AndClosed](../src/test/java/org/courseflow/common/system/SystemStatusServiceTest.java#L29) | Invalid connection is 503 and closed. Исключение `ResponseStatusException`; `assertThrows(ResponseStatusException.class, service::checkDatabase) .getStatusCode() .value() = 503`; Вызов `c.close()` |
| [connectionFailureIs503](../src/test/java/org/courseflow/common/system/SystemStatusServiceTest.java#L42) | Connection failure is 503. Исключение `ResponseStatusException`; `assertThrows(ResponseStatusException.class, service::checkDatabase) .getStatusCode() .value() = 503` |

#### [PasswordBytesTest](../src/test/java/org/courseflow/common/validation/PasswordBytesTest.java)

Правила проверки входных данных.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [acceptsExactly72AsciiBytes](../src/test/java/org/courseflow/common/validation/PasswordBytesTest.java#L12) | Accepts exactly 72 ascii bytes |
| [rejects73AsciiBytes](../src/test/java/org/courseflow/common/validation/PasswordBytesTest.java#L17) | Отклонение операции: 73 Ascii Bytes |
| [countsUtf8BytesInsteadOfCharacters](../src/test/java/org/courseflow/common/validation/PasswordBytesTest.java#L22) | Counts utf 8 bytes instead of characters |
| [acceptsPasswordWithinLimit](../src/test/java/org/courseflow/common/validation/PasswordBytesTest.java#L28) | Accepts password within limit |
| [rejectsOversizedPassword](../src/test/java/org/courseflow/common/validation/PasswordBytesTest.java#L34) | Отклонение операции: Oversized Password |

#### [RequestDtoValidationTest](../src/test/java/org/courseflow/common/validation/RequestDtoValidationTest.java)

Правила проверки входных данных.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [validRegistrationAccepted](../src/test/java/org/courseflow/common/validation/RequestDtoValidationTest.java#L31) | Valid registration accepted. `invalidFields(new RegistrationRequest("Student", "student@example.test", "Valid-pass-1")) .isEmpty()` — истина |
| [registrationRejectsInvalidEmailAndBlankName](../src/test/java/org/courseflow/common/validation/RequestDtoValidationTest.java#L38) | Registration rejects invalid email and blank name. `invalidFields(new RegistrationRequest("…", "…", "…")) = Set.of("…", "…")` |
| [registrationEnforcesUtf8ByteLimit](../src/test/java/org/courseflow/common/validation/RequestDtoValidationTest.java#L45) | Registration enforces utf 8 byte limit. `invalidFields(new RegistrationRequest("Student", "student@example.test", "я".repeat(37))) = Set.of("…")` |
| [adminCreationRequiresRoles](../src/test/java/org/courseflow/common/validation/RequestDtoValidationTest.java#L52) | Admin creation requires roles. `invalidFields( new AdminCreateUserRequest( "Student", "student@example.test", "Valid-pass-1", Set.of(), false)) = Set.of("roles")` |
| [questionValidatesNestedOption](../src/test/java/org/courseflow/common/validation/RequestDtoValidationTest.java#L61) | Question validates nested option. `invalidFields(request).stream() .anyMatch(path -> path.contains("options") && path.endsWith("text"))` — истина |

#### [ValidationConfigTest](../src/test/java/org/courseflow/common/validation/ValidationConfigTest.java)

Правила проверки входных данных.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [validatorUsesInjectedClock](../src/test/java/org/courseflow/common/validation/ValidationConfigTest.java#L15) | Validator uses injected clock. `validator.validate(new FutureValue(NOW.plusSeconds(1))).isEmpty()` — истина; `validator.validate(new FutureValue(NOW)).isEmpty()` — ложь |

#### [ValidationUtilitiesTest](../src/test/java/org/courseflow/common/validation/ValidationUtilitiesTest.java)

Правила проверки входных данных.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [emailNormalization](../src/test/java/org/courseflow/common/validation/ValidationUtilitiesTest.java#L18) | Email Normalization. `EmailAddresses.normalize("…") = "alice@example.test"` |
| [optionalUrlAcceptsOnlyAbsoluteHttpAndHttps](../src/test/java/org/courseflow/common/validation/ValidationUtilitiesTest.java#L23) | Optional url accepts only absolute http and https. `HttpUrls.optional(" https://example.test/a ", "url") = "https://example.test/a"`; `HttpUrls.optional("http://localhost:8080/a", "url") = "http://localhost:8080/a"`; `HttpUrls.optional(null, "url")` — null |
| [invalidUrlsRejected](../src/test/java/org/courseflow/common/validation/ValidationUtilitiesTest.java#L31) | Отклонение некорректного значения: urls. Исключение `BadRequestException` |
| [byteLimitValidatorSupportsNullAndMultibyte](../src/test/java/org/courseflow/common/validation/ValidationUtilitiesTest.java#L45) | Byte limit validator supports null and multibyte. `validator.isValid(null, null)` — истина; `validator.isValid("\u044f\u044f", null)` — истина; `validator.isValid("\u044f\u044fa", null)` — ложь |
| [requireFitsRejectsLongPassword](../src/test/java/org/courseflow/common/validation/ValidationUtilitiesTest.java#L59) | Require fits rejects long password. Исключение `BadRequestException`; `assertDoesNotThrow(() -> PasswordBytes.requireFits("…".repeat(72), "…"))` |

### 8.10. Конфигурация безопасности (21)

#### [EndpointAuthorizationTest](../src/test/java/org/courseflow/security/EndpointAuthorizationTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [registersPublicHealthProtectedGradingAndDefaultDeny](../src/test/java/org/courseflow/security/EndpointAuthorizationTest.java#L45) | Registers public health protected grading and default deny. Вызов `registry.requestMatchers()`; Вызов `grading.hasAnyRole()`; Вызов `fallback.denyAll()` |
| [swaggerRouteIsRegisteredOnlyWhenEnabled](../src/test/java/org/courseflow/security/EndpointAuthorizationTest.java#L78) | Swagger route is registered only when enabled. Вызов `docs.permitAll()` |
| [temporaryPasswordMatcherDeniesAllExceptPasswordChangeAndLogout](../src/test/java/org/courseflow/security/EndpointAuthorizationTest.java#L96) | Temporary password matcher denies all except password change and logout. `matcher.matches(new MockHttpServletRequest("GET", "/api/courses"))` — ложь; `matcher.matches(new MockHttpServletRequest("GET", "/api/courses"))` — истина; `matcher.matches(new MockHttpServletRequest("GET", "…"))` — истина |

#### [JwtConfigTest](../src/test/java/org/courseflow/security/JwtConfigTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [rejectsShortOrInvalidSigningKey](../src/test/java/org/courseflow/security/JwtConfigTest.java#L23) | Отклонение операции: Short Or Invalid Signing Key. Исключение `IllegalArgumentException` |
| [signsAndDecodesJwtUsingConfiguredKey](../src/test/java/org/courseflow/security/JwtConfigTest.java#L31) | Signs and decodes jwt using configured key. `key.getAlgorithm() = "HmacSHA256"`; `decoded.getSubject() = "10"`; `decoded.getId() = session.getId().toString()` |

#### [JwtServiceTest](../src/test/java/org/courseflow/security/JwtServiceTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [generatesTokenWithSortedRolesSessionIdAndPasswordChangeFlag](../src/test/java/org/courseflow/security/JwtServiceTest.java#L26) | Generates token with sorted roles session id and password change flag. `new JwtService(encoder).generateAccessToken(session) = "…"`; `claims.getSubject() = "10"`; `claims.getId() = sessionId.toString()` |

#### [JwtSessionValidatorTest](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [validatesCurrentRolesIndependentOfTokenOrder](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java#L41) | Validates current roles independent of token order. `validator .validate(token("…", session.toString(), List.of("TEACHER", "STUDENT"), false)) .hasErrors()` — ложь |
| [rejectsMissingOrMalformedSessionId](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java#L51) | Отклонение операции: Missing Or Malformed Session Id. `validator.validate(token("…", null, List.of("STUDENT"), false)).hasErrors()` — истина; `validator.validate(token("…", "…", List.of("STUDENT"), false)).hasErrors()` — истина; Отсутствие вызовов `sessions` |
| [rejectsInvalidSubject](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java#L58) | Отклонение операции: Invalid Subject. `validator .validate(token(sub, session.toString(), List.of("STUDENT"), false)) .hasErrors()` — истина; Отсутствие вызовов `sessions` |
| [rejectsRevokedSessionAndChangedRoles](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java#L69) | Отклонение операции: Revoked Session And Changed Roles. `validator.validate(token("…", session.toString(), List.of("STUDENT"), false)).hasErrors()` — истина; `validator.validate(token("…", session.toString(), null, false)).hasErrors()` — истина |
| [passesPasswordChangeFlagToSessionLookup](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java#L79) | Passes password change flag to session lookup. `validator.validate(token("…", session.toString(), List.of("STUDENT"), true)).hasErrors()` — ложь; Вызов `sessions.activeRoles()` |
| [currentUserExtractsIdAndRoles](../src/test/java/org/courseflow/security/JwtSessionValidatorTest.java#L88) | Current user extracts id and roles. `CurrentUser.id(jwt) = 10L`; `CurrentUser.isAdmin(auth)` — истина; `CurrentUser.hasRole(auth, "STUDENT")` — ложь |

#### [PublicEndpointsTest](../src/test/java/org/courseflow/security/PublicEndpointsTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [publicGetTemplatesAreMatchedWithoutExposingLessonContent](../src/test/java/org/courseflow/security/PublicEndpointsTest.java#L13) | Public get templates are matched without exposing lesson content. `PublicEndpoints.isPublicGet("/api/courses/{courseId}/modules/{moduleId}")` — истина; `PublicEndpoints.isPublicGet("/api/courses/{courseId}/modules/{moduleId}/lessons")` — ложь; `PublicEndpoints.isPublicGet("/api/users/me")` — ложь |
| [passwordChangeRequiresAuthenticationButIsRateLimited](../src/test/java/org/courseflow/security/PublicEndpointsTest.java#L21) | Password change requires authentication but is rate limited. `PublicEndpoints.isPublicPost("/api/auth/login")` — истина; `PublicEndpoints.isPublicPost("…")` — ложь; `PublicEndpoints.isRateLimitedPost("…")` — истина |
| [startupRunnerDelegatesSessionInvalidation](../src/test/java/org/courseflow/security/PublicEndpointsTest.java#L29) | Startup runner delegates session invalidation. Вызов `access.invalidateAllOnStartup()` |

#### [SecurityConfigTest](../src/test/java/org/courseflow/security/SecurityConfigTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [corsAllowsOnlyExplicitOriginsAndExposesRequiredHeaders](../src/test/java/org/courseflow/security/SecurityConfigTest.java#L15) | Cors allows only explicit origins and exposes required headers. `cors.getAllowedOrigins() = List.of("https://example.test", "http://localhost:5173")`; `cors.getAllowCredentials()` — ложь; `cors.getAllowedMethods().contains("PATCH")` — истина |
| [wildcardOriginIsRejected](../src/test/java/org/courseflow/security/SecurityConfigTest.java#L27) | Отклонение операции: wildcard Origin Is. Исключение `IllegalArgumentException` |
| [passwordEncoderHashesAndChecksPasswords](../src/test/java/org/courseflow/security/SecurityConfigTest.java#L34) | Password encoder hashes and checks passwords. `encoder.matches("…", hash)` — истина; `encoder.matches("incorrect", hash)` — ложь |

#### [SecurityExceptionHandlerTest](../src/test/java/org/courseflow/security/SecurityExceptionHandlerTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [unauthorizedIncludesBearerChallengeAndMachineReadableError](../src/test/java/org/courseflow/security/SecurityExceptionHandlerTest.java#L18) | Unauthorized includes bearer challenge and machine readable error. `response.getStatus() = 401`; `response.getHeader("WWW-Authenticate") = "Bearer"`; `body.get("code").asText() = "AUTHENTICATION_REQUIRED"` |
| [forbiddenDoesNotExposeInternalExceptionOrBearerChallenge](../src/test/java/org/courseflow/security/SecurityExceptionHandlerTest.java#L31) | Forbidden does not expose internal exception or bearer challenge. `response.getStatus() = 403`; `body.get("code").asText() = "ACCESS_DENIED"`; `body.get("message").asText() = "Access denied"` |

#### [SystemInfoAuthorizationTest](../src/test/java/org/courseflow/security/SystemInfoAuthorizationTest.java)

Проверки компонента и его контрактов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [registersDedicatedAdminMatcherAndPreservesStatusPolicy](../src/test/java/org/courseflow/security/SystemInfoAuthorizationTest.java#L12) | Registers dedicated admin matcher and preserves status policy. Вызов `diagnostics.hasRole()`; Вызов `status.authenticated()` |

### 8.11. Архитектурные ограничения (15)

#### [ArchitectureTest](../src/test/java/org/courseflow/architecture/ArchitectureTest.java)

Проверки зависимостей Java-классов средствами ArchUnit.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [allProductionModulesAreImportedAndKnown](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L130) | Все рабочие модули входят в архитектурную модель. `actual.equals(MODULE_DEPENDENCIES.keySet())` — истина |
| [controllersCannotAccessPersistenceDirectly](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L155) | Контроллеры обращаются к данным через прикладные слои |
| [nonWebCodeCannotDependOnControllers](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L164) | Зависимости от контроллеров разрешены только web-слою |
| [repositoriesCannotDependOnHigherLayers](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L169) | Репозитории не зависят от верхних слоёв |
| [repositoriesArePrivateToTheirOwningModule](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L181) | Репозитории используются в пределах своего модуля |
| [businessServicesAndModuleApisAreIndependentOfHttp](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L189) | Сервисы и межмодульные контракты независимы от HTTP |
| [domainModelDoesNotDependOnApplicationOrInfrastructureLayers](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L197) | Доменная модель независима от прикладных и инфраструктурных слоёв |
| [dtosCannotDependOnPersistenceOrBusinessServices](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L211) | DTO независимы от репозиториев и сервисов |
| [controllersDoNotReturnJpaEntitiesEvenInsideGenericContainers](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L224) | JPA-сущности отсутствуют в ответах контроллеров, включая generic-контейнеры |
| [dtosDoNotStoreJpaEntitiesEvenInsideGenericContainers](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L245) | DTO не содержат JPA-сущностей, включая generic-контейнеры |
| [springDependenciesAreNotInjectedIntoFields](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L263) | Внедрение зависимостей через конструктор |
| [frameworkStereotypesResideInTheAppropriateLayers](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L290) | Spring-аннотации соответствуют слоям |
| [crossModuleServiceDependenciesDoNotGrowWithoutReview](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L320) | Новые межмодульные зависимости сервисов требуют явного согласования |
| [moduleDependencyDirectionsAndExistingCyclesDoNotGrow](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L332) | Контроль направлений зависимостей и существующих циклов. `newCyclicEdges.isEmpty()` — истина |
| [springComponentConstructorDependenciesHaveNoCycles](../src/test/java/org/courseflow/architecture/ArchitectureTest.java#L362) | Отсутствие циклов конструкторных зависимостей Spring-компонентов. `cycles.isEmpty()` — истина |

### 8.12. Интеграционные тесты (677)

#### Запуск приложения и общие проверки

#### [ApplicationContextIT](../src/test/java/org/courseflow/integration/ApplicationContextIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [shouldLoadApplicationContext](../src/test/java/org/courseflow/integration/ApplicationContextIT.java#L15) | Запуск контекста Spring. `jdbcTemplate.queryForObject("SELECT 1", Integer.class) = 1` |
| [shouldUseTemporaryDatabase](../src/test/java/org/courseflow/integration/ApplicationContextIT.java#L20) | Подключение к временной тестовой БД. `database = "courseflow_test"` |
| [shouldUsePostgres18](../src/test/java/org/courseflow/integration/ApplicationContextIT.java#L27) | Версия СУБД PostgreSQL 18. `version.startsWith("PostgreSQL 18")` — истина |
| [shouldCreateAllDatabaseTables](../src/test/java/org/courseflow/integration/ApplicationContextIT.java#L34) | Create all database tables. `tableCount = 22L` |

#### [PublicTeacherSearchIT](../src/test/java/org/courseflow/integration/PublicTeacherSearchIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [publicDirectorySearchesOnlyActiveTeacherNamesWithStablePagination](../src/test/java/org/courseflow/integration/PublicTeacherSearchIT.java#L30) | Public directory searches only active teacher names with stable pagination |

#### [SystemInfoIT](../src/test/java/org/courseflow/integration/SystemInfoIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [endpointAllowsOnlyAdminAndDoesNotChangeStatusEndpoint](../src/test/java/org/courseflow/integration/SystemInfoIT.java#L34) | Endpoint allows only admin and does not change status endpoint |

#### Документация OpenAPI

#### [OpenApiDocumentationIT](../src/test/java/org/courseflow/integration/api/OpenApiDocumentationIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [allOperationsHaveRussianDocumentationAndUniqueIdentifiers](../src/test/java/org/courseflow/integration/api/OpenApiDocumentationIT.java#L15) | All operations have russian documentation and unique identifiers. HTTP 200; `tags.size() = 1`; `count = 107` |
| [documentationPreservesSecuritySchemasAndResponseCodes](../src/test/java/org/courseflow/integration/api/OpenApiDocumentationIT.java#L46) | Documentation preserves security schemas and response codes. HTTP 200; `login.get("security") = List.of()`; `errors.keySet() = Set.of("timestamp", "status", "error", "message", "path", "code", "requestId")` |

#### Аутентификация, лимиты и proxy

#### [AuthLifecycleIT](../src/test/java/org/courseflow/integration/auth/AuthLifecycleIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [adminRecoverySendsOneLinkAndEnforcesCooldown](../src/test/java/org/courseflow/integration/auth/AuthLifecycleIT.java#L29) | Admin recovery sends one link and enforces cooldown. HTTP 403; HTTP 202; HTTP 204 |
| [expiredRecoveryTokenIsRejectedWithoutChangingPassword](../src/test/java/org/courseflow/integration/auth/AuthLifecycleIT.java#L60) | Expired recovery token is rejected without changing password. HTTP 202; HTTP 400; HTTP 200 |
| [expiredSessionIsRejectedAndCleanupPreservesNewSession](../src/test/java/org/courseflow/integration/auth/AuthLifecycleIT.java#L86) | Expired session is rejected and cleanup preserves new session. HTTP 401; HTTP 200 |
| [credentialCleanupRemovesOldResetTokensButKeepsFreshOnes](../src/test/java/org/courseflow/integration/auth/AuthLifecycleIT.java#L111) | Credential cleanup removes old reset tokens but keeps fresh ones. HTTP 202; `jdbc.update( "…""…""…", old.id()) = 1`; `resetTokenCount(old.id()) = 0L` |

#### [RateLimitHardeningIT](../src/test/java/org/courseflow/integration/auth/RateLimitHardeningIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [loginIpBudgetCannotBeBypassedByRotatingEmailsAndExpiresExactlyAtBoundary](../src/test/java/org/courseflow/integration/auth/RateLimitHardeningIT.java#L47) | Login ip budget cannot be bypassed by rotating emails and expires exactly at boundary. HTTP 429 и Retry-After; HTTP 201; `count( "…", id(registration), registeredEmail) = 1L` |
| [emailBudgetIsCaseInsensitiveAndDoesNotBlockOtherUsers](../src/test/java/org/courseflow/integration/auth/RateLimitHardeningIT.java#L81) | Email budget is case insensitive and does not block other users. HTTP 429 и Retry-After |
| [resetPasswordIpBudgetRejectsTwentyFirstRequestAndRecovers](../src/test/java/org/courseflow/integration/auth/RateLimitHardeningIT.java#L92) | Reset password ip budget rejects twenty first request and recovers. HTTP 429 и Retry-After |
| [changePasswordIpBudgetAggregatesDifferentAuthenticatedUsers](../src/test/java/org/courseflow/integration/auth/RateLimitHardeningIT.java#L105) | Change password ip budget aggregates different authenticated users. HTTP 429 и Retry-After |

#### [TrustedProxyRateLimitIT](../src/test/java/org/courseflow/integration/auth/TrustedProxyRateLimitIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [differentForwardedClientsHaveIndependentBudgets](../src/test/java/org/courseflow/integration/auth/TrustedProxyRateLimitIT.java#L29) | Different forwarded clients have independent budgets. HTTP 429 и Retry-After |
| [alternativeIpHeadersCannotOverrideForwardedFor](../src/test/java/org/courseflow/integration/auth/TrustedProxyRateLimitIT.java#L40) | Alternative ip headers cannot override forwarded for. HTTP 429 и Retry-After |
| [missingForwardedForFallsBackToConnectionAddress](../src/test/java/org/courseflow/integration/auth/TrustedProxyRateLimitIT.java#L63) | Missing forwarded for falls back to connection address. HTTP 429 и Retry-After |

#### [UntrustedProxyRateLimitIT](../src/test/java/org/courseflow/integration/auth/UntrustedProxyRateLimitIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [forgedForwardingHeadersCannotCreateNewIpBudgets](../src/test/java/org/courseflow/integration/auth/UntrustedProxyRateLimitIT.java#L18) | Подмена proxy-заголовков не сбрасывает лимит запросов по IP. HTTP 400; HTTP 429; `field(body, "code") = "BAD_REQUEST"` |

[`NonMatchingProxyRateLimitIT`](../src/test/java/org/courseflow/integration/auth/NonMatchingProxyRateLimitIT.java) — тот же унаследованный сценарий `forgedForwardingHeadersCannotCreateNewIpBudgets`, но с настроенным доверенным proxy по другому IP. Ожидается HTTP 429 после исчерпания лимита.

#### Граничные значения и время

#### [ApiBoundaryIT](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [categoryNameLength99](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L14) | Граничная длина названия категории: 99. HTTP 201 |
| [categoryNameLength100](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L22) | Граничная длина названия категории: 100. HTTP 201 |
| [categoryNameLength101](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L30) | Граничная длина названия категории: 101. HTTP 400 |
| [categoryDescriptionLength1999](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L38) | Граничная длина описания категории: 1999. HTTP 201 |
| [categoryDescriptionLength2000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L46) | Граничная длина описания категории: 2000. HTTP 201 |
| [categoryDescriptionLength2001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L54) | Граничная длина описания категории: 2001. HTTP 400 |
| [tagNameLength49](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L62) | Граничная длина названия тега: 49. HTTP 201 |
| [tagNameLength50](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L68) | Граничная длина названия тега: 50. HTTP 201 |
| [tagNameLength51](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L74) | Граничная длина названия тега: 51. HTTP 400 |
| [courseTitleLength199](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L80) | Граничная длина названия курса: 199. HTTP 201 |
| [courseTitleLength200](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L87) | Граничная длина названия курса: 200. HTTP 201 |
| [courseTitleLength201](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L94) | Граничная длина названия курса: 201. HTTP 400 |
| [courseDescriptionLength4999](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L101) | Граничная длина описания курса: 4999. HTTP 201 |
| [courseDescriptionLength5000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L108) | Граничная длина описания курса: 5000. HTTP 201 |
| [courseDescriptionLength5001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L115) | Граничная длина описания курса: 5001. HTTP 400 |
| [courseDuration0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L122) | Граничное значение длительности курса: 0. HTTP 400 |
| [courseDuration1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L129) | Граничное значение длительности курса: 1. HTTP 201 |
| [courseDuration2](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L136) | Граничное значение длительности курса: 2. HTTP 201 |
| [pageSize0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L143) | Граничное значение размера страницы: 0. HTTP 400 |
| [pageSize1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L149) | Граничное значение размера страницы: 1. HTTP 200; `number(response.body(), "size") = 1L` |
| [pageSize100](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L156) | Граничное значение размера страницы: 100. HTTP 200; `number(response.body(), "size") = 100L` |
| [pageSize101](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L163) | Граничное значение размера страницы: 101. HTTP 400 |
| [courseSearchLength199](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L169) | Граничная длина строки поиска курса: 199. HTTP 200 |
| [courseSearchLength200](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L174) | Граничная длина строки поиска курса: 200. HTTP 200 |
| [courseSearchLength201](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L179) | Граничная длина строки поиска курса: 201. HTTP 400 |
| [pageOffsetOverflowRejected](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L184) | Отклонение операции: page Offset Overflow. HTTP 400 |
| [moduleTitleLength200](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L189) | Граничная длина module title: 200. HTTP 201 |
| [moduleTitleLength201](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L197) | Граничная длина module title: 201. HTTP 400 |
| [moduleDescriptionLength2000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L205) | Граничная длина module description: 2000. HTTP 201 |
| [moduleDescriptionLength2001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L213) | Граничная длина module description: 2001. HTTP 400 |
| [lessonTitleLength200](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L221) | Граничная длина lesson title: 200. HTTP 201 |
| [lessonTitleLength201](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L236) | Граничная длина lesson title: 201. HTTP 400 |
| [lessonContentLength20000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L251) | Граничная длина содержимого урока: 20000. HTTP 201 |
| [lessonContentLength20001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L266) | Граничная длина содержимого урока: 20001. HTTP 400 |
| [lessonVideourlLength2048](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L281) | Граничная длина lesson videourl: 2048. HTTP 201 |
| [lessonVideourlLength2049](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L296) | Граничная длина lesson videourl: 2049. HTTP 400 |
| [assignmentTitleLength200](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L311) | Граничная длина assignment title: 200. HTTP 201 |
| [assignmentTitleLength201](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L320) | Граничная длина assignment title: 201. HTTP 400 |
| [assignmentDescriptionLength20000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L329) | Граничная длина assignment description: 20000. HTTP 201 |
| [assignmentDescriptionLength20001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L338) | Граничная длина assignment description: 20001. HTTP 400 |
| [assignmentMaxScore0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L347) | Assignment max score 0. HTTP 400 |
| [assignmentMaxScore1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L356) | Assignment max score 1. HTTP 201 |
| [submissionContentLength20000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L365) | Граничная длина submission content: 20000. HTTP 201; `count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment) = 1L` |
| [submissionContentLength20001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L380) | Граничная длина submission content: 20001. HTTP 400; `count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment) = 0L` |
| [gradeScoreMinus1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L395) | Grade score minus 1. HTTP 201; HTTP 400; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission))` — null |
| [gradeScore0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L421) | Grade score 0. HTTP 201; HTTP 200; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)) = 0` |
| [gradeScore100](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L448) | Grade score 100. HTTP 201; HTTP 200; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission)) = 100` |
| [gradeScore101](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L475) | Grade score 101. HTTP 201; HTTP 400; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, id(submission))` — null |
| [reviewRating0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L501) | Граничное значение рейтинга отзыва: 0. HTTP 400; `count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()) = 0L` |
| [reviewRating1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L515) | Граничное значение рейтинга отзыва: 1. HTTP 201; `count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()) = 1L` |
| [reviewRating5](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L529) | Граничное значение рейтинга отзыва: 5. HTTP 201; `count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()) = 1L` |
| [reviewRating6](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L543) | Граничное значение рейтинга отзыва: 6. HTTP 400; `count("SELECT count(*) FROM course_reviews WHERE course_id = ?", g.course()) = 0L` |
| [quizTimeLimit0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L557) | Quiz time limit 0. HTTP 400 |
| [quizTimeLimit1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L569) | Quiz time limit 1. HTTP 201 |
| [quizTimeLimit240](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L581) | Quiz time limit 240. HTTP 201 |
| [quizTimeLimit241](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L593) | Quiz time limit 241. HTTP 400 |
| [quizMaxAttempts0](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L605) | Quiz max attempts 0. HTTP 200; HTTP 400 |
| [quizMaxAttempts1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L626) | Quiz max attempts 1. HTTP 200 |
| [quizMaxAttempts10](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L647) | Quiz max attempts 10. HTTP 200 |
| [quizMaxAttempts11](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L668) | Quiz max attempts 11. HTTP 200; HTTP 400 |
| [questionOptionCount1](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L689) | Question option count 1. HTTP 400 |
| [questionOptionCount2](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L701) | Question option count 2. HTTP 201 |
| [questionOptionCount10](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L713) | Question option count 10. HTTP 201 |
| [questionOptionCount11](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L725) | Question option count 11. HTTP 400 |
| [questionTextLength5000](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L737) | Граничная длина текста вопроса: 5000. HTTP 201 |
| [questionTextLength5001](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L745) | Граничная длина текста вопроса: 5001. HTTP 400 |
| [passwordSevenChars](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L753) | Password seven chars. HTTP 400; `count("…", address) = 0L` |
| [passwordEightChars](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L777) | Password eight chars. HTTP 201; `count("…", address) = 1L`; `login(address, password).isBlank()` — ложь |
| [passwordAscii72](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L802) | Password ascii 72. HTTP 201; `count("…", address) = 1L`; `login(address, password).isBlank()` — ложь |
| [passwordAscii73](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L827) | Password ascii 73. HTTP 400; `count("…", address) = 0L` |
| [passwordUtf8Exactly72](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L851) | Password utf 8 exactly 72. HTTP 201; `count("…", address) = 1L`; `login(address, password).isBlank()` — ложь |
| [passwordUtf8Over72](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L876) | Password utf 8 over 72. HTTP 400; `count("…", address) = 0L` |
| [passwordSupplementary72](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L900) | Password supplementary 72. HTTP 201; `count("…", address) = 1L`; `login(address, password).isBlank()` — ложь |
| [passwordSupplementary76](../src/test/java/org/courseflow/integration/boundary/ApiBoundaryIT.java#L925) | Password supplementary 76. HTTP 400; `count("…", address) = 0L` |

#### [TemporalBoundaryIT](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [assignmentSubmissionOneMicrosecondBeforeDeadlineSucceeds](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L31) | Assignment submission one microsecond before deadline succeeds |
| [assignmentSubmissionExactlyAtDeadlineIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L36) | Отклонение операции: assignment Submission Exactly At Deadline Is |
| [assignmentSubmissionOneMicrosecondAfterDeadlineIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L41) | Отклонение операции: assignment Submission One Microsecond After Deadline Is |
| [quizSubmissionOneMicrosecondBeforeExpirySucceeds](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L46) | Quiz submission one microsecond before expiry succeeds |
| [quizSubmissionExactlyAtExpiryIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L51) | Отклонение операции: quiz Submission Exactly At Expiry Is |
| [quizSubmissionOneMicrosecondAfterExpiryIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L56) | Отклонение операции: quiz Submission One Microsecond After Expiry Is |
| [scheduleWithEqualStartAndEndIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L61) | Отклонение операции: schedule With Equal Start And End Is |
| [sessionStartingAtCurrentTimeIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L68) | Отклонение операции: session Starting At Current Time Is |
| [adjacentSessionsForSameTeacherDoNotOverlap](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L74) | Adjacent sessions for same teacher do not overlap. `count("SELECT count(*) FROM lesson_sessions WHERE lesson_id = ?", g.lesson()) = 2L` |
| [sessionOverlappingByOneMicrosecondIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L83) | Отклонение операции: session Overlapping By One Microsecond Is. `count("SELECT count(*) FROM lesson_sessions WHERE lesson_id = ?", g.lesson()) = 1L` |
| [extendingAssignmentToExactlyItsExistingDeadlineIsRejected](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L96) | Отклонение операции: extending Assignment To Exactly Its Existing Deadline Is. HTTP 200; HTTP 400; `jdbc.queryForObject( "SELECT version FROM assignments WHERE id = ?", Long.class, assignment) = number(current, "version")` |
| [extendingAssignmentByOneMicrosecondSucceeds](../src/test/java/org/courseflow/integration/boundary/TemporalBoundaryIT.java#L115) | Extending assignment by one microsecond succeeds. HTTP 200; `jdbc.queryForObject( "SELECT due_at FROM assignments WHERE id = ?", Timestamp.class, assignment) .toInstant() = due` |

#### Бизнес-инварианты и откат

#### [BusinessIntegrityIT](../src/test/java/org/courseflow/integration/business/BusinessIntegrityIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [alteredJwtSignatureCannotAuthenticateButOriginalStillWorks](../src/test/java/org/courseflow/integration/business/BusinessIntegrityIT.java#L15) | Altered jwt signature cannot authenticate but original still works. HTTP 401; HTTP 200; `parts.length = 3` |
| [deactivationRevokesAllExistingSessionsWithoutDeletingEnrollments](../src/test/java/org/courseflow/integration/business/BusinessIntegrityIT.java#L33) | Deactivation revokes all existing sessions without deleting enrollments. HTTP 200; HTTP 401; `second ≠ target.token()` |
| [maximumLessonContentRoundTripsAndRejectedUpdateLeavesStoredValueUnchanged](../src/test/java/org/courseflow/integration/business/BusinessIntegrityIT.java#L56) | Maximum lesson content round trips and rejected update leaves stored value unchanged. HTTP 200; HTTP 400; `field(updated, "content") = content` |
| [invalidQuizAnswerCannotPartiallyPersistAndRetryCalculatesActualScore](../src/test/java/org/courseflow/integration/business/BusinessIntegrityIT.java#L106) | Invalid quiz answer cannot partially persist and retry calculates actual score. HTTP 201; HTTP 400; HTTP 200 |
| [submittedAndInProgressAttemptFiltersReturnDifferentKnownIds](../src/test/java/org/courseflow/integration/business/BusinessIntegrityIT.java#L207) | Submitted and in progress attempt filters return different known ids. HTTP 200; `number(submitted, "totalElements") = 1L`; `number(active, "totalElements") = 1L` |

#### [TransactionRecoveryIT](../src/test/java/org/courseflow/integration/business/TransactionRecoveryIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [failedQuizCompletionRollsBackAnswersAndAllowsRetry](../src/test/java/org/courseflow/integration/business/TransactionRecoveryIT.java#L13) | Failed quiz completion rolls back answers and allows retry. HTTP 201; HTTP 500; HTTP 200 |

#### Параллельные операции и блокировки

#### [ConcurrentCleanupIT](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [cleanupSkipsLockedExpiredAttemptAndProcessesUnlockedAttempt](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java#L32) | Cleanup skips locked expired attempt and processes unlocked attempt. `status(locked) = "IN_PROGRESS"`; `status(free) = "EXPIRED"`; `status(locked) = "EXPIRED"` |
| [twoCleanupInvocationsDoNotCorruptAttemptState](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java#L76) | Two cleanup invocations do not corrupt attempt state |
| [cleanupRacingWithExpiredSubmissionCannotPersistAnswers](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java#L95) | Cleanup racing with expired submission cannot persist answers. `results.get(0) = 409`; `count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt) = 0L` |
| [cleanupRacingWithValidSubmissionDoesNotExpireIt](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java#L122) | Cleanup racing with valid submission does not expire it. `results.get(0) = 200`; `status(attempt) = "SUBMITTED"`; `jdbc.queryForObject( "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt) = 100` |
| [cleanupLeavesAlreadySubmittedAttemptAndItsAnswersIntact](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java#L149) | Cleanup leaves already submitted attempt and its answers intact. HTTP 200; `status(attempt) = "SUBMITTED"`; `count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt) = 1L` |
| [cleanupProcessesAtMost250ExpiredAttemptsPerBatch](../src/test/java/org/courseflow/integration/concurrency/ConcurrentCleanupIT.java#L172) | Cleanup processes at most 250 expired attempts per batch. `jdbc.update( "…""…""…", prefix, Timestamp.from(expiry.minusSeconds(3600))) = 251`; `count( "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'EXPIRED'", q.quiz()) = 250L`; `count( "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'IN_PROGRESS'", q.quiz()) = 1L` |

#### [ConcurrentOperationsIT](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [concurrentRegistrationCreatesOneAccount](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L63) | Concurrent registration creates one account. `count("…", address) = 1L`; `count( "…""…""…", address) = 1L` |
| [concurrentEnrollmentIsIdempotent](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L84) | Concurrent enrollment is idempotent. `id(responses.get(1).body()) = id(responses.get(0).body())`; `count( "SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, studentId) = 1L` |
| [concurrentHomeworkCreatesOnlyOneSubmission](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L101) | Concurrent homework creates only one submission. HTTP 200; `count( "SELECT count(*) FROM submissions WHERE assignment_id = ? AND student_id = ?", assignment, studentId) = 1L`; `id(expect(api("GET", path + "/me", student, null), 200)) = id(winner.body())` |
| [concurrentQuizStartResumesTheSameAttempt](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L121) | Concurrent quiz start resumes the same attempt. `id(responses.get(1).body()) = id(responses.get(0).body())`; `count( "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND student_id = ?", quiz.quiz(), studentId) = 1L` |
| [concurrentQuizSubmitCommitsOnlyOneResult](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L139) | Concurrent quiz submit commits only one result. HTTP 201; `jdbc.queryForObject( "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt) = intField(winner.body(), "score")`; `jdbc.queryForObject( "SELECT status FROM quiz_submissions WHERE id = ?", String.class, attempt) = "SUBMITTED"` |
| [concurrentReviewCannotCreateDuplicate](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L178) | Concurrent review cannot create duplicate. `count( "SELECT count(*) FROM course_reviews WHERE course_id = ? AND student_id = ?", f.course(), studentId) = 1L` |
| [concurrentOverlappingSessionsAcrossCoursesRejectOne](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L203) | Concurrent overlapping sessions across courses reject one. `count( "SELECT count(*) FROM lesson_sessions WHERE lesson_id IN (?, ?)", a.lesson(), b.lesson()) = 1L` |
| [concurrentGradingRejectsStaleVersion](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L233) | Concurrent grading rejects stale version. HTTP 201; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, submissionId) = intField(winner.body(), "score")`; `jdbc.queryForObject( "SELECT feedback FROM submissions WHERE id = ?", String.class, submissionId) = str(field(winner.body(), "feedback"))` |
| [concurrentCategoryUpdatesRejectStaleVersion](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L278) | Concurrent category updates reject stale version. HTTP 201; `jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, categoryId) = str(field(winner.body(), "name"))`; `jdbc.queryForObject("SELECT version FROM categories WHERE id = ?", Long.class, categoryId) = version + 1` |
| [concurrentModuleCreationRejectsDuplicatePosition](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L317) | Concurrent module creation rejects duplicate position. `count("SELECT count(*) FROM modules WHERE course_id = ? AND position = 1", course) = 1L` |
| [concurrentResetTokenCanBeUsedOnlyOnce](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L330) | Concurrent reset token can be used only once. HTTP 202; HTTP 401; HTTP 400 |
| [concurrentInvalidLoginCannotExceedEmailBudget](../src/test/java/org/courseflow/integration/concurrency/ConcurrentOperationsIT.java#L364) | Concurrent invalid login cannot exceed email budget. `responses.stream().filter(r -> r.status() == 401).count() = 12L`; `responses.stream().filter(r -> r.status() == 429).count() = 4L`; `Long.parseLong(response.header("Retry-After")) > 0` — истина |

#### [ControlledTransactionIT](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [waitingUpdateRejectsStaleVersionAfterConcurrentCommit](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L40) | Waiting update rejects stale version after concurrent commit. HTTP 409; HTTP 200; `field(error, "code") = "CONCURRENT_CONFLICT"` |
| [waitingUpdateUsesOriginalVersionAfterConcurrentRollback](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L67) | Waiting update uses original version after concurrent rollback. HTTP 200; `id(result) = initial.id()`; `number(result, "version") = initial.version() + 1` |
| [lockTimeoutReturnsConflictAndNextHttpRequestSucceeds](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L92) | Lock timeout returns conflict and next http request succeeds. HTTP 409; HTTP 200; `field(error, "code") = "CONCURRENT_CONFLICT"` |
| [accountLookupByUserIdAcquiresRealWriteLock](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L117) | Account lookup by user id acquires real write lock. `accounts.findByUserIdForUpdate(student.id()).orElseThrow().getId().longValue() = account` |
| [systemAdminLookupAcquiresRealWriteLock](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L132) | System admin lookup acquires real write lock. `users.findSystemAdminForUpdate().orElseThrow().getId().longValue() = systemAdmin` |
| [passwordResetTokenLookupAcquiresRealWriteLock](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L144) | Password reset token lookup acquires real write lock. `resetTokens.findByTokenHashForUpdate(hash).orElseThrow().getId().longValue() = token` |
| [systemAdminBootstrapWaitsForAdvisoryLockAndPreservesExistingAccounts](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L166) | System admin bootstrap waits for advisory lock and preserves existing accounts. HTTP 200; `count("SELECT count(*) FROM users WHERE system_admin") = 1L`; `jdbc.queryForObject("SELECT id FROM users WHERE system_admin", Long.class).longValue() = adminId` |
| [invariantInitializerWaitsForAdvisoryLockAndPreservesCourseData](../src/test/java/org/courseflow/integration/concurrency/ControlledTransactionIT.java#L181) | Invariant initializer waits for advisory lock and preserves course data. `count("SELECT count(*) FROM courses") = before`; `count("SELECT count(*) FROM courses WHERE id = ?", course) = 1L` |

#### [MultiUserConcurrencyIT](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [twentyStudentsEnrollInTheSameCourse](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L27) | Twenty students enroll in the same course. `count( "SELECT count(DISTINCT student_id) FROM enrollments WHERE course_id = ? AND status = 'ACTIVE'", course) = 20L` |
| [twentyStudentsSubmitIndependentHomework](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L38) | Twenty students submit independent homework. `count( "SELECT count(DISTINCT student_id) FROM submissions WHERE assignment_id = ?", assignment) = 20L` |
| [twentyStudentsStartTheirOwnQuizAttempts](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L60) | Twenty students start their own quiz attempts. `count( "SELECT count(DISTINCT student_id) FROM quiz_submissions WHERE quiz_id = ?", q.quiz()) = 20L` |
| [twentyStudentsSubmitQuizWithDifferentAnswers](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L77) | Twenty students submit quiz with different answers. HTTP 200; `count( "SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND status = 'SUBMITTED'", q.quiz()) = 20L`; `count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ? AND score = 100", q.quiz()) = 10L` |
| [twentyStudentsCreateIndependentReviews](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L118) | Twenty students create independent reviews. HTTP 201; `count("SELECT count(DISTINCT student_id) FROM course_reviews WHERE course_id = ?", course) = 20L`; `jdbc.queryForObject( "SELECT sum(rating) FROM course_reviews WHERE course_id = ?", Long.class, course) = 60L` |
| [teacherAndAdministratorCannotOverwriteSameCourseVersion](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L144) | Teacher and administrator cannot overwrite same course version. HTTP 200; `jdbc.queryForObject("SELECT title FROM courses WHERE id = ?", String.class, course) = field(winner, "title")`; `jdbc.queryForObject("SELECT version FROM courses WHERE id = ?", Long.class, course) = version + 1` |
| [teacherAndAdministratorGradeTheSameSubmission](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L169) | Teacher and administrator grade the same submission. HTTP 201; `jdbc.queryForObject("SELECT score FROM submissions WHERE id = ?", Long.class, id) = number(winner, "score")` |
| [studentReadsOwnSubmissionWhileTeacherGradesIt](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L197) | Student reads own submission while teacher grades it. HTTP 201; HTTP 200; `id(read) = id(submission)` |
| [administratorDeactivationRacingWithStudentEnrollment](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L234) | Administrator deactivation racing with student enrollment. HTTP 200; HTTP 401; `count( "SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, target.id()) = status == 201 ? 1L : 0L` |
| [deletingAssignmentAndSubmittingCannotBothSucceed](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L258) | Deleting assignment and submitting cannot both succeed. HTTP 404; HTTP 409; HTTP 201 |
| [archivingCourseWhileStudentsEnrollPreservesEveryCommittedEnrollment](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L290) | Archiving course while students enroll preserves every committed enrollment. HTTP 200; HTTP 409; `count( "SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ?", course, actor) = status == 201 ? 1L : 0L` |
| [quizEditRacingWithAttemptStartNeverChangesQuizAfterAttempt](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L320) | Quiz edit racing with attempt start never changes quiz after attempt. HTTP 200; HTTP 201; HTTP 409 |
| [oneStudentCannotReadAnotherStudentsAttemptDuringSubmission](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L376) | One student cannot read another students attempt during submission. HTTP 200; HTTP 404; `id(completed) = attempt` |
| [studentWithdrawAndOtherStudentEnrollmentDoNotInterfere](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L396) | Student withdraw and other student enrollment do not interfere. HTTP 204; HTTP 201; `number(expect(responses.get(1), 201), "studentId") = other.id()` |
| [unrelatedStudentCannotGradeWhileTeacherGrades](../src/test/java/org/courseflow/integration/concurrency/MultiUserConcurrencyIT.java#L424) | Unrelated student cannot grade while teacher grades. HTTP 201; HTTP 200; HTTP 403 |

#### Демонстрационные данные

#### [DemoDataInitializerIT](../src/test/java/org/courseflow/integration/demo/DemoDataInitializerIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [seedPreservesExistingDataAndNeverRecreatesDeletedLinks](../src/test/java/org/courseflow/integration/demo/DemoDataInitializerIT.java#L19) | Повторное наполнение сохраняет существующие данные и удалённые связи. `count("…") = 0`; `adminBefore = 1`; `count("SELECT count(*) FROM users") = usersBefore + 60` |
| [uniqueViolationRollsBackPreviouslyInsertedDemoAccount](../src/test/java/org/courseflow/integration/demo/DemoDataInitializerIT.java#L112) | Откат ранее добавленного аккаунта при нарушении уникальности. Исключение `RuntimeException`; `count("SELECT count(*) FROM users") = before`; `count("…") = 0` |

#### ORM, SQL и транзакции

#### [AdvancedIntegrityIT](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [duplicateModulePositionRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L13) | Duplicate module position rejected by database. `count("SELECT count(*) FROM modules WHERE course_id = ?", g.course()) = 1L` |
| [duplicateLessonPositionRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L28) | Duplicate lesson position rejected by database. `count("SELECT count(*) FROM lessons WHERE module_id = ?", g.module()) = 1L` |
| [duplicateQuestionPositionRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L43) | Duplicate question position rejected by database. `count("SELECT count(*) FROM questions WHERE quiz_id = ?", q.quiz()) = 1L` |
| [duplicateQuizForModuleRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L58) | Duplicate quiz for module rejected by database. `count("SELECT count(*) FROM quizzes WHERE module_id = ?", g.module()) = 1L` |
| [invalidQuizTimeLimitRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L74) | Invalid quiz time limit rejected by database. `jdbc.queryForObject( "SELECT time_limit FROM quizzes WHERE id = ?", Integer.class, q.quiz()) = 20` |
| [invalidMaxAttemptsRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L85) | Invalid max attempts rejected by database. `jdbc.queryForObject( "SELECT max_attempts FROM quizzes WHERE id = ?", Integer.class, q.quiz()) = 2` |
| [invalidAssignmentMaxScoreRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L96) | Invalid assignment max score rejected by database. `jdbc.queryForObject("SELECT max_score FROM assignments WHERE id = ?", Integer.class, a) = 100` |
| [duplicateHomeworkSubmissionRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L105) | Duplicate homework submission rejected by database. HTTP 201; `count("SELECT count(*) FROM submissions WHERE assignment_id = ?", a) = 1L` |
| [negativeHomeworkScoreRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L131) | Negative homework score rejected by database. HTTP 201; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, submission)` — null |
| [duplicateQuizAttemptNumberRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L152) | Duplicate quiz attempt number rejected by database. `count("SELECT count(*) FROM quiz_submissions WHERE quiz_id = ?", q.quiz()) = 1L` |
| [invalidQuizAttemptNumberRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L170) | Invalid quiz attempt number rejected by database. `jdbc.queryForObject( "SELECT attempt_number FROM quiz_submissions WHERE id = ?", Integer.class, attempt) = 1` |
| [quizScoreOver100RejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L184) | Quiz score over 100 rejected by database. `jdbc.queryForObject( "SELECT score FROM quiz_submissions WHERE id = ?", Integer.class, attempt)` — null |
| [zeroDurationQuizAttemptRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L197) | Zero duration quiz attempt rejected by database. `count( "SELECT count(*) FROM quiz_submissions WHERE id = ? AND expires_at > taken_at", attempt) = 1L` |
| [duplicateAnswerSelectionRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L213) | Duplicate answer selection rejected by database. HTTP 200; `count("SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", attempt) = 1L` |
| [secondSystemAdministratorFlagRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L236) | Second system administrator flag rejected by database. `count("SELECT count(*) FROM users WHERE system_admin") = 1L` |
| [duplicateRoleMembershipRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L244) | Duplicate role membership rejected by database. `count( "SELECT count(*) FROM user_roles WHERE user_id = ? AND role = 'STUDENT'", student.id()) = 1L` |
| [duplicateCourseTagJoinRowRejectedByDatabase](../src/test/java/org/courseflow/integration/orm/AdvancedIntegrityIT.java#L258) | Duplicate course tag join row rejected by database. HTTP 201; HTTP 204; `count("SELECT count(*) FROM course_tag WHERE course_id = ? AND tag_id = ?", course, tag) = 1L` |

#### [LazyInitializationIT](../src/test/java/org/courseflow/integration/orm/LazyInitializationIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [shouldThrowLazyInitializationException](../src/test/java/org/courseflow/integration/orm/LazyInitializationIT.java#L72) | Обращение к незагруженной коллекции после закрытия контекста JPA. Исключение `LazyInitializationException`; `course` — значение присутствует; `emf.getPersistenceUnitUtil().isLoaded(course, "modules")` — ложь |
| [shouldLoadModulesWithinTransaction](../src/test/java/org/courseflow/integration/orm/LazyInitializationIT.java#L88) | Загрузка модулей внутри транзакции. `course` — значение присутствует; `em.getTransaction().isActive()` — истина; `emf.getPersistenceUnitUtil().isLoaded(course, "modules")` — ложь |
| [shouldLoadModulesUsingFetchJoin](../src/test/java/org/courseflow/integration/orm/LazyInitializationIT.java#L108) | Загрузка модулей через JOIN FETCH. `emf.getPersistenceUnitUtil().isLoaded(course, "modules")` — истина; `emf.getPersistenceUnitUtil().isLoaded(detachedCourse, "modules")` — истина |

#### [OrmPersistenceIT](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [categoryPersistFlushClearAndReload](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L45) | Сохранение категории, очистка контекста и чтение из БД. `loaded ≠ category`; `loaded.getName() = name`; `jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, id) = "Changed description"` |
| [rollbackAfterFlushLeavesNoPersistedRow](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L70) | Откат после flush отменяет вставку строки. `count("SELECT count(*) FROM categories WHERE name = ?", name) = 0L` |
| [cascadePersistStoresCourseModuleLessonAndSession](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L91) | Каскадное сохранение курса, модуля, урока и занятия. `count("SELECT count(*) FROM modules WHERE course_id = ?", g.course()) = 1L`; `count("SELECT count(*) FROM lessons WHERE module_id = ?", g.module()) = 1L`; `count("SELECT count(*) FROM lesson_sessions WHERE lesson_id = ?", g.lesson()) = 1L` |
| [lazyCollectionsLoadInsidePersistenceContext](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L105) | Lazy collections load inside persistence context. `modules.size() = 1`; `modules.get(0).getId() = g.module()`; `modules.get(0).getCourse().getId() = g.course()` |
| [manyToManyDetachRemovesJoinRowButPreservesTag](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L123) | Many to many detach removes join row but preserves tag. `count( "SELECT count(*) FROM course_tag WHERE course_id = ? AND tag_id = ?", g.course(), g.tag()) = 0L`; `count("SELECT count(*) FROM tags WHERE id = ?", g.tag()) = 1L` |
| [quizQuestionAnswerCascadePersistsOwningSide](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L141) | Quiz question answer cascade persists owning side. `count("SELECT count(*) FROM questions WHERE quiz_id = ?", q.quiz()) = 2L`; `count("SELECT count(*) FROM answer_options WHERE question_id = ?", q.firstQuestion()) = 2L`; `count( "SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", q.attempt()) = 1L` |
| [databaseRejectsCaseInsensitiveDuplicateCategoryAndTag](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L159) | Database rejects case insensitive duplicate category and tag |
| [databaseRejectsInvalidCourseDurationAndPosition](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L181) | Database rejects invalid course duration and position. `jdbc.queryForObject( "SELECT duration FROM courses WHERE id = ?", Integer.class, g.course()) = 56` |
| [databaseRejectsDuplicateEnrollmentAndInvalidReviewRating](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L196) | Database rejects duplicate enrollment and invalid review rating. `jdbc.queryForObject( "SELECT rating FROM course_reviews WHERE id = ?", Integer.class, review) = 5` |
| [databaseRejectsAnswerOptionFromDifferentQuestion](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L238) | Database rejects answer option from different question. `count( "SELECT count(*) FROM quiz_answer_selections WHERE quiz_submission_id = ?", q.attempt()) = 1L` |
| [databaseRejectsDeletingReferencedCategory](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L260) | Database rejects deleting referenced category. `count("SELECT count(*) FROM categories WHERE id = ?", g.category()) = 1L` |
| [hibernateVersionPreventsLostUpdateAcrossTwoTransactions](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L267) | Hibernate version prevents lost update across two transactions. `results.stream().filter(UpdateResult::committed).count() = 1L`; `jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, id) = winner.description()`; `jdbc.queryForObject("SELECT version FROM categories WHERE id = ?", Long.class, id) = 1L` |
| [cleanupExpiresOverdueAttemptAndPreservesCompletedAttempt](../src/test/java/org/courseflow/integration/orm/OrmPersistenceIT.java#L292) | Cleanup expires overdue attempt and preserves completed attempt. `jdbc.queryForObject( "SELECT status FROM quiz_submissions WHERE id = ?", String.class, expired) = "EXPIRED"`; `count( "SELECT count(*) FROM quiz_submissions WHERE id = ? AND finished_at = expires_at", expired) = 1L`; `jdbc.queryForObject( "SELECT status FROM quiz_submissions WHERE id = ?", String.class, q.attempt()) = "SUBMITTED"` |

#### [PostgresLockingIT](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [categoryRepositoryWriteLockBlocksConcurrentWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L40) | Category repository write lock blocks concurrent writer until commit |
| [categoryRepositorySharedLockBlocksWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L46) | Category repository shared lock blocks writer until commit |
| [courseRepositoryWriteLockBlocksConcurrentWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L52) | Course repository write lock blocks concurrent writer until commit |
| [courseRepositorySharedLockBlocksWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L58) | Course repository shared lock blocks writer until commit |
| [tagRepositoryWriteLockBlocksConcurrentWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L64) | Tag repository write lock blocks concurrent writer until commit |
| [tagRepositorySharedLockBlocksWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L70) | Tag repository shared lock blocks writer until commit |
| [userRepositoryWriteLockBlocksConcurrentWriterUntilCommit](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L76) | User repository write lock blocks concurrent writer until commit |
| [accountRepositoryLockBlocksConcurrentCredentialMutation](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L82) | Account repository lock blocks concurrent credential mutation |
| [submissionGradingRepositoryLockBlocksConcurrentWriter](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L90) | Submission grading repository lock blocks concurrent writer. HTTP 201 |
| [ownedQuizAttemptRepositoryLockBlocksConcurrentWriter](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L110) | Owned quiz attempt repository lock blocks concurrent writer |
| [latestQuizAttemptRepositoryLockBlocksConcurrentWriter](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L122) | Latest quiz attempt repository lock blocks concurrent writer |
| [twoCourseRepositorySharedLocksCanCoexist](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L137) | Two course repository shared locks can coexist. `firstLocked.await(10, TimeUnit.SECONDS)` — истина |
| [lockedCategoryDoesNotBlockUnrelatedRowOrMvccRead](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L175) | Locked category does not block unrelated row or mvcc read. `scalar(b, "SELECT id FROM categories WHERE id = ?", locked) = locked`; `update(b, "UPDATE categories SET description = 'Independent' WHERE id = ?", free) = 1` |
| [rowLockNowaitReturnsSqlState55P03](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L195) | Row lock nowait returns sql state 55 P 03. Исключение `SQLException`; `error.getSQLState() = "55P03"` |
| [lockTimeoutRejectsBlockedWriteAndRollbackRestoresConnection](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L214) | Lock timeout rejects blocked write and rollback restores connection. Исключение `SQLException`; `error.getSQLState() = "55P03"`; `update(b, "UPDATE categories SET description = 'Recovered' WHERE id = ?", id) = 1` |
| [tableAccessExclusiveLockRejectsConcurrentAccessShareNowait](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L242) | Table access exclusive lock rejects concurrent access share nowait. Исключение `SQLException`; `error.getSQLState() = "55P03"` |
| [tableShareLockAllowsReadButRejectsUpdate](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L263) | Table share lock allows read but rejects update. Исключение `SQLException`; `scalar(b, "SELECT id FROM categories WHERE id = ?", id) = id`; `error.getSQLState() = "55P03"` |
| [postgresDetectsRealDeadlockAndRollsBackExactlyOneTransaction](../src/test/java/org/courseflow/integration/orm/PostgresLockingIT.java#L284) | Postgres detects real deadlock and rolls back exactly one transaction. `results.stream().filter(r -> r.state().equals("COMMITTED")).count() = 1L`; `results.stream().filter(r -> r.state().equals("40P01")).count() = 1L`; `jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, a) = winner` |

#### [QueryEfficiencyIT](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [fetchJoinLoadsOptionsForOneHundredQuestionsWithOneStatement](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java#L48) | Загрузка вариантов 100 вопросов одним SQL-запросом. `loaded.size() = 100`; `question.getAnswerOptions().size() = 2`; `statements = 1L` |
| [questionManagementQueryCountDoesNotGrowWithQuestionCount](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java#L65) | Число запросов при чтении вопросов не зависит от их количества. `questionService .getQuestionsForManagement(small.quiz(), teacher.id(), false) .size() = 1`; `questionService .getQuestionsForManagement(large.quiz(), teacher.id(), false) .size() = 100`; `many <= one + 1` — истина |
| [userPageFetchesRoleCollectionsInBoundedQueries](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java#L89) | Загрузка ролей страницы пользователей ограниченным числом запросов. `userService .findAll(0, 1, null, null, UserRole.STUDENT, "id", "asc") .content() .size() = 1`; `page.content().size() = 33`; `user.roles().contains(UserRole.STUDENT)` — истина |
| [coursePageAvoidsPerCourseAssociationQueries](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java#L112) | Загрузка страницы курсов без отдельного запроса на каждый курс. HTTP 201; `courseQuery .findAll(0, 1, prefix, null, null, null, "id", "asc") .content() .size() = 1`; `page.content().size() = 33` |
| [lessonSummaryProjectionUsesAtMostTwoStatementsForPage](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java#L155) | Проекция уроков использует не более двух SQL-запросов на страницу. HTTP 201; `page.getContent().size() = 50`; `page.getTotalElements() = 100L` |
| [gradebookQueryCountDoesNotGrowPerStudentOrCell](../src/test/java/org/courseflow/integration/orm/QueryEfficiencyIT.java#L180) | Число запросов журнала не растёт на каждого студента или ячейку. HTTP 201; HTTP 200; `result.students().content().size() = 33` |

#### [TransactionIsolationIT](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [readCommittedNeverReadsUncommittedChanges](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L20) | READ COMMITTED исключает чтение незавершённых изменений. `description(reader, id) = "Original"` |
| [readCommittedSeesNewCommitOnNextStatement](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L37) | READ COMMITTED видит новое подтверждённое изменение при следующем запросе. `description(reader, id) = "Original"`; `description(reader, id) = "Committed"` |
| [repeatableReadKeepsSnapshotUntilTransactionEnds](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L54) | REPEATABLE READ сохраняет снимок до завершения транзакции. `description(reader, id) = "Original"`; `description(reader, id) = "Committed"` |
| [readCommittedCanObserveNewMatchingRows](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L73) | READ COMMITTED может видеть новые подходящие строки |
| [repeatableReadPreventsPhantomsInSameSnapshot](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L78) | REPEATABLE READ сохраняет набор строк в пределах снимка |
| [repeatableReadRejectsConcurrentUpdateOfStaleSnapshot](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L83) | REPEATABLE READ отклоняет конкурентное обновление устаревшего снимка. Исключение `SQLException`; `description(a, id) = "Original"`; `error.getSQLState() = "40001"` |
| [serializablePreventsWriteSkewAcrossDifferentRows](../src/test/java/org/courseflow/integration/orm/TransactionIsolationIT.java#L103) | SERIALIZABLE предотвращает аномалию write skew. `results.stream().filter("COMMITTED"::equals).count() = 1L`; `results.stream().filter("40001"::equals).count() = 1L`; `count( "SELECT count(*) FROM categories WHERE id IN (?, ?) AND description = 'Active'", first, second) = 1L` |

#### Восстановление и повторный запуск

#### [DatabaseAvailabilityIT](../src/test/java/org/courseflow/integration/recovery/DatabaseAvailabilityIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [exhaustedPoolRejectsDatabaseRequestAndRecoversWhenConnectionsReturn](../src/test/java/org/courseflow/integration/recovery/DatabaseAvailabilityIT.java#L46) | Исчерпание пула соединений и восстановление при возврате соединений. `pool.getMaximumPoolSize() = 3`; `pool.getHikariPoolMXBean().getActiveConnections() = 3`; `pool.getHikariPoolMXBean().getIdleConnections() = 0` |
| [unavailableDatabaseReportsNotReadyAndRecoversWithoutApplicationRestart](../src/test/java/org/courseflow/integration/recovery/DatabaseAvailabilityIT.java#L66) | Readiness при недоступности БД и восстановление без перезапуска. HTTP 200; `databaseConnections(maintenance) = 0L`; `expect(api("GET", coursePath, null, null), 200) = originalCourse` |
| [terminatedTransactionRollsBackUncommittedChangeAndPoolRemainsUsable](../src/test/java/org/courseflow/integration/recovery/DatabaseAvailabilityIT.java#L101) | Откат прерванной транзакции и дальнейшее использование пула. `update.executeUpdate() = 1`; `result.getString(1) = "Uncommitted change"`; `readCategory() = original` |

#### [ExistingDatabaseStartupIT](../src/test/java/org/courseflow/integration/recovery/ExistingDatabaseStartupIT.java)

Проверки Spring и PostgreSQL в тестовом контексте.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [secondStartupPreservesBusinessDataAndInvalidatesExistingSessions](../src/test/java/org/courseflow/integration/recovery/ExistingDatabaseStartupIT.java#L40) | Повторный запуск сохраняет учебные данные и отзывает прежние сессии. HTTP 201; HTTP 200; `jdbc.queryForObject("SELECT current_database()", String.class) = "courseflow_test"` |

#### [CourseFlowRegressionIT](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java) — 394 динамических сценария

Последовательность REST-запросов с сохранением идентификаторов и токенов между шагами. В каждом сценарии проверяется ожидаемый статус и, где предусмотрено, содержимое ответа или состояние объекта.

| Сценарий | Операция и ожидаемый результат |
|---|---|
| [001. Login admin — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L448) (`case001`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [002. System status (ADMIN) — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L472) (`case002`) | `GET /api/system/status`; HTTP 200 — успешное выполнение |
| [003. Anonymous actuator health — status only](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L488) (`case003`) | `GET /actuator/health`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `eq(field(response.body(), "status"), "UP")` (`"response.body.status === \"UP\""`); Вызов `looseEq(field(response.body(), "components"), null)` (`"Health must not disclose components"`) |
| [004. Allow configured React CORS preflight — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L500) (`case004`) | `OPTIONS /api/users/me`; HTTP 200 — успешное выполнение |
| [005. Reject untrusted CORS origin — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L518) (`case005`) | `OPTIONS /api/users/me`; HTTP 403 — отказ в доступе |
| [006. Register student — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L533) (`case006`) | `POST /api/auth/register`; HTTP 201 — создание объекта |
| [007. Duplicate registration — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L556) (`case007`) | `POST /api/auth/register`; HTTP 409 — конфликт |
| [008. Wrong password — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L573) (`case008`) | `POST /api/auth/login`; HTTP 401 — отказ в аутентификации |
| [009. Login student — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L598) (`case009`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [010. Own user profile — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L621) (`case010`) | `GET /api/users/me`; HTTP 200 — успешное выполнение |
| [011. Guest denied — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L633) (`case011`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [012. Invalid token denied — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L638) (`case012`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [013. Student cannot list users — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L644) (`case013`) | `GET /api/users`; HTTP 403 — отказ в доступе |
| [014. Student cannot read another user — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L658) (`case014`) | `GET /api/users/{{admin_id}}`; HTTP 403 — отказ в доступе |
| [015. Create teacher — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L665) (`case015`) | `POST /api/users`; HTTP 201 — создание объекта |
| [016. Login teacher — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L696) (`case016`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [017. Create temporary account — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L719) (`case017`) | `POST /api/users`; HTTP 201 — создание объекта |
| [018. Login other — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L749) (`case018`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [019. Admin reads user — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L772) (`case019`) | `GET /api/users/{{other_id}}`; HTTP 200 — успешное выполнение |
| [020. Admin filters and sorts users — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L785) (`case020`) | `GET /api/users?q={{run_id}}&status=ACTIVE&role=STUDENT&page=0&size=10&sortBy=displayName&direction=asc`; HTTP 200 — успешное выполнение |
| [021. Update temporary user — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L813) (`case021`) | `PUT /api/users/{{other_id}}`; HTTP 200 — успешное выполнение |
| [022. Email change revokes previous token — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L838) (`case022`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [023. Login other — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L843) (`case023`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [024. Assign temporary teacher role — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L866) (`case024`) | `PUT /api/users/{{other_id}}/roles`; HTTP 200 — успешное выполнение |
| [025. Role change revokes previous token — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L893) (`case025`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [026. Login other — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L898) (`case026`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [027. Reject empty roles — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L921) (`case027`) | `PUT /api/users/{{other_id}}/roles`; HTTP 400 — ошибка входных данных |
| [028. System admin cannot be deactivated — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L938) (`case028`) | `POST /api/users/{{admin_id}}/deactivate`; HTTP 409 — конфликт |
| [029. Change student password — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L949) (`case029`) | `POST /api/auth/change-password`; HTTP 204 — успех без тела ответа |
| [030. Password change revokes session — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L967) (`case030`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [031. Old password rejected — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L973) (`case031`) | `POST /api/auth/login`; HTTP 401 — отказ в аутентификации |
| [032. Login student — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L989) (`case032`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [033. Logout student — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1012) (`case033`) | `POST /api/auth/logout`; HTTP 204 — успех без тела ответа |
| [034. Logged out token rejected — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1018) (`case034`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [035. Login student — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1024) (`case035`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [036. Forgot password unknown email — 202](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1047) (`case036`) | `POST /api/auth/forgot-password`; HTTP 202 — запрос принят |
| [037. Reject invalid reset token — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1062) (`case037`) | `POST /api/auth/reset-password`; HTTP 400 — ошибка входных данных |
| [038. Admin reset nonexistent account — 404](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1078) (`case038`) | `POST /api/users/999999999/password-reset`; HTTP 404 — ресурс не найден |
| [039. Create category — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1089) (`case039`) | `POST /api/categories`; HTTP 201 — создание объекта |
| [040. Duplicate category — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1111) (`case040`) | `POST /api/categories`; HTTP 409 — конфликт |
| [041. Student cannot create category — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1129) (`case041`) | `POST /api/categories`; HTTP 403 — отказ в доступе |
| [042. Reject blank category name — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1147) (`case042`) | `POST /api/categories`; HTTP 400 — ошибка входных данных |
| [043. Public category list and search — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1174) (`case043`) | `GET /api/categories?search=Regression%20{{run_id}}&page=0&size=5&sortBy=name&direction=desc`; HTTP 200 — успешное выполнение |
| [044. Public category detail — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1189) (`case044`) | `GET /api/categories/{{category_id}}`; HTTP 200 — успешное выполнение |
| [045. Invalid page size — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1197) (`case045`) | `GET /api/categories?size=0`; HTTP 400 — ошибка входных данных |
| [046. Missing category — 404](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1202) (`case046`) | `GET /api/categories/999999999`; HTTP 404 — ресурс не найден |
| [047. Create tag — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1216) (`case047`) | `POST /api/tags`; HTTP 201 — создание объекта |
| [048. Update tag — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1237) (`case048`) | `PUT /api/tags/{{tag_id}}`; HTTP 200 — успешное выполнение |
| [049. Public tag detail — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1257) (`case049`) | `GET /api/tags/{{tag_id}}`; HTTP 200 — успешное выполнение |
| [050. Public tags — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1265) (`case050`) | `GET /api/tags?page=0&size=10&q=reg-updated-{{run_id}}`; HTTP 200 — успешное выполнение |
| [051. Create main course — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1273) (`case051`) | `POST /api/courses`; HTTP 201 — создание объекта |
| [052. Update main course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1305) (`case052`) | `PUT /api/courses/{{course_id}}`; HTTP 200 — успешное выполнение |
| [053. Public course detail — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1334) (`case053`) | `GET /api/courses/{{course_id}}`; HTTP 200 — успешное выполнение |
| [054. Course search treats percent literally — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1346) (`case054`) | `GET /api/courses?q=%25&categoryId={{category_id}}`; HTTP 200 — успешное выполнение |
| [055. Public filtered course list — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1352) (`case055`) | `GET /api/courses?q={{run_id}}&categoryId={{category_id}}&teacherId={{teacher_id}}&status=ACTIVE&page=0&size=10`; HTTP 200 — успешное выполнение |
| [056. Other teacher cannot edit course — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1366) (`case056`) | `PUT /api/courses/{{course_id}}`; HTTP 403 — отказ в доступе |
| [057. Create temporary course — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1388) (`case057`) | `POST /api/courses`; HTTP 201 — создание объекта |
| [058. Admin reassigns temporary course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1417) (`case058`) | `PUT /api/courses/{{temp_course_id}}/teacher`; HTTP 200 — успешное выполнение |
| [059. Archive temporary course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1441) (`case059`) | `POST /api/courses/{{temp_course_id}}/archive`; HTTP 200 — успешное выполнение |
| [060. Cannot enroll in archived course — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1454) (`case060`) | `POST /api/courses/{{temp_course_id}}/enrollments`; HTTP 409 — конфликт |
| [061. Delete empty archived course — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1465) (`case061`) | `DELETE /api/courses/{{temp_course_id}}`; HTTP 204 — успех без тела ответа |
| [062. Deleted course not found — 404](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1476) (`case062`) | `GET /api/courses/{{temp_course_id}}`; HTTP 404 — ресурс не найден |
| [063. Attach tag to course — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1481) (`case063`) | `POST /api/courses/{{course_id}}/tags/{{tag_id}}`; HTTP 204 — успех без тела ответа |
| [064. List attached tags — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1492) (`case064`) | `GET /api/courses/{{course_id}}/tags`; HTTP 200 — успешное выполнение |
| [065. Cannot delete attached tag — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1500) (`case065`) | `DELETE /api/tags/{{tag_id}}`; HTTP 409 — конфликт |
| [066. Detach tag — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1506) (`case066`) | `DELETE /api/courses/{{course_id}}/tags/{{tag_id}}`; HTTP 204 — успех без тела ответа |
| [067. Delete unused tag — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1517) (`case067`) | `DELETE /api/tags/{{tag_id}}`; HTTP 204 — успех без тела ответа |
| [068. Create module — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1523) (`case068`) | `POST /api/courses/{{course_id}}/modules`; HTTP 201 — создание объекта |
| [069. Reject duplicate module position — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1549) (`case069`) | `POST /api/courses/{{course_id}}/modules`; HTTP 409 — конфликт |
| [070. Create temporary module — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1568) (`case070`) | `POST /api/courses/{{course_id}}/modules`; HTTP 201 — создание объекта |
| [071. Update module — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1590) (`case071`) | `PUT /api/courses/{{course_id}}/modules/{{module_id}}`; HTTP 200 — успешное выполнение |
| [072. Reorder module — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1615) (`case072`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/position`; HTTP 200 — успешное выполнение |
| [073. Read module — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1637) (`case073`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}`; HTTP 200 — успешное выполнение |
| [074. List modules — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1649) (`case074`) | `GET /api/courses/{{course_id}}/modules`; HTTP 200 — успешное выполнение |
| [075. Create lesson — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1671) (`case075`) | `POST /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 201 — создание объекта |
| [076. Create temporary lesson — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1698) (`case076`) | `POST /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 201 — создание объекта |
| [077. Update lesson — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1720) (`case077`) | `PUT /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`; HTTP 200 — успешное выполнение |
| [078. Reject unsafe video URL — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1743) (`case078`) | `PUT /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`; HTTP 400 — ошибка входных данных |
| [079. Reject oversized lesson content — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1756) (`case079`) | `PUT /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`; HTTP 400 — ошибка входных данных |
| [080. Reorder lesson — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1770) (`case080`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}/position`; HTTP 200 — успешное выполнение |
| [081. Delete empty lesson — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1792) (`case081`) | `DELETE /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{temp_lesson_id}}`; HTTP 204 — успех без тела ответа |
| [082. Read lesson — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1803) (`case082`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`; HTTP 200 — успешное выполнение |
| [083. List lessons — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1826) (`case083`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 200 — успешное выполнение |
| [084. Unenrolled student cannot read lessons — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1853) (`case084`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 403 — отказ в доступе |
| [085. Create session — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1864) (`case085`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 201 — создание объекта |
| [086. Reject overlapping session — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1891) (`case086`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 409 — конфликт |
| [087. Update session — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1911) (`case087`) | `PUT /api/lessons/{{lesson_id}}/sessions/{{session_id}}`; HTTP 200 — успешное выполнение |
| [088. Reschedule session — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1938) (`case088`) | `PATCH /api/lessons/{{lesson_id}}/sessions/{{session_id}}/time`; HTTP 200 — успешное выполнение |
| [089. Session detail — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1963) (`case089`) | `GET /api/lessons/{{lesson_id}}/sessions/{{session_id}}`; HTTP 200 — успешное выполнение |
| [090. List sessions — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1977) (`case090`) | `GET /api/lessons/{{lesson_id}}/sessions`; HTTP 200 — успешное выполнение |
| [091. ONLINE session requires a link — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L1994) (`case091`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 400 — ошибка входных данных |
| [092. Reject unsafe meeting link — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2007) (`case092`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 400 — ошибка входных данных |
| [093. OFFLINE session must not have link — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2020) (`case093`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 400 — ошибка входных данных |
| [094. Create temporary offline session — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2033) (`case094`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 201 — создание объекта |
| [095. Delete temporary session — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2055) (`case095`) | `DELETE /api/lessons/{{lesson_id}}/sessions/{{temp_session_id}}`; HTTP 204 — успех без тела ответа |
| [096. Enroll student — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2066) (`case096`) | `POST /api/courses/{{course_id}}/enrollments`; HTTP 201 — создание объекта |
| [097. Repeat enrollment idempotently — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2086) (`case097`) | `POST /api/courses/{{course_id}}/enrollments`; HTTP 200 — успешное выполнение |
| [098. Teacher cannot enroll in own course — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2100) (`case098`) | `POST /api/courses/{{course_id}}/enrollments`; HTTP 409 — конфликт |
| [099. My enrollment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2111) (`case099`) | `GET /api/courses/{{course_id}}/enrollments/me`; HTTP 200 — успешное выполнение |
| [100. Course roster — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2125) (`case100`) | `GET /api/courses/{{course_id}}/enrollments?status=ACTIVE&page=0&size=10`; HTTP 200 — успешное выполнение |
| [101. My enrollments — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2142) (`case101`) | `GET /api/users/me/enrollments?status=ACTIVE`; HTTP 200 — успешное выполнение |
| [102. Student reads enrolled modules — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2159) (`case102`) | `GET /api/courses/{{course_id}}/modules`; HTTP 200 — успешное выполнение |
| [103. Student reads enrolled lessons — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2175) (`case103`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 200 — успешное выполнение |
| [104. Student reads schedule — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2191) (`case104`) | `GET /api/lessons/{{lesson_id}}/sessions`; HTTP 200 — успешное выполнение |
| [105. Student cannot read roster — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2208) (`case105`) | `GET /api/courses/{{course_id}}/enrollments`; HTTP 403 — отказ в доступе |
| [106. Other teacher cannot read roster — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2219) (`case106`) | `GET /api/courses/{{course_id}}/enrollments`; HTTP 403 — отказ в доступе |
| [107. Create assignment — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2230) (`case107`) | `POST /api/lessons/{{lesson_id}}/assignments`; HTTP 201 — создание объекта |
| [108. Update assignment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2264) (`case108`) | `PUT /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`; HTTP 200 — успешное выполнение |
| [109. Create second assignment — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2289) (`case109`) | `POST /api/lessons/{{lesson_id}}/assignments`; HTTP 201 — создание объекта |
| [110. Create temporary assignment — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2312) (`case110`) | `POST /api/lessons/{{lesson_id}}/assignments`; HTTP 201 — создание объекта |
| [111. Delete unused assignment — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2335) (`case111`) | `DELETE /api/lessons/{{lesson_id}}/assignments/{{temp_assignment_id}}`; HTTP 204 — успех без тела ответа |
| [112. Read assignment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2346) (`case112`) | `GET /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`; HTTP 200 — успешное выполнение |
| [113. List assignments — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2360) (`case113`) | `GET /api/lessons/{{lesson_id}}/assignments`; HTTP 200 — успешное выполнение |
| [114. Unenrolled user cannot submit — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2384) (`case114`) | `POST /api/assignments/{{assignment_id}}/submissions`; HTTP 403 — отказ в доступе |
| [115. Submit homework — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2401) (`case115`) | `POST /api/assignments/{{assignment_id}}/submissions`; HTTP 201 — создание объекта |
| [116. Reject duplicate homework — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2434) (`case116`) | `POST /api/assignments/{{assignment_id}}/submissions`; HTTP 409 — конфликт |
| [117. Read own submission — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2460) (`case117`) | `GET /api/assignments/{{assignment_id}}/submissions/me`; HTTP 200 — успешное выполнение |
| [118. Teacher lists pending work — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2474) (`case118`) | `GET /api/assignments/{{assignment_id}}/submissions?status=SUBMITTED`; HTTP 200 — успешное выполнение |
| [119. Student cannot list submissions — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2491) (`case119`) | `GET /api/assignments/{{assignment_id}}/submissions`; HTTP 403 — отказ в доступе |
| [120. Other teacher cannot list submissions — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2502) (`case120`) | `GET /api/assignments/{{assignment_id}}/submissions`; HTTP 403 — отказ в доступе |
| [121. Reject score above maximum — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2513) (`case121`) | `PUT /api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`; HTTP 400 — ошибка входных данных |
| [122. Teacher dashboard pending queue — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2531) (`case122`) | `GET /api/dashboard/teacher`; HTTP 200 — успешное выполнение |
| [123. Grade homework — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2558) (`case123`) | `PUT /api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`; HTTP 200 — успешное выполнение |
| [124. Cannot edit submitted assignment — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2582) (`case124`) | `PUT /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`; HTTP 409 — конфликт |
| [125. Cannot delete submitted assignment — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2602) (`case125`) | `DELETE /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`; HTTP 409 — конфликт |
| [126. Create quiz — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2613) (`case126`) | `POST /api/modules/{{module_id}}/quiz`; HTTP 201 — создание объекта |
| [127. Read quiz by module — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2641) (`case127`) | `GET /api/modules/{{module_id}}/quiz`; HTTP 200 — успешное выполнение |
| [128. Update quiz settings — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2655) (`case128`) | `PUT /api/quizzes/{{quiz_id}}`; HTTP 200 — успешное выполнение |
| [129. Read quiz — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2679) (`case129`) | `GET /api/quizzes/{{quiz_id}}`; HTTP 200 — успешное выполнение |
| [130. Reject second quiz in same module — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2689) (`case130`) | `POST /api/modules/{{module_id}}/quiz`; HTTP 409 — конфликт |
| [131. Create temporary quiz — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2707) (`case131`) | `POST /api/modules/{{temp_module_id}}/quiz`; HTTP 201 — создание объекта |
| [132. Cannot start empty quiz — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2728) (`case132`) | `POST /api/quizzes/{{temp_quiz_id}}/attempts`; HTTP 409 — конфликт |
| [133. Create single choice question — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2739) (`case133`) | `POST /api/quizzes/{{quiz_id}}/questions`; HTTP 201 — создание объекта |
| [134. Create multiple choice question — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2794) (`case134`) | `POST /api/quizzes/{{quiz_id}}/questions`; HTTP 201 — создание объекта |
| [135. Reject duplicate question position — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2849) (`case135`) | `POST /api/quizzes/{{quiz_id}}/questions`; HTTP 409 — конфликт |
| [136. Create temporary question — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2882) (`case136`) | `POST /api/quizzes/{{quiz_id}}/questions`; HTTP 201 — создание объекта |
| [137. Update temporary question — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2922) (`case137`) | `PUT /api/quizzes/{{quiz_id}}/questions/{{temp_question_id}}`; HTTP 200 — успешное выполнение |
| [138. Delete temporary question — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2962) (`case138`) | `DELETE /api/quizzes/{{quiz_id}}/questions/{{temp_question_id}}`; HTTP 204 — успех без тела ответа |
| [139. Create question in temporary quiz — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L2973) (`case139`) | `POST /api/quizzes/{{temp_quiz_id}}/questions`; HTTP 201 — создание объекта |
| [140. Delete unused quiz with questions — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3006) (`case140`) | `DELETE /api/quizzes/{{temp_quiz_id}}`; HTTP 204 — успех без тела ответа |
| [141. Delete empty temporary module — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3017) (`case141`) | `DELETE /api/courses/{{course_id}}/modules/{{temp_module_id}}`; HTTP 204 — успех без тела ответа |
| [142. Reject multiple choice without distractor — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3028) (`case142`) | `POST /api/quizzes/{{quiz_id}}/questions`; HTTP 400 — ошибка входных данных |
| [143. Teacher reads questions with answers — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3041) (`case143`) | `GET /api/quizzes/{{quiz_id}}/questions/manage`; HTTP 200 — успешное выполнение |
| [144. Questions unavailable before active attempt — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3083) (`case144`) | `GET /api/quizzes/{{quiz_id}}/questions`; HTTP 409 — конфликт |
| [145. Student cannot read answer keys — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3094) (`case145`) | `GET /api/quizzes/{{quiz_id}}/questions/manage`; HTTP 403 — отказ в доступе |
| [146. Start attempt 1 — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3105) (`case146`) | `POST /api/quizzes/{{quiz_id}}/attempts`; HTTP 201 — создание объекта |
| [147. Student reads questions without correct flags — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3130) (`case147`) | `GET /api/quizzes/{{quiz_id}}/questions`; HTTP 200 — успешное выполнение |
| [148. Progress identifies active quiz — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3157) (`case148`) | `GET /api/users/me/progress?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [149. Gradebook identifies active quiz — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3177) (`case149`) | `GET /api/courses/{{course_id}}/gradebook`; HTTP 200 — успешное выполнение |
| [150. Reject oversized quiz answer list — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3196) (`case150`) | `POST /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit`; HTTP 400 — ошибка входных данных |
| [151. Resume same attempt — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3210) (`case151`) | `POST /api/quizzes/{{quiz_id}}/attempts`; HTTP 200 — успешное выполнение |
| [152. Read ongoing attempt — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3224) (`case152`) | `GET /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}`; HTTP 200 — успешное выполнение |
| [153. Freeze quiz after attempt starts — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3238) (`case153`) | `PUT /api/quizzes/{{quiz_id}}`; HTTP 409 — конфликт |
| [154. Reject answer from another question — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3257) (`case154`) | `POST /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit`; HTTP 400 — ошибка входных данных |
| [155. Submit perfect attempt — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3281) (`case155`) | `POST /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit`; HTTP 200 — успешное выполнение |
| [156. Reject duplicate attempt submission — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3316) (`case156`) | `POST /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/submit`; HTTP 409 — конфликт |
| [157. Start second attempt — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3347) (`case157`) | `POST /api/quizzes/{{quiz_id}}/attempts`; HTTP 201 — создание объекта |
| [158. Submit partial result — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3362) (`case158`) | `POST /api/quizzes/{{quiz_id}}/attempts/{{attempt2_id}}/submit`; HTTP 200 — успешное выполнение |
| [159. Reject attempt above limit — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3394) (`case159`) | `POST /api/quizzes/{{quiz_id}}/attempts`; HTTP 409 — конфликт |
| [160. Student attempt history — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3405) (`case160`) | `GET /api/quizzes/{{quiz_id}}/attempts/me?status=SUBMITTED&page=0&size=10`; HTTP 200 — успешное выполнение |
| [161. Teacher quiz results — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3429) (`case161`) | `GET /api/quizzes/{{quiz_id}}/attempts?status=SUBMITTED`; HTTP 200 — успешное выполнение |
| [162. Own attempt detail — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3441) (`case162`) | `GET /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/result`; HTTP 200 — успешное выполнение |
| [163. Management attempt detail — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3458) (`case163`) | `GET /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/manage`; HTTP 200 — успешное выполнение |
| [164. Student cannot access management result — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3472) (`case164`) | `GET /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}/manage`; HTTP 403 — отказ в доступе |
| [165. Other student cannot read attempt — 404](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3483) (`case165`) | `GET /api/quizzes/{{quiz_id}}/attempts/{{attempt1_id}}`; HTTP 404 — ресурс не найден |
| [166. Questions unavailable after final attempt — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3494) (`case166`) | `GET /api/quizzes/{{quiz_id}}/questions`; HTTP 409 — конфликт |
| [167. Cannot delete quiz with attempts — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3505) (`case167`) | `DELETE /api/quizzes/{{quiz_id}}`; HTTP 409 — конфликт |
| [168. Create review — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3516) (`case168`) | `POST /api/courses/{{course_id}}/reviews`; HTTP 201 — создание объекта |
| [169. Reject duplicate review — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3543) (`case169`) | `POST /api/courses/{{course_id}}/reviews`; HTTP 409 — конфликт |
| [170. Update own review — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3561) (`case170`) | `PUT /api/courses/{{course_id}}/reviews/me`; HTTP 200 — успешное выполнение |
| [171. Read own review — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3580) (`case171`) | `GET /api/courses/{{course_id}}/reviews/me`; HTTP 200 — успешное выполнение |
| [172. Public course reviews — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3594) (`case172`) | `GET /api/courses/{{course_id}}/reviews?page=0&size=10`; HTTP 200 — успешное выполнение |
| [173. Public aggregate rating — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3608) (`case173`) | `GET /api/courses/{{course_id}}/reviews/rating`; HTTP 200 — успешное выполнение |
| [174. Delete own review — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3615) (`case174`) | `DELETE /api/courses/{{course_id}}/reviews/me`; HTTP 204 — успех без тела ответа |
| [175. Empty aggregate rating — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3626) (`case175`) | `GET /api/courses/{{course_id}}/reviews/rating`; HTTP 200 — успешное выполнение |
| [176. Restore review — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3634) (`case176`) | `POST /api/courses/{{course_id}}/reviews`; HTTP 201 — создание объекта |
| [177. Gradebook with both assessment types — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3652) (`case177`) | `GET /api/courses/{{course_id}}/gradebook?enrollmentStatus=ACTIVE&page=0&size=10`; HTTP 200 — успешное выполнение |
| [178. Admin filtered gradebook — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3691) (`case178`) | `GET /api/courses/{{course_id}}/gradebook?type=ASSIGNMENT&reviewStatus=GRADED&moduleId={{module_id}}`; HTTP 200 — успешное выполнение |
| [179. Student cannot read gradebook — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3708) (`case179`) | `GET /api/courses/{{course_id}}/gradebook`; HTTP 403 — отказ в доступе |
| [180. Other teacher cannot read gradebook — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3719) (`case180`) | `GET /api/courses/{{course_id}}/gradebook`; HTTP 403 — отказ в доступе |
| [181. Student progress — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3730) (`case181`) | `GET /api/users/me/progress?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [182. Student dashboard — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3760) (`case182`) | `GET /api/dashboard/student`; HTTP 200 — успешное выполнение |
| [183. Teacher dashboard graded work removed — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3821) (`case183`) | `GET /api/dashboard/teacher`; HTTP 200 — успешное выполнение |
| [184. Admin dashboard — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3866) (`case184`) | `GET /api/dashboard/admin`; HTTP 200 — успешное выполнение |
| [185. Student cannot access admin dashboard — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3904) (`case185`) | `GET /api/dashboard/admin`; HTTP 403 — отказ в доступе |
| [186. Student cannot access teacher dashboard — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3910) (`case186`) | `GET /api/dashboard/teacher`; HTTP 403 — отказ в доступе |
| [187. Admin without student role denied — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3916) (`case187`) | `GET /api/dashboard/student`; HTTP 403 — отказ в доступе |
| [188. Withdraw from course — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3922) (`case188`) | `DELETE /api/courses/{{course_id}}/enrollments/me`; HTTP 204 — успех без тела ответа |
| [189. Withdrawn student denied content — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3933) (`case189`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 403 — отказ в доступе |
| [190. Submission history retained — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3944) (`case190`) | `GET /api/assignments/{{assignment_id}}/submissions/me`; HTTP 200 — успешное выполнение |
| [191. Progress history retained — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3956) (`case191`) | `GET /api/users/me/progress?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [192. Withdrawn student in gradebook — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3979) (`case192`) | `GET /api/courses/{{course_id}}/gradebook?enrollmentStatus=WITHDRAWN`; HTTP 200 — успешное выполнение |
| [193. Dashboard excludes withdrawn course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L3999) (`case193`) | `GET /api/dashboard/student`; HTTP 200 — успешное выполнение |
| [194. Admin enrollment count updated — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4017) (`case194`) | `GET /api/dashboard/admin`; HTTP 200 — успешное выполнение |
| [195. Restore enrollment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4033) (`case195`) | `POST /api/courses/{{course_id}}/enrollments`; HTTP 200 — успешное выполнение |
| [196. Restored student access — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4048) (`case196`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 200 — успешное выполнение |
| [197. Restored admin count — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4064) (`case197`) | `GET /api/dashboard/admin`; HTTP 200 — успешное выполнение |
| [198. Cannot remove TEACHER with assigned course — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4075) (`case198`) | `PUT /api/users/{{teacher_id}}/roles`; HTTP 409 — конфликт |
| [199. Cannot remove STUDENT with active enrollment — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4088) (`case199`) | `PUT /api/users/{{student_id}}/roles`; HTTP 409 — конфликт |
| [200. Teacher cannot submit homework in own course — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4101) (`case200`) | `POST /api/assignments/{{assignment_id}}/submissions`; HTTP 409 — конфликт |
| [201. Other teacher enrolls as student — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4114) (`case201`) | `POST /api/courses/{{course_id}}/enrollments`; HTTP 201 — создание объекта |
| [202. Cannot reassign course to enrolled student — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4126) (`case202`) | `PUT /api/courses/{{course_id}}/teacher`; HTTP 409 — конфликт |
| [203. Cannot update course with enrolled student as teacher — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4139) (`case203`) | `PUT /api/courses/{{course_id}}`; HTTP 409 — конфликт |
| [204. Teacher cannot administratively withdraw student — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4161) (`case204`) | `DELETE /api/courses/{{course_id}}/enrollments/students/{{other_id}}`; HTTP 403 — отказ в доступе |
| [205. Student cannot administratively withdraw student — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4172) (`case205`) | `DELETE /api/courses/{{course_id}}/enrollments/students/{{other_id}}`; HTTP 403 — отказ в доступе |
| [206. Cannot delete course with history — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4183) (`case206`) | `DELETE /api/courses/{{course_id}}`; HTTP 409 — конфликт |
| [207. Cannot delete active account — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4194) (`case207`) | `DELETE /api/users/{{other_id}}`; HTTP 409 — конфликт |
| [208. Deactivate temporary account — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4201) (`case208`) | `POST /api/users/{{other_id}}/deactivate`; HTTP 200 — успешное выполнение |
| [209. Deactivation revokes JWT — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4214) (`case209`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [210. Login other — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4219) (`case210`) | `POST /api/auth/login`; HTTP 401 — отказ в аутентификации |
| [211. Cannot delete inactive user with active enrollment — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4235) (`case211`) | `DELETE /api/users/{{other_id}}`; HTTP 409 — конфликт |
| [212. Admin withdraws inactive student — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4242) (`case212`) | `DELETE /api/courses/{{course_id}}/enrollments/students/{{other_id}}`; HTTP 204 — успех без тела ответа |
| [213. Admin withdrawal is idempotent — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4253) (`case213`) | `DELETE /api/courses/{{course_id}}/enrollments/students/{{other_id}}`; HTTP 204 — успех без тела ответа |
| [214. Admin sees withdrawn enrollment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4264) (`case214`) | `GET /api/courses/{{course_id}}/enrollments?status=WITHDRAWN&page=0&size=10`; HTTP 200 — успешное выполнение |
| [215. Reactivate temporary account — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4283) (`case215`) | `POST /api/users/{{other_id}}/activate`; HTTP 200 — успешное выполнение |
| [216. Login other — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4295) (`case216`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [217. Deactivate before deletion — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4318) (`case217`) | `POST /api/users/{{other_id}}/deactivate`; HTTP 200 — успешное выполнение |
| [218. Delete inactive account — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4329) (`case218`) | `DELETE /api/users/{{other_id}}`; HTTP 204 — успех без тела ответа |
| [219. Deleted user preserved as archive — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4336) (`case219`) | `GET /api/users/{{other_id}}`; HTTP 200 — успешное выполнение |
| [220. Login other — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4343) (`case220`) | `POST /api/auth/login`; HTTP 401 — отказ в аутентификации |
| [221. Deleted account cannot be activated — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4359) (`case221`) | `POST /api/users/{{other_id}}/activate`; HTTP 409 — конфликт |
| [222. Final student progress — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4370) (`case222`) | `GET /api/users/me/progress?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [223. Unknown login email — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4387) (`case223`) | `POST /api/auth/login`; HTTP 401 — отказ в аутентификации |
| [224. Oversized login password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4398) (`case224`) | `POST /api/auth/login`; HTTP 400 — ошибка входных данных |
| [225. Oversized UTF-8 login password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4409) (`case225`) | `POST /api/auth/login`; HTTP 400 — ошибка входных данных |
| [226. Oversized current password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4420) (`case226`) | `POST /api/auth/change-password`; HTTP 400 — ошибка входных данных |
| [227. Unknown API denied — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4433) (`case227`) | `GET /api/regression-unknown-endpoint`; HTTP 403 — отказ в доступе |
| [228. HEAD administrative dashboard denied for student — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4444) (`case228`) | HTTP 403 — отказ в доступе |
| [229. Create mixed-case tag — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4450) (`case229`) | `POST /api/tags`; HTTP 201 — создание объекта |
| [230. Reject case-insensitive duplicate tag — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4467) (`case230`) | `POST /api/tags`; HTTP 409 — конфликт |
| [231. Delete invariant test tag — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4480) (`case231`) | `DELETE /api/tags/{{tag_id}}`; HTTP 204 — успех без тела ответа |
| [232. Update category — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4486) (`case232`) | `PUT /api/categories/{{category_id}}`; HTTP 200 — успешное выполнение |
| [233. Student cannot update category — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4502) (`case233`) | `PUT /api/categories/{{category_id}}`; HTTP 403 — отказ в доступе |
| [234. Cannot delete category referenced by course — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4515) (`case234`) | `DELETE /api/categories/{{category_id}}`; HTTP 409 — конфликт |
| [235. Create temporary category — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4526) (`case235`) | `POST /api/categories`; HTTP 201 — создание объекта |
| [236. Cannot rename category to existing name — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4543) (`case236`) | `PUT /api/categories/{{temp_category_id}}`; HTTP 409 — конфликт |
| [237. Delete unused category — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4556) (`case237`) | `DELETE /api/categories/{{temp_category_id}}`; HTTP 204 — успех без тела ответа |
| [238. Deleted category not found — 404](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4567) (`case238`) | `GET /api/categories/{{temp_category_id}}`; HTTP 404 — ресурс не найден |
| [239. Read empty own profile — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4572) (`case239`) | `GET /api/users/me/profile`; HTTP 200 — успешное выполнение |
| [240. Create own profile — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4584) (`case240`) | `PUT /api/users/me/profile`; HTTP 200 — успешное выполнение |
| [241. Read persisted profile — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4600) (`case241`) | `GET /api/users/me/profile`; HTTP 200 — успешное выполнение |
| [242. Reject unsafe avatar URL — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4612) (`case242`) | `PUT /api/users/me/profile`; HTTP 400 — ошибка входных данных |
| [243. Teacher has separate profile — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4625) (`case243`) | `GET /api/users/me/profile`; HTTP 200 — успешное выполнение |
| [244. Anonymous profile denied — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4635) (`case244`) | `GET /api/users/me/profile`; HTTP 401 — отказ в аутентификации |
| [245. Public teacher identity — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4640) (`case245`) | `GET /api/teachers/{{teacher_id}}`; HTTP 200 — успешное выполнение |
| [246. Non-teacher not published — 404](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4657) (`case246`) | `GET /api/teachers/{{student_id}}`; HTTP 404 — ресурс не найден |
| [247. My current assignments — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4662) (`case247`) | `GET /api/users/me/assignments?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [248. My pending assignments filter — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4688) (`case248`) | `GET /api/users/me/assignments?status=NOT_SUBMITTED&page=0&size=1`; HTTP 200 — успешное выполнение |
| [249. My graded submissions — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4705) (`case249`) | `GET /api/users/me/submissions?courseId={{course_id}}&status=GRADED`; HTTP 200 — успешное выполнение |
| [250. My quiz attempt history — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4725) (`case250`) | `GET /api/users/me/quiz-attempts?courseId={{course_id}}&status=SUBMITTED&page=0&size=1`; HTTP 200 — успешное выполнение |
| [251. Invalid page size rejected — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4748) (`case251`) | `GET /api/users/me/assignments?size=101`; HTTP 400 — ошибка входных данных |
| [252. Admin without student role denied history — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4759) (`case252`) | `GET /api/users/me/submissions`; HTTP 403 — отказ в доступе |
| [253. Other student cannot see submissions — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4766) (`case253`) | `GET /api/users/me/submissions?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [254. Other student cannot see quiz attempts — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4778) (`case254`) | `GET /api/users/me/quiz-attempts?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [255. Withdraw to verify global history — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4790) (`case255`) | `DELETE /api/courses/{{course_id}}/enrollments/me`; HTTP 204 — успех без тела ответа |
| [256. Current assignments hidden after withdrawal — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4801) (`case256`) | `GET /api/users/me/assignments?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [257. Submission history survives withdrawal — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4813) (`case257`) | `GET /api/users/me/submissions?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [258. Quiz history survives withdrawal — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4828) (`case258`) | `GET /api/users/me/quiz-attempts?courseId={{course_id}}`; HTTP 200 — успешное выполнение |
| [259. Restore enrollment after history checks — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4840) (`case259`) | `POST /api/courses/{{course_id}}/enrollments`; HTTP 200 — успешное выполнение |
| [260. Create course for archived dashboard check — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4851) (`case260`) | `POST /api/courses`; HTTP 201 — создание объекта |
| [261. Create second course module for schedule test — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4867) (`case261`) | `POST /api/courses/{{temp_course_id}}/modules`; HTTP 201 — создание объекта |
| [262. Create second course lesson for schedule test — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4883) (`case262`) | `POST /api/courses/{{temp_course_id}}/modules/{{temp_module_id}}/lessons`; HTTP 201 — создание объекта |
| [263. Reject teacher schedule overlap across courses — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4899) (`case263`) | `POST /api/lessons/{{temp_lesson_id}}/sessions`; HTTP 409 — конфликт |
| [264. Delete temporary schedule lesson — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4912) (`case264`) | `DELETE /api/courses/{{temp_course_id}}/modules/{{temp_module_id}}/lessons/{{temp_lesson_id}}`; HTTP 204 — успех без тела ответа |
| [265. Delete temporary schedule module — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4923) (`case265`) | `DELETE /api/courses/{{temp_course_id}}/modules/{{temp_module_id}}`; HTTP 204 — успех без тела ответа |
| [266. Enroll in course before archive — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4934) (`case266`) | `POST /api/courses/{{temp_course_id}}/enrollments`; HTTP 201 — создание объекта |
| [267. Archive course with active enrollment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4945) (`case267`) | `POST /api/courses/{{temp_course_id}}/archive`; HTTP 200 — успешное выполнение |
| [268. Dashboard excludes archived active enrollment — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4958) (`case268`) | `GET /api/dashboard/student`; HTTP 200 — успешное выполнение |
| [269. Withdraw from archived course — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4975) (`case269`) | `DELETE /api/courses/{{temp_course_id}}/enrollments/me`; HTTP 204 — успех без тела ответа |
| [270. OpenAPI describes every REST operation — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L4986) (`case270`) | `GET /v3/api-docs`; HTTP 200 — успешное выполнение |
| [271. Swagger UI static page — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5227) (`case271`) | `GET /swagger-ui/index.html`; HTTP 200 — успешное выполнение |
| [272. Swagger configuration — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5235) (`case272`) | `GET /v3/api-docs/swagger-config`; HTTP 200 — успешное выполнение |
| [273. Canonical category search parameter q — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5240) (`case273`) | `GET /api/categories?q=Regression%20updated%20{{run_id}}&page=0&size=5&sortBy=name&direction=asc`; HTTP 200 — успешное выполнение |
| [274. Reject unsupported login content type — 415](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5255) (`case274`) | `POST /api/auth/login`; HTTP 415 — неподдерживаемый формат |
| [275. Reject malformed JSON — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5266) (`case275`) | `POST /api/auth/login`; HTTP 400 — ошибка входных данных |
| [276. Admin creates account with temporary password — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5278) (`case276`) | `POST /api/users`; HTTP 201 — создание объекта |
| [277. Login requires password change — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5291) (`case277`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [278. Restricted JWT cannot access own user endpoint — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5308) (`case278`) | `GET /api/users/me`; HTTP 403 — отказ в доступе |
| [279. Restricted JWT cannot use STUDENT dashboard — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5314) (`case279`) | `GET /api/dashboard/student`; HTTP 403 — отказ в доступе |
| [280. Temporary password cannot be reused — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5325) (`case280`) | `POST /api/auth/change-password`; HTTP 400 — ошибка входных данных |
| [281. Change temporary password — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5338) (`case281`) | `POST /api/auth/change-password`; HTTP 204 — успех без тела ответа |
| [282. Restricted JWT revoked after change — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5351) (`case282`) | `GET /api/users/me`; HTTP 401 — отказ в аутентификации |
| [283. Login with new password — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5357) (`case283`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [284. Full access restored — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5374) (`case284`) | `GET /api/users/me`; HTTP 200 — успешное выполнение |
| [285. Get current version of main course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5381) (`case285`) | `GET /api/courses/{{course_id}}`; HTTP 200 — успешное выполнение |
| [286. Update using current version — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5392) (`case286`) | `PUT /api/courses/{{course_id}}`; HTTP 200 — успешное выполнение |
| [287. Reject stale version — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5411) (`case287`) | `PUT /api/courses/{{course_id}}`; HTTP 409 — конфликт |
| [288. Reject missing version — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5427) (`case288`) | `PUT /api/courses/{{course_id}}`; HTTP 400 — ошибка входных данных |
| [289. Verify stale update did not overwrite course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5440) (`case289`) | `GET /api/courses/{{course_id}}`; HTTP 200 — успешное выполнение |
| [290. Category case-insensitive duplicate — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5448) (`case290`) | `POST /api/categories`; HTTP 409 — конфликт |
| [291. Category stale update — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5461) (`case291`) | `PUT /api/categories/{{category_id}}`; HTTP 409 — конфликт |
| [292. Category missing version — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5477) (`case292`) | `PUT /api/categories/{{category_id}}`; HTTP 400 — ошибка входных данных |
| [293. Category remains unchanged — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5490) (`case293`) | `GET /api/categories/{{category_id}}`; HTTP 200 — успешное выполнение |
| [294. Create version test tag — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5501) (`case294`) | `POST /api/tags`; HTTP 201 — создание объекта |
| [295. Tag update with valid version — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5521) (`case295`) | `PUT /api/tags/{{version_test_tag_id}}`; HTTP 200 — успешное выполнение |
| [296. Stale tag update — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5537) (`case296`) | `PUT /api/tags/{{version_test_tag_id}}`; HTTP 409 — конфликт |
| [297. Tag update missing version — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5553) (`case297`) | `PUT /api/tags/{{version_test_tag_id}}`; HTTP 400 — ошибка входных данных |
| [298. Tag name remains unchanged — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5566) (`case298`) | `GET /api/tags/{{version_test_tag_id}}`; HTTP 200 — успешное выполнение |
| [299. Delete version test tag — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5574) (`case299`) | `DELETE /api/tags/{{version_test_tag_id}}`; HTTP 204 — успех без тела ответа |
| [300. Read quiz history sorted by effective status — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5585) (`case300`) | `GET /api/quizzes/{{quiz_id}}/attempts/me?status=SUBMITTED&sortBy=status&direction=asc&page=0&size=10`; `GET /api/quizzes/{{quiz_id}}/attempts/me?status=IN_PROGRESS&page=0&size=10`; HTTP 200 — успешное выполнение |
| [301. Read quiz history sorted by effective finishedAt — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5614) (`case301`) | `GET /api/quizzes/{{quiz_id}}/attempts/me?sortBy=finishedAt&direction=desc&page=0&size=10`; HTTP 200 — успешное выполнение |
| [302. Cannot deactivate teacher with active courses — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5633) (`case302`) | `POST /api/users/{{teacher_id}}/deactivate`; HTTP 409 — конфликт |
| [303. Teacher remains active — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5644) (`case303`) | `GET /api/users/{{teacher_id}}`; HTTP 200 — успешное выполнение |
| [304. Cannot archive course with future session — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5652) (`case304`) | `POST /api/courses/{{course_id}}/archive`; HTTP 409 — конфликт |
| [305. Course remains active — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5663) (`case305`) | `GET /api/courses/{{course_id}}`; HTTP 200 — успешное выполнение |
| [306. Cannot change course teacher through ordinary update — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5674) (`case306`) | `PUT /api/courses/{{course_id}}`; HTTP 409 — конфликт |
| [307. Cannot create session in past — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5690) (`case307`) | `POST /api/lessons/{{lesson_id}}/sessions`; HTTP 400 — ошибка входных данных |
| [308. Read session version for date validation — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5703) (`case308`) | `GET /api/lessons/{{lesson_id}}/sessions/{{session_id}}`; HTTP 200 — успешное выполнение |
| [309. Cannot update session into past — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5717) (`case309`) | `PUT /api/lessons/{{lesson_id}}/sessions/{{session_id}}`; HTTP 400 — ошибка входных данных |
| [310. Cannot reschedule session into past — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5730) (`case310`) | `PATCH /api/lessons/{{lesson_id}}/sessions/{{session_id}}/time`; HTTP 400 — ошибка входных данных |
| [311. Rejected changes preserve session — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5743) (`case311`) | `GET /api/lessons/{{lesson_id}}/sessions/{{session_id}}`; HTTP 200 — успешное выполнение |
| [312. Create module at position 3 — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5760) (`case312`) | `POST /api/courses/{{course_id}}/modules`; HTTP 201 — создание объекта |
| [313. Create module at position 4 — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5777) (`case313`) | `POST /api/courses/{{course_id}}/modules`; HTTP 201 — создание объекта |
| [314. Read current module position and version — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5794) (`case314`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}`; HTTP 200 — успешное выполнение |
| [315. Move module forward with shift — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5809) (`case315`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/position`; HTTP 200 — успешное выполнение |
| [316. Check module positions after forward move — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5826) (`case316`) | `GET /api/courses/{{course_id}}/modules?page=0&size=100`; HTTP 200 — успешное выполнение |
| [317. Move module backward with shift — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5861) (`case317`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/position`; HTTP 200 — успешное выполнение |
| [318. Check module positions after backward move — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5878) (`case318`) | `GET /api/courses/{{course_id}}/modules?page=0&size=100`; HTTP 200 — успешное выполнение |
| [319. Reject stale version of shifted module — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5913) (`case319`) | `PATCH /api/courses/{{course_id}}/modules/{{cf614_module3_id}}/position`; HTTP 409 — конфликт |
| [320. Delete temporary module at position 3 — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5926) (`case320`) | `DELETE /api/courses/{{course_id}}/modules/{{cf614_module3_id}}`; HTTP 204 — успех без тела ответа |
| [321. Delete temporary module at position 4 — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5937) (`case321`) | `DELETE /api/courses/{{course_id}}/modules/{{cf614_module4_id}}`; HTTP 204 — успех без тела ответа |
| [322. Create lesson at position 3 — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5948) (`case322`) | `POST /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 201 — создание объекта |
| [323. Create lesson at position 4 — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5965) (`case323`) | `POST /api/courses/{{course_id}}/modules/{{module_id}}/lessons`; HTTP 201 — создание объекта |
| [324. Read current lesson position and version — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5982) (`case324`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`; HTTP 200 — успешное выполнение |
| [325. Move lesson forward with shift — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L5997) (`case325`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}/position`; HTTP 200 — успешное выполнение |
| [326. Check lesson positions after forward move — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6014) (`case326`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons?page=0&size=100`; HTTP 200 — успешное выполнение |
| [327. Move lesson backward with shift — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6049) (`case327`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}/position`; HTTP 200 — успешное выполнение |
| [328. Check lesson positions after backward move — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6066) (`case328`) | `GET /api/courses/{{course_id}}/modules/{{module_id}}/lessons?page=0&size=100`; HTTP 200 — успешное выполнение |
| [329. Reject stale version of shifted lesson — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6101) (`case329`) | `PATCH /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{cf614_lesson3_id}}/position`; HTTP 409 — конфликт |
| [330. Delete temporary lesson at position 3 — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6114) (`case330`) | `DELETE /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{cf614_lesson3_id}}`; HTTP 204 — успех без тела ответа |
| [331. Delete temporary lesson at position 4 — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6125) (`case331`) | `DELETE /api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{cf614_lesson4_id}}`; HTTP 204 — успех без тела ответа |
| [332. Gradebook first column page — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6136) (`case332`) | `GET /api/courses/{{course_id}}/gradebook?columnPage=0&columnSize=1`; HTTP 200 — успешное выполнение |
| [333. Gradebook second column page — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6171) (`case333`) | `GET /api/courses/{{course_id}}/gradebook?columnPage=1&columnSize=1`; HTTP 200 — успешное выполнение |
| [334. Gradebook quiz column page — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6209) (`case334`) | `GET /api/courses/{{course_id}}/gradebook?columnPage=2&columnSize=1`; HTTP 200 — успешное выполнение |
| [335. Gradebook column page out of range — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6236) (`case335`) | `GET /api/courses/{{course_id}}/gradebook?columnPage=3&columnSize=1`; HTTP 200 — успешное выполнение |
| [336. Gradebook filter counts only quiz columns — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6263) (`case336`) | `GET /api/courses/{{course_id}}/gradebook?type=QUIZ&columnSize=1`; HTTP 200 — успешное выполнение |
| [337. Reject invalid gradebook column size — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6282) (`case337`) | `GET /api/courses/{{course_id}}/gradebook?columnSize=101`; HTTP 400 — ошибка входных данных |
| [338. Reject negative gradebook column page — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6293) (`case338`) | `GET /api/courses/{{course_id}}/gradebook?columnPage=-1`; HTTP 400 — ошибка входных данных |
| [339. Progress list returns summaries — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6304) (`case339`) | `GET /api/users/me/progress`; HTTP 200 — успешное выполнение |
| [340. Progress details first page — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6331) (`case340`) | `GET /api/users/me/progress?courseId={{course_id}}&detailPage=0&detailSize=1`; HTTP 200 — успешное выполнение |
| [341. Progress details second page preserves totals — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6362) (`case341`) | `GET /api/users/me/progress?courseId={{course_id}}&detailPage=1&detailSize=1`; HTTP 200 — успешное выполнение |
| [342. Progress details out of range preserve totals — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6389) (`case342`) | `GET /api/users/me/progress?courseId={{course_id}}&detailPage=2&detailSize=1`; HTTP 200 — успешное выполнение |
| [343. Progress explicit summary for course — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6410) (`case343`) | `GET /api/users/me/progress?courseId={{course_id}}&includeDetails=false`; HTTP 200 — успешное выполнение |
| [344. Reject unscoped progress details — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6430) (`case344`) | `GET /api/users/me/progress?includeDetails=true`; HTTP 400 — ошибка входных данных |
| [345. Reject invalid progress detail size — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6441) (`case345`) | `GET /api/users/me/progress?courseId={{course_id}}&detailSize=101`; HTTP 400 — ошибка входных данных |
| [346. Reject negative progress detail page — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6452) (`case346`) | `GET /api/users/me/progress?courseId={{course_id}}&detailPage=-1`; HTTP 400 — ошибка входных данных |
| [347. Progress detail page overflow rejected — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6463) (`case347`) | `GET /api/users/me/progress?courseId={{course_id}}&detailPage=2147483647&detailSize=100`; HTTP 400 — ошибка входных данных |
| [348. Anonymous liveness](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6474) (`case348`) | `GET /actuator/health/liveness`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `eq(field(response.body(), "status"), "UP")` (`"response.body.status === \"UP\""`); Вызов `looseEq(field(response.body(), "components"), null)` (`"Probe must not disclose components"`) |
| [349. Anonymous readiness](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6483) (`case349`) | `GET /actuator/health/readiness`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `eq(field(response.body(), "status"), "UP")` (`"response.body.status === \"UP\""`); Вызов `looseEq(field(response.body(), "components"), null)` (`"Probe must not disclose components"`) |
| [350. Health component details denied](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6492) (`case350`) | `GET /actuator/health/db`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 401)` (`plus("Expected 401, got ", response.status())`) |
| [351. Anonymous metrics denied](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6497) (`case351`) | `GET /actuator/metrics`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 401)` (`plus("Expected 401, got ", response.status())`) |
| [352. Student metrics denied](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6502) (`case352`) | `GET /actuator/metrics`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 403)` (`plus("Expected 403, got ", response.status())`) |
| [353. Anonymous individual metric denied](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6508) (`case353`) | `GET /actuator/metrics/courseflow.auth.rate_limited`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 401)` (`plus("Expected 401, got ", response.status())`) |
| [354. Admin metrics names](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6513) (`case354`) | `GET /actuator/metrics`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `truth(isArray(field(response.body(), "names")))` (`"Array.isArray(response.body.names)"`); Вызов `ge(indexOf(field(response.body(), "names"), name), 0)` (`plus("Missing ", name)`) |
| [355. Admin counter courseflow.password.reset.requests](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6533) (`case355`) | `GET /actuator/metrics/courseflow.password.reset.requests`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `eq(field(response.body(), "…"), "…")` (`"…"`); Вызов `((truth(count) && eq(typeOf(field(count, "value")), "number")) && ge(field(count, "value"), 1))` (`"Invalid counter"`) |
| [356. Admin counter courseflow.password.reset.accepted](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6557) (`case356`) | `GET /actuator/metrics/courseflow.password.reset.accepted`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `eq(field(response.body(), "…"), "…")` (`"…"`); Вызов `((truth(count) && eq(typeOf(field(count, "value")), "number")) && ge(field(count, "value"), 1))` (`"Invalid counter"`) |
| [357. Admin counter courseflow.auth.rate_limited](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6581) (`case357`) | `GET /actuator/metrics/courseflow.auth.rate_limited`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 200)` (`plus("Expected 200, got ", response.status())`); Вызов `eq(field(response.body(), "name"), "courseflow.auth.rate_limited")` (`"response.body.name === \"courseflow.auth.rate_limited\""`); Вызов `((truth(count) && eq(typeOf(field(count, "value")), "number")) && ge(field(count, "value"), 0))` (`"Invalid counter"`) |
| [358. Admin environment endpoint denied](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6605) (`case358`) | `GET /actuator/env`; Проверка контракта и состояния ответа; Вызов `eq(response.status(), 403)` (`plus("Expected 403, got ", response.status())`) |
| [359. Register rejects 74 UTF-8 bytes — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6610) (`case359`) | `POST /api/auth/register`; HTTP 400 — ошибка входных данных |
| [360. Admin create rejects 74 UTF-8 bytes — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6627) (`case360`) | `POST /api/users`; HTTP 400 — ошибка входных данных |
| [361. Login rejects 74 UTF-8 bytes — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6646) (`case361`) | `POST /api/auth/login`; HTTP 400 — ошибка входных данных |
| [362. Change rejects oversized current password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6663) (`case362`) | `POST /api/auth/change-password`; HTTP 400 — ошибка входных данных |
| [363. Change rejects oversized new password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6682) (`case363`) | `POST /api/auth/change-password`; HTTP 400 — ошибка входных данных |
| [364. Reset rejects oversized new password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6701) (`case364`) | `POST /api/auth/reset-password`; HTTP 400 — ошибка входных данных |
| [365. Register accepts exactly 72 UTF-8 bytes — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6718) (`case365`) | `POST /api/auth/register`; HTTP 201 — создание объекта |
| [366. Admin cannot create same normalized email — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6742) (`case366`) | `POST /api/users`; HTTP 409 — конфликт |
| [367. Login accepts exactly 72 UTF-8 bytes — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6758) (`case367`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [368. Deactivate UTF8 test user — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6775) (`case368`) | `POST /api/users/{{cf618_user_id}}/deactivate`; HTTP 200 — успешное выполнение |
| [369. Clean up UTF8 test user — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6788) (`case369`) | `DELETE /api/users/{{cf618_user_id}}`; HTTP 204 — успех без тела ответа |
| [370. Missing submission filter retains existing behavior — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6799) (`case370`) | `GET /api/courses/{{course_id}}/gradebook?type=ASSIGNMENT&reviewStatus=NOT_SUBMITTED&enrollmentStatus=ACTIVE&moduleId={{module_id}}`; HTTP 200 — успешное выполнение |
| [371. Future unsubmitted assignments are not overdue — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6823) (`case371`) | `GET /api/courses/{{course_id}}/gradebook?type=ASSIGNMENT&reviewStatus=OVERDUE&moduleId={{module_id}}`; HTTP 200 — успешное выполнение |
| [372. ADMIN may filter overdue assignments — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6841) (`case372`) | `GET /api/courses/{{course_id}}/gradebook?reviewStatus=OVERDUE`; HTTP 200 — успешное выполнение |
| [373. Overdue assignment filter cannot be applied to quiz-only gradebook — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6856) (`case373`) | `GET /api/courses/{{course_id}}/gradebook?type=QUIZ&reviewStatus=OVERDUE`; HTTP 400 — ошибка входных данных |
| [374. Student cannot use overdue gradebook filter — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6870) (`case374`) | `GET /api/courses/{{course_id}}/gradebook?reviewStatus=OVERDUE`; HTTP 403 — отказ в доступе |
| [375. Student cannot extend deadline — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6881) (`case375`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 403 — отказ в доступе |
| [376. Deadline extension requires version — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6894) (`case376`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 400 — ошибка входных данных |
| [377. Cannot shorten deadline — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6907) (`case377`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 400 — ошибка входных данных |
| [378. Cannot extend into the past — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6920) (`case378`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 400 — ошибка входных данных |
| [379. Teacher extends deadline after grading — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6933) (`case379`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 200 — успешное выполнение |
| [380. Stale deadline update rejected — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6961) (`case380`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 409 — конфликт |
| [381. Admin extends deadline — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6974) (`case381`) | `PATCH /api/lessons/{{lesson_id}}/assignments/{{assignment_id}}/deadline`; HTTP 200 — успешное выполнение |
| [382. Existing grade unchanged after deadline extension — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L6994) (`case382`) | `GET /api/assignments/{{assignment_id}}/submissions/me`; HTTP 200 — успешное выполнение |
| [383. Teacher regrades an already graded submission — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7016) (`case383`) | `PUT /api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`; HTTP 200 — успешное выполнение |
| [384. Stale regrade cannot overwrite committed grade — 409](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7039) (`case384`) | `PUT /api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`; HTTP 409 — конфликт |
| [385. Student cannot regrade — 403](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7055) (`case385`) | `PUT /api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`; HTTP 403 — отказ в доступе |
| [386. Rejected grades do not modify submission — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7071) (`case386`) | `GET /api/assignments/{{assignment_id}}/submissions/me`; HTTP 200 — успешное выполнение |
| [387. Admin restores original grade — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7089) (`case387`) | `PUT /api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`; HTTP 200 — успешное выполнение |
| [388. Register temporary authorization test user — 201](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7108) (`case388`) | `POST /api/auth/register`; HTTP 201 — создание объекта |
| [389. Login temporary authorization test user — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7122) (`case389`) | `POST /api/auth/login`; HTTP 200 — успешное выполнение |
| [390. Active user with wrong current password — 400](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7134) (`case390`) | `POST /api/auth/change-password`; HTTP 400 — ошибка входных данных |
| [391. Deactivate temporary authorization test user — 200](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7150) (`case391`) | `POST /api/users/{{cf623_auth_id}}/deactivate`; HTTP 200 — успешное выполнение |
| [392. Inactive user cannot change password with revoked JWT — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7163) (`case392`) | `POST /api/auth/change-password`; HTTP 401 — отказ в аутентификации |
| [393. Inactive user cannot log in with correct password — 401](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7182) (`case393`) | `POST /api/auth/login`; HTTP 401 — отказ в аутентификации |
| [394. Delete temporary authorization test user — 204](../src/test/java/org/courseflow/integration/regression/CourseFlowRegressionIT.java#L7196) (`case394`) | `DELETE /api/users/{{cf623_auth_id}}`; HTTP 204 — успех без тела ответа |

#### [RateLimitRegressionIT](../src/test/java/org/courseflow/integration/regression/RateLimitRegressionIT.java) — 59 динамических сценариев

Проверка накопления лимитов регистрации, входа, смены и восстановления пароля. Все случаи с 429 проверяют код `RATE_LIMIT_EXCEEDED` и диапазон `Retry-After`.

| Название сценария | Ожидаемый результат |
|---|---|
| Register disposable user | Регистрация тестового пользователя — HTTP 201 |
| Register duplicate 2 | Повторная регистрация — HTTP 409 |
| Register duplicate 3 | Повторная регистрация — HTTP 409 |
| Register duplicate 4 | Повторная регистрация — HTTP 409 |
| Register duplicate 5 | Повторная регистрация — HTTP 409 |
| Register duplicate 6 | Повторная регистрация — HTTP 409 |
| Register duplicate 7 | Повторная регистрация — HTTP 409 |
| Register duplicate 8 | Повторная регистрация — HTTP 409 |
| Register duplicate 9 | Повторная регистрация — HTTP 409 |
| Register duplicate 10 | Повторная регистрация — HTTP 409 |
| Registration IP limit | Лимит регистрации по IP — HTTP 429 |
| Successful login 1 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 2 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 3 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 4 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 5 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 6 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 7 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 8 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 9 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 10 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 11 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 12 | Успешный вход и выдача токена — HTTP 200 |
| Successful login 13 | Успешный вход и выдача токена — HTTP 200 |
| Failed login 1 | Неверный пароль — HTTP 401 |
| Failed login 2 | Неверный пароль — HTTP 401 |
| Failed login 3 | Неверный пароль — HTTP 401 |
| Failed login 4 | Неверный пароль — HTTP 401 |
| Failed login 5 | Неверный пароль — HTTP 401 |
| Failed login 6 | Неверный пароль — HTTP 401 |
| Failed login 7 | Неверный пароль — HTTP 401 |
| Failed login 8 | Неверный пароль — HTTP 401 |
| Failed login 9 | Неверный пароль — HTTP 401 |
| Failed login 10 | Неверный пароль — HTTP 401 |
| Failed login 11 | Неверный пароль — HTTP 401 |
| Failed login 12 | Неверный пароль — HTTP 401 |
| Login email failure limit | Лимит ошибочных входов по email — HTTP 429 |
| Wrong current password 1 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 2 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 3 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 4 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 5 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 6 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 7 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 8 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 9 | Неверный текущий пароль — HTTP 400 |
| Wrong current password 10 | Неверный текущий пароль — HTTP 400 |
| Change password user limit | Лимит смены пароля — HTTP 429 |
| Unknown email recovery 1 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 2 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 3 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 4 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 5 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 6 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 7 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 8 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 9 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Unknown email recovery 10 | Запрос восстановления неизвестного аккаунта — HTTP 202 |
| Forgot password IP limit | Лимит восстановления по IP — HTTP 429 |

### 8.13. Нагрузочные тесты (14)

#### [AdvancedPerformanceIT](../src/test/java/org/courseflow/performance/AdvancedPerformanceIT.java)

Нагрузочные сценарии с параллельными HTTP-клиентами; формирование CSV-отчётов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [steppedRampUp](../src/test/java/org/courseflow/performance/AdvancedPerformanceIT.java#L213) | Ступенчатый рост нагрузки |
| [saturationWithoutThinkTime](../src/test/java/org/courseflow/performance/AdvancedPerformanceIT.java#L229) | Насыщение системы запросами без пауз |
| [stagedSpikeAndRecovery](../src/test/java/org/courseflow/performance/AdvancedPerformanceIT.java#L237) | Резкий всплеск нагрузки и восстановление |
| [extendedSoak](../src/test/java/org/courseflow/performance/AdvancedPerformanceIT.java#L245) | Продолжительное нагрузочное испытание |
| [authenticationBurstExercisesRealRateLimiter](../src/test/java/org/courseflow/performance/AdvancedPerformanceIT.java#L256) | Всплеск аутентификации с действующим ограничителем запросов. HTTP 200; `field(response.body(), "code") = "RATE_LIMIT_EXCEEDED"`; `number(response.body(), "status") = 429L` |

#### [PerformanceIT](../src/test/java/org/courseflow/performance/PerformanceIT.java)

Нагрузочные сценарии с параллельными HTTP-клиентами; формирование CSV-отчётов.

| Метод | Сценарий и проверяемый результат |
|---|---|
| [catalogWithOneHundredUsers](../src/test/java/org/courseflow/performance/PerformanceIT.java#L192) | Чтение каталога 100 пользователями |
| [lessonsWithOneHundredUsers](../src/test/java/org/courseflow/performance/PerformanceIT.java#L212) | Чтение уроков 100 пользователями |
| [fiftyConcurrentEnrollments](../src/test/java/org/courseflow/performance/PerformanceIT.java#L225) | 50 одновременных записей на курс. `count( "SELECT count(DISTINCT student_id) FROM enrollments " + "WHERE course_id = ? AND status = 'ACTIVE'", course) = 50L`; `count( "SELECT count(*) FROM enrollments WHERE course_id = ? AND student_id = ? AND status = 'ACTIVE'", course, actor.id()) = 1L` |
| [fiftyConcurrentHomeworkSubmissions](../src/test/java/org/courseflow/performance/PerformanceIT.java#L258) | 50 одновременных отправок домашних работ. `count( "SELECT count(DISTINCT student_id) FROM submissions WHERE assignment_id = ?", assignment) = 50L` |
| [fiftyConcurrentQuizWorkflows](../src/test/java/org/courseflow/performance/PerformanceIT.java#L293) | 50 параллельных сценариев прохождения теста. HTTP 201 |
| [twentyTeachersReadGradebooks](../src/test/java/org/courseflow/performance/PerformanceIT.java#L359) | 20 преподавателей читают электронные журналы |
| [mixedLoadWithOneHundredUsers](../src/test/java/org/courseflow/performance/PerformanceIT.java#L379) | Смешанная нагрузка от 100 пользователей |
| [mixedReadAndWriteBurstWithOneHundredUsers](../src/test/java/org/courseflow/performance/PerformanceIT.java#L434) | Всплеск чтения и записи от 100 пользователей. HTTP 201; `count("SELECT count(*) FROM submissions WHERE assignment_id = ?", assignment) = 50L`; `jdbc.queryForObject( "SELECT score FROM submissions WHERE id = ?", Integer.class, gradingSubmissions.get(i)) = 60 + i` |
| [sustainedLoadWithFiftyStudents](../src/test/java/org/courseflow/performance/PerformanceIT.java#L540) | Продолжительная нагрузка от 50 студентов. `seconds >= 1 && seconds <= 1800` — истина |

### 8.14. Frontend: модульные, архитектурные и браузерные тесты

#### Vitest

**[calendar.test.ts](../frontend/tests/calendar.test.ts)** — Часы календаря, границы отображения и длительность событий.

| Сценарий | Проверка |
|---|---|
| [defaults to 08:00–24:00](../frontend/tests/calendar.test.ts#L7) | Диапазон календаря по умолчанию — с 08:00 до 24:00. |
| [includes an early session only in its own week](../frontend/tests/calendar.test.ts#L8) | Раннее занятие расширяет диапазон только своей недели. |
| [includes an early deadline](../frontend/tests/calendar.test.ts#L9) | Ранний срок сдачи учитывается при определении начала дня. |
| [preserves late deadlines](../frontend/tests/calendar.test.ts#L10) | Поздние сроки сдачи остаются видимыми. |
| [includes early hours when a session spans midnight](../frontend/tests/calendar.test.ts#L11) | Занятие, пересекающее полночь, расширяет отображаемый диапазон. |
| [does not expand for a session that ended at the start of the week](../frontend/tests/calendar.test.ts#L12) | Занятие, завершившееся ровно к началу недели, не расширяет диапазон. |
| [includes a session beginning before the visible week but ending inside it](../frontend/tests/calendar.test.ts#L13) | Занятие, начавшееся до недели и заканчивающееся в ней, отображается. |
| [does not expand for next week](../frontend/tests/calendar.test.ts#L14) | События следующей недели не влияют на текущую. |
| [allows explicit full day](../frontend/tests/calendar.test.ts#L15) | Возможность включить отображение полных суток. |
| [ignores invalid times](../frontend/tests/calendar.test.ts#L16) | Некорректные даты исключаются из расчёта. |
| [distinguishes before, during and after including boundaries](../frontend/tests/calendar.test.ts#L20) | Различение событий до начала, в процессе и после окончания с учётом границ. |
| [handles invalid intervals](../frontend/tests/calendar.test.ts#L21) | Обработка некорректных временных интервалов. |

**[contract.test.ts](../frontend/tests/contract.test.ts)** — Контракты DTO, безопасные URL и показатели мониторинга.

| Сценарий | Проверка |
|---|---|
| [keeps optimistic lock version 0](../frontend/tests/contract.test.ts#L5) | Нулевое значение версии объекта сохраняется при формировании DTO. |
| [converts date to ISO](../frontend/tests/contract.test.ts#L5) | Преобразование даты формы в ISO-формат. |
| [rejects invalid question](../frontend/tests/contract.test.ts#L5) | Отклонение вопроса с некорректным набором ответов. |
| [rejects dangerous URL](../frontend/tests/contract.test.ts#L5) | Фильтрация URL со схемой javascript. |
| [registers all 7 custom metrics](../frontend/tests/contract.test.ts#L5) | В интерфейсе представлены семь прикладных метрик. |
| [handles counter resets](../frontend/tests/contract.test.ts#L5) | Корректный расчёт приращения счётчика после его сброса. |

**[course-navigation.test.ts](../frontend/tests/course-navigation.test.ts)** — Маршрутизация публичной, студенческой и преподавательской областей.

| Сценарий | Проверка |
|---|---|
| [keeps public routes unchanged](../frontend/tests/course-navigation.test.ts#L4) | Сохранение путей публичного каталога, курсов и преподавателей. |
| [uses non-conflicting student catalog routes](../frontend/tests/course-navigation.test.ts#L5) | Отдельные маршруты каталога и учебных страниц студента. |
| [opens exact lesson in appropriate management workspace](../frontend/tests/course-navigation.test.ts#L6) | Переход к конкретному уроку в редакторе преподавателя или администратора. |

**[course-visuals.test.ts](../frontend/tests/course-visuals.test.ts)** — Отображение названий, обложек и инициалов.

| Сценарий | Проверка |
|---|---|
| [uses correct Russian plural forms](../frontend/tests/course-visuals.test.ts#L4) | Правильное склонение русских названий количества отзывов, курсов и дней. |
| [selects covers by category instead of numeric id](../frontend/tests/course-visuals.test.ts#L5) | Выбор обложки по тематике категории. |
| [derives initials from actual name](../frontend/tests/course-visuals.test.ts#L6) | Формирование инициалов из имени, включая кириллицу и пустое значение. |

**[datetime-and-session.test.ts](../frontend/tests/datetime-and-session.test.ts)** — Дата и время, интервалы занятий, проверки ссылок.

| Сценарий | Проверка |
|---|---|
| [formats time in 24-hour format and preserves local date](../frontend/tests/datetime-and-session.test.ts#L10) | 24-часовой формат времени и сохранение локальной даты. |
| [rejects impossible dates and 12-hour notation](../frontend/tests/datetime-and-session.test.ts#L11) | Отклонение невозможных дат и 12-часового формата. |
| [does not parse format as a date](../frontend/tests/datetime-and-session.test.ts#L12) | Формат занятия отображается отдельно от даты. |
| [checks session URL and date interval](../frontend/tests/datetime-and-session.test.ts#L13) | Проверка ссылки занятия, корректности интервала и формата онлайн/очно. |
| [round-trips date input and computes duration](../frontend/tests/datetime-and-session.test.ts#L14) | Обратимое преобразование даты и вычисление длительности занятия. |

**[gradebook.test.ts](../frontend/tests/gradebook.test.ts)** — Объединение страниц электронного журнала.

| Сценарий | Проверка |
|---|---|
| [keeps separate assessments even if quiz and assignment IDs overlap](../frontend/tests/gradebook.test.ts#L6) | Разделение заданий и тестов при совпадении числовых идентификаторов. |
| [does not duplicate existing students or results after refresh](../frontend/tests/gradebook.test.ts#L7) | При повторной загрузке отсутствуют дубликаты студентов и результатов. |

**[pagination.test.ts](../frontend/tests/pagination.test.ts)** — Постраничная загрузка и устранение дублей.

| Сценарий | Проверка |
|---|---|
| [requests next backend page and stops on the final page](../frontend/tests/pagination.test.ts#L5) | Загрузка следующей страницы до достижения последней. |
| [keeps prior records and removes duplicate IDs](../frontend/tests/pagination.test.ts#L6) | Объединение записей с обновлением совпадающих идентификаторов. |

#### Playwright

**[admin-user-pagination.spec.ts](../frontend/e2e/admin-user-pagination.spec.ts)** — Пагинация и поиск в таблице пользователей.

| Сценарий | Проверка |
|---|---|
| [admin table appends pages and resets with search](../frontend/e2e/admin-user-pagination.spec.ts#L3) | Дозагрузка строк таблицы пользователей и сброс списка при новом поиске. |

**[auth-layout.spec.ts](../frontend/e2e/auth-layout.spec.ts)** — Общее оформление форм аутентификации.

| Сценарий | Проверка |
|---|---|
| [shared auth design: /login](../frontend/e2e/auth-layout.spec.ts#L2) | Единое оформление и структура формы /login. |
| [shared auth design: /register](../frontend/e2e/auth-layout.spec.ts#L2) | Единое оформление и структура формы /register. |
| [shared auth design: /forgot-password](../frontend/e2e/auth-layout.spec.ts#L2) | Единое оформление и структура формы /forgot-password. |
| [shared auth design: /reset-password](../frontend/e2e/auth-layout.spec.ts#L2) | Единое оформление и структура формы /reset-password. |

**[auth.spec.ts](../frontend/e2e/auth.spec.ts)** — Регистрация, восстановление, смена пароля и очистка состояния форм.

| Сценарий | Проверка |
|---|---|
| [UI-048 registration success link restores login and allows submission](../frontend/e2e/auth.spec.ts#L37) | После регистрации переход к форме входа позволяет отправить данные. |
| [UI-049 forgot-password success does not persist on login or next request](../frontend/e2e/auth.spec.ts#L54) | Сообщение об успешном запросе восстановления очищается при смене формы. |
| [UI-049 reset link submits token and returns to a fresh login](../frontend/e2e/auth.spec.ts#L69) | Ссылка восстановления передаёт токен, после смены пароля открывается чистая форма входа. |
| [UI-049 changing auth route and browser history clears previous form values](../frontend/e2e/auth.spec.ts#L80) | Переходы между формами и навигация браузера очищают прежние значения. |
| [UI-049 mandatory password change returns to login without stale state](../frontend/e2e/auth.spec.ts#L96) | Обязательная смена пароля завершает прежнюю сессию формы и возвращает ко входу. |

**[calendar.spec.ts](../frontend/e2e/calendar.spec.ts)** — Календарь, ранние и поздние события, переходы к урокам.

| Сценарий | Проверка |
|---|---|
| [calendar compact mode keeps early/late events and detailed drawer links](../frontend/e2e/calendar.spec.ts#L3) | Компактный календарь сохраняет ранние и поздние события и ссылки в панели подробностей. |
| [ADMIN: calendar links to the exact lesson and retains management controls](../frontend/e2e/calendar.spec.ts#L5) | Переход к конкретному уроку из календаря с сохранением управляющих элементов роли ADMIN. |
| [TEACHER: calendar links to the exact lesson and retains management controls](../frontend/e2e/calendar.spec.ts#L5) | Переход к конкретному уроку из календаря с сохранением управляющих элементов роли TEACHER. |

**[catalog-appearance.spec.ts](../frontend/e2e/catalog-appearance.spec.ts)** — Внешний вид каталога, карточки курса и профиля преподавателя.

| Сценарий | Проверка |
|---|---|
| [catalog has hero SVG and category cover](../frontend/e2e/catalog-appearance.spec.ts#L5) | Главный экран каталога содержит SVG-иллюстрацию и обложку категории. |
| [teacher page has separate profile, no catalog hero](../frontend/e2e/catalog-appearance.spec.ts#L6) | Профиль преподавателя имеет собственный экран без иллюстрации каталога. |
| [public course action is compact and reviews empty is illustrated](../frontend/e2e/catalog-appearance.spec.ts#L7) | Компактная кнопка записи на курс и оформление пустого списка отзывов. |

**[catalog-category-search.spec.ts](../frontend/e2e/catalog-category-search.spec.ts)** — Серверный поиск категорий.

| Сценарий | Проверка |
|---|---|
| [category server search loads additional pages and filters courses by selected ID](../frontend/e2e/catalog-category-search.spec.ts#L3) | Поиск категорий загружает дополнительные страницы и фильтрует курсы по выбранному ID. |

**[catalog-navigation.spec.ts](../frontend/e2e/catalog-navigation.spec.ts)** — Маршруты каталога и отображение оболочки по роли.

| Сценарий | Проверка |
|---|---|
| [student catalog and details retain sidebar; public catalog retains hero](../frontend/e2e/catalog-navigation.spec.ts#L3) | В каталоге студента сохраняется боковая панель, а в публичном — главный экран. |
| [guest still has the public hero and favicon files are served](../frontend/e2e/catalog-navigation.spec.ts#L5) | Публичный главный экран и доступность favicon для гостя. |

**[catalog-teacher-search.spec.ts](../frontend/e2e/catalog-teacher-search.spec.ts)** — Серверный поиск преподавателей.

| Сценарий | Проверка |
|---|---|
| [public teacher search is paginated on the server and selection filters courses](../frontend/e2e/catalog-teacher-search.spec.ts#L3) | Серверная пагинация поиска преподавателя и фильтрация курсов по выбору. |

**[gradebook.spec.ts](../frontend/e2e/gradebook.spec.ts)** — Пагинация строк и столбцов электронного журнала.

| Сценарий | Проверка |
|---|---|
| [gradebook loads row and column pages without losing existing cells](../frontend/e2e/gradebook.spec.ts#L3) | Дозагрузка строк и столбцов журнала с сохранением ранее полученных ячеек. |

**[quiz-results.spec.ts](../frontend/e2e/quiz-results.spec.ts)** — Таблица результатов и поиск студента.

| Сценарий | Проверка |
|---|---|
| [UI-099/103 result table shows name, student autocomplete filters attempt API](../frontend/e2e/quiz-results.spec.ts#L3) | Имя студента в таблице результатов и фильтр попыток через автодополнение. |

**[smoke.spec.ts](../frontend/e2e/smoke.spec.ts)** — Публичный каталог, форма входа и защита административной страницы.

| Сценарий | Проверка |
|---|---|
| [public catalog and login form (stubbed catalog)](../frontend/e2e/smoke.spec.ts#L2) | Открытие публичного каталога и формы входа с тестовыми ответами API. |
| [anonymous admin route redirects](../frontend/e2e/smoke.spec.ts#L3) | Перенаправление гостя с административной страницы на вход. |

**[student-course-tree.spec.ts](../frontend/e2e/student-course-tree.spec.ts)** — Дерево курса и отображение заданий.

| Сценарий | Проверка |
|---|---|
| [tree includes overdue and graded assignments under lesson, opens overdue read-only](../frontend/e2e/student-course-tree.spec.ts#L3) | Дерево урока включает просроченные и оценённые задания; просроченное открывается для просмотра. |

**[submissions.spec.ts](../frontend/e2e/submissions.spec.ts)** — Просмотр отправленной работы и оценивание.

| Сценарий | Проверка |
|---|---|
| [Existing submitted work remains read-only; editing deferred](../frontend/e2e/submissions.spec.ts#L3) | Сданная работа доступна для просмотра без повторного редактирования. |
| [UI-081/084/093 checks named student work through correct grade endpoint and default course](../frontend/e2e/submissions.spec.ts#L5) | Проверка работы конкретного студента через маршрут оценивания и выбора курса по умолчанию. |

**[system-info.spec.ts](../frontend/e2e/system-info.spec.ts)** — Системная информация и разграничение доступа.

| Сценарий | Проверка |
|---|---|
| [administrator sees actual system info from API; existing tabs remain](../frontend/e2e/system-info.spec.ts#L12) | Администратор видит данные системного API; остальные вкладки сохраняются. |
| [student cannot open admin monitoring](../frontend/e2e/system-info.spec.ts#L29) | Студенту недоступен административный мониторинг. |

#### Node.js

**[architecture.test.mjs](../frontend/scripts/architecture.test.mjs)** — Границы слоёв, циклы импортов и централизованный HTTP-транспорт.

| Сценарий | Проверка |
|---|---|
| [Frontend architecture: app, modules, shared and assets respect layer boundaries](../frontend/scripts/architecture.test.mjs#L107) | Соблюдение границ слоёв app, modules, shared и assets. |
| [Frontend architecture: no cycles in local imports, re-exports or literal dynamic imports](../frontend/scripts/architecture.test.mjs#L148) | Отсутствие циклов в локальных, повторных и динамических импортах. |
| [Frontend architecture: browser HTTP transports are centralized in shared/api.ts](../frontend/scripts/architecture.test.mjs#L183) | HTTP-транспорт сосредоточен в shared/api.ts. |

**[bundle-report.test.cjs](../frontend/scripts/bundle-report.test.cjs)** — Проверка анализа чанков сборки и отложенной загрузки.

| Сценарий | Проверка |
|---|---|
| [static closure ignores dynamic imports and counts shared chunks once](../frontend/scripts/bundle-report.test.cjs#L11) | Расчёт статических зависимостей без динамических импортов и двойного подсчёта общих чанков. |
| [bundle audit rejects expensive route leaking into anonymous startup](../frontend/scripts/bundle-report.test.cjs#L16) | Проверка обнаруживает тяжёлый модуль, попавший в начальную загрузку гостя. |
| [bundle audit rejects a missing dynamic entry](../frontend/scripts/bundle-report.test.cjs#L22) | Проверка обнаруживает отсутствие обязательной динамической точки входа. |

**[structure.test.cjs](../frontend/scripts/structure.test.cjs)** — Разрешение импортов, зависимости и имена файлов.

| Сценарий | Проверка |
|---|---|
| [Every local import and re-export resolves; architectural layers and module entrypoints are respected](../frontend/scripts/structure.test.cjs#L33) | Разрешение локальных импортов и экспортов, проверка слоёв и точек входа модулей. |
| [No circular local module dependencies](../frontend/scripts/structure.test.cjs#L34) | Отсутствие циклических зависимостей локальных модулей. |
| [Browser HTTP transport remains centralized in shared/api.ts](../frontend/scripts/structure.test.cjs#L35) | Запрет самостоятельного HTTP-транспорта в других модулях. |
| [Executable test and source file names are functional, not patch-version based](../frontend/scripts/structure.test.cjs#L36) | Имена исходных и тестовых файлов соответствуют назначению, а не номерам патчей. |

#### Дополнительные проверки Nginx и контракта

[`check-proxy.py`](../frontend/scripts/check-proxy.py) запускает настоящий Nginx и тестовый HTTP-сервер. Для каждого сочетания маршрута и IP проверяются подстановка фактического адреса клиента, удаление поддельных proxy-заголовков и сохранение Authorization.

| Маршрут | Адрес клиента | Ожидаемый результат |
|---|---|---|
| `/api/auth/login` | `127.0.0.2` | HTTP 200, корректные X-Forwarded-For и X-Real-IP, удаление поддельных заголовков |
| `/api/auth/login` | `127.0.0.3` | HTTP 200, корректные X-Forwarded-For и X-Real-IP, удаление поддельных заголовков |
| `/actuator/health` | `127.0.0.2` | HTTP 200, корректные X-Forwarded-For и X-Real-IP, удаление поддельных заголовков |
| `/actuator/health` | `127.0.0.3` | HTTP 200, корректные X-Forwarded-For и X-Real-IP, удаление поддельных заголовков |
| `/actuator/metrics` | `127.0.0.2` | HTTP 200, корректные X-Forwarded-For и X-Real-IP, удаление поддельных заголовков |
| `/actuator/metrics` | `127.0.0.3` | HTTP 200, корректные X-Forwarded-For и X-Real-IP, удаление поддельных заголовков |

[`check-contract.cjs`](../frontend/scripts/check-contract.cjs) проверяет наличие семи прикладных метрик в исходниках и соответствие ссылок на схемы запросов файлу `contract.json`. [`bundle-report.cjs`](../frontend/scripts/bundle-report.cjs) анализирует манифест Vite и состав начальной и отложенной загрузки.

### 8.15. HTTP Client

[`requests_auto.http`](../requests_auto.http) содержит 394 последовательных запроса с проверками ответов. Их названия соответствуют каталогу `CourseFlowRegressionIT` выше. [`requests.http`](../requests.http) содержит ручные проверки и отдельные примеры вызовов API. Ниже приведён указатель запросов по порядку расположения в файле; связанные комментарии и настройки находятся в исходном сценарии.

| № | Запрос | Назначение (заголовок сценария) |
|---:|---|---|
| 1 | [`GET {{baseUrl}}/api/system/status`](../requests.http#L10) | 1. Проверка Spring Boot и соединения с PostgreSQL |
| 2 | [`GET {{baseUrl}}/actuator/health`](../requests.http#L17) | 2. Стандартная проверка здоровья приложения (Actuator) |
| 3 | [`GET http://localhost:8080/api/system/status`](../requests.http#L24) | Existing endpoint: must work |
| 4 | [`GET http://localhost:8080/api/does-not-exist`](../requests.http#L27) | Nonexistent endpoint: must return 404 |
| 5 | [`POST http://localhost:8080/api/categories`](../requests.http#L32) | 1. Create category - expected 201 |
| 6 | [`POST http://localhost:8080/api/categories`](../requests.http#L42) | 2. Create another category - expected 201 |
| 7 | [`GET http://localhost:8080/api/categories?page=0&size=10`](../requests.http#L52) | 3. Get categories with pagination - expected 200 |
| 8 | [`GET http://localhost:8080/api/categories?search=Java`](../requests.http#L56) | 4. Search categories - expected 200 |
| 9 | [`GET http://localhost:8080/api/categories?sortBy=name&direction=desc`](../requests.http#L60) | 5. Sort descending - expected 200 |
| 10 | [`POST http://localhost:8080/api/categories`](../requests.http#L64) | 6. Duplicate category - expected 409 |
| 11 | [`POST http://localhost:8080/api/categories`](../requests.http#L74) | 7. Empty name - expected 400 |
| 12 | [`GET http://localhost:8080/api/categories?size=0`](../requests.http#L84) | 8. Invalid page size - expected 400 |
| 13 | [`GET http://localhost:8080/api/categories/999999`](../requests.http#L88) | 9. Category not found - expected 404 |
| 14 | [`GET http://localhost:8080/api/categories?sortBy=password`](../requests.http#L92) | 10. Invalid sorting field - expected 400 |
| 15 | [`GET http://localhost:8080/api/categories?page=0&size=10`](../requests.http#L97) | 1. Public categories - expected 200 |
| 16 | [`GET http://localhost:8080/api/system/status`](../requests.http#L101) | 2. System status - expected 200 |
| 17 | [`POST http://localhost:8080/api/categories`](../requests.http#L105) | 3. Create category without authentication - expected 401 |
| 18 | [`GET http://localhost:8080/api/categories?page=0&size=10`](../requests.http#L116) | 1. Public categories - expected 200 |
| 19 | [`GET http://localhost:8080/api/system/status`](../requests.http#L120) | 2. System status - expected 200 |
| 20 | [`POST http://localhost:8080/api/categories`](../requests.http#L124) | 3. Protected endpoint without JWT - expected 401 |
| 21 | [`POST http://localhost:8080/api/categories`](../requests.http#L134) | 4. Protected endpoint with invalid JWT - expected 401 |
| 22 | [`POST http://localhost:8080/api/auth/register`](../requests.http#L146) | 1. Register STUDENT - expected 201 |
| 23 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L157) | 2. Login - expected 200 and JWT |
| 24 | [`POST http://localhost:8080/api/categories`](../requests.http#L171) | 3. Access ADMIN-only endpoint as STUDENT - expected 403 |
| 25 | [`GET http://localhost:8080/api/categories?page=0&size=10`](../requests.http#L182) | 4. Public catalog - expected 200 |
| 26 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L186) | 5. Incorrect password - expected 401 |
| 27 | [`POST http://localhost:8080/api/auth/register`](../requests.http#L196) | 6. Duplicate registration - expected 409 |
| 28 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L208) | 1. Login and save JWT |
| 29 | [`POST http://localhost:8080/api/auth/logout`](../requests.http#L222) | 2. Logout - expected 204 |
| 30 | [`POST http://localhost:8080/api/categories`](../requests.http#L227) | 3. Reuse revoked JWT - expected 401 |
| 31 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L238) | 1. Login as system ADMIN |
| 32 | [`POST http://localhost:8080/api/categories`](../requests.http#L255) | 2. Create category as ADMIN - expected 201 |
| 33 | [`POST http://localhost:8080/api/auth/logout`](../requests.http#L266) | 3. Logout ADMIN - expected 204 |
| 34 | [`POST http://localhost:8080/api/categories`](../requests.http#L271) | 4. Reuse old ADMIN JWT - expected 401 |
| 35 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L283) | 1. Login as STUDENT |
| 36 | [`GET http://localhost:8080/api/users/me`](../requests.http#L297) | 2. STUDENT: own profile - expected 200 |
| 37 | [`GET http://localhost:8080/api/users`](../requests.http#L302) | 3. STUDENT: all users - expected 403 |
| 38 | [`GET http://localhost:8080/api/users/1`](../requests.http#L307) | 4. STUDENT: another user - expected 403 |
| 39 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L312) | 5. Login as ADMIN |
| 40 | [`GET http://localhost:8080/api/users?page=0&size=10`](../requests.http#L326) | 6. ADMIN: all users - expected 200 |
| 41 | [`GET http://localhost:8080/api/users?q=student1`](../requests.http#L331) | 7. ADMIN: search by email - expected 200 |
| 42 | [`GET http://localhost:8080/api/users?status=ACTIVE&role=STUDENT`](../requests.http#L336) | 8. ADMIN: filter active students - expected 200 |
| 43 | [`GET http://localhost:8080/api/users?role=ADMIN`](../requests.http#L341) | 9. ADMIN: list admins - expected 200 |
| 44 | [`GET http://localhost:8080/api/users?page=0&size=5&sortBy=displayName&direction=desc`](../requests.http#L346) | 10. ADMIN: sorting and pagination - expected 200 |
| 45 | [`GET http://localhost:8080/api/users/999999`](../requests.http#L351) | 11. ADMIN: nonexistent user - expected 404 |
| 46 | [`GET http://localhost:8080/api/users/me`](../requests.http#L356) | 12. Without JWT - expected 401 |
| 47 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L362) | 1. Login ADMIN |
| 48 | [`POST http://localhost:8080/api/users`](../requests.http#L376) | 2. ADMIN creates TEACHER - expected 201 |
| 49 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L393) | 3. Login TEACHER - expected 200 |
| 50 | [`GET http://localhost:8080/api/users`](../requests.http#L407) | 4. TEACHER cannot list users - expected 403 |
| 51 | [`PUT http://localhost:8080/api/users/{{teacher_id}}/roles`](../requests.http#L412) | 5. ADMIN promotes TEACHER to ADMIN - expected 200 |
| 52 | [`GET http://localhost:8080/api/users/me`](../requests.http#L422) | 6. Old TEACHER token must be revoked - expected 401 |
| 53 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L427) | 7. Login TEACHER again - expected 200 |
| 54 | [`GET http://localhost:8080/api/users`](../requests.http#L441) | 8. New ADMIN can list users - expected 200 |
| 55 | [`PUT http://localhost:8080/api/users/{{teacher_id}}/roles`](../requests.http#L446) | 9. Empty roles - expected 400 |
| 56 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L458) | 1. Login ADMIN - expected 200 |
| 57 | [`POST http://localhost:8080/api/users`](../requests.http#L473) | 2. Create test STUDENT - expected 201 |
| 58 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L490) | 3. Login STUDENT - expected 200 |
| 59 | [`PUT http://localhost:8080/api/users/{{demo_user_id}}`](../requests.http#L504) | 4. ADMIN changes student details - expected 200 |
| 60 | [`GET http://localhost:8080/api/users/me`](../requests.http#L515) | 5. Old JWT after email change - expected 401 |
| 61 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L520) | 6. Login with new email - expected 200 |
| 62 | [`POST http://localhost:8080/api/users/{{demo_user_id}}/deactivate`](../requests.http#L534) | 7. ADMIN deactivates student - expected 200 |
| 63 | [`GET http://localhost:8080/api/users/me`](../requests.http#L539) | 8. Old JWT after deactivation - expected 401 |
| 64 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L544) | 9. Login of inactive student - expected 401 |
| 65 | [`POST http://localhost:8080/api/users/{{demo_user_id}}/activate`](../requests.http#L554) | 10. ADMIN activates student - expected 200 |
| 66 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L559) | 11. Login after activation - expected 200 |
| 67 | [`POST http://localhost:8080/api/users/{{admin_id}}/deactivate`](../requests.http#L573) | 12. System ADMIN deactivation - expected 409 |
| 68 | [`POST http://localhost:8080/api/users/{{demo_user_id}}/deactivate`](../requests.http#L578) | 13. STUDENT tries to deactivate a user - expected 403 |
| 69 | [`POST http://localhost:8080/api/users/999999/deactivate`](../requests.http#L583) | 14. Nonexistent user - expected 404 |
| 70 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L590) | 1. Login ADMIN - 200 |
| 71 | [`POST http://localhost:8080/api/users`](../requests.http#L605) | 2. Create user for deletion test - 201 |
| 72 | [`DELETE http://localhost:8080/api/users/{{archive_user_id}}`](../requests.http#L622) | 3. Delete ACTIVE user - 409 |
| 73 | [`POST http://localhost:8080/api/users/{{archive_user_id}}/deactivate`](../requests.http#L627) | 4. Deactivate user - 200 |
| 74 | [`DELETE http://localhost:8080/api/users/{{archive_user_id}}`](../requests.http#L632) | 5. Delete INACTIVE user - 204 |
| 75 | [`GET http://localhost:8080/api/users/{{archive_user_id}}`](../requests.http#L637) | 6. Read archived User - 200 |
| 76 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L642) | 7. Login with deleted account - 401 |
| 77 | [`DELETE http://localhost:8080/api/users/{{archive_user_id}}`](../requests.http#L652) | 8. Repeated DELETE - 204 |
| 78 | [`POST http://localhost:8080/api/users/{{archive_user_id}}/activate`](../requests.http#L657) | 9. Reactivate deleted user - 409 |
| 79 | [`DELETE http://localhost:8080/api/users/{{admin_id}}`](../requests.http#L662) | 10. Delete system ADMIN - 409 |
| 80 | [`DELETE http://localhost:8080/api/users/999999`](../requests.http#L667) | 11. Delete nonexistent user - 404 |
| 81 | [`GET http://localhost:8080/api/users?status=DELETED`](../requests.http#L672) | 12. List archived users - 200 |
| 82 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L679) | 1. Login STUDENT - expected 200 |
| 83 | [`POST http://localhost:8080/api/auth/change-password`](../requests.http#L693) | 2. Change password - expected 204 |
| 84 | [`GET http://localhost:8080/api/users/me`](../requests.http#L704) | 3. Old JWT must be invalid - expected 401 |
| 85 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L709) | 4. Login with old password - expected 401 |
| 86 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L719) | 5. Login with new password - expected 200 |
| 87 | [`POST http://localhost:8080/api/auth/change-password`](../requests.http#L733) | 6. Restore original password - expected 204 |
| 88 | [`GET http://localhost:8080/api/users/me`](../requests.http#L744) | 7. JWT after restoration - expected 401 |
| 89 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L749) | 8. Login with original password - expected 200 |
| 90 | [`POST http://localhost:8080/api/auth/change-password`](../requests.http#L763) | 9. Incorrect current password - expected 400 |
| 91 | [`POST http://localhost:8080/api/auth/register`](../requests.http#L778) | 1. Register test user - expected 201 |
| 92 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L789) | 2. Login with original password - expected 200 |
| 93 | [`POST http://localhost:8080/api/auth/forgot-password`](../requests.http#L804) | 3. Request password reset - expected 202 |
| 94 | [`POST http://localhost:8080/api/auth/forgot-password`](../requests.http#L814) | No second email should be issued during cooldown |
| 95 | [`POST http://localhost:8080/api/auth/forgot-password`](../requests.http#L823) | 5. Unknown email - expected 202 |
| 96 | [`POST http://localhost:8080/api/auth/reset-password`](../requests.http#L832) | 6. Reset using token from email - expected 204 |
| 97 | [`POST http://localhost:8080/api/auth/reset-password`](../requests.http#L842) | 7. Reuse the token - expected 400 |
| 98 | [`GET http://localhost:8080/api/users/me`](../requests.http#L852) | 8. Previously issued JWT - expected 401 |
| 99 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L857) | 9. Login with old password - expected 401 |
| 100 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L867) | 10. Login with new password - expected 200 |
| 101 | [`POST http://localhost:8080/api/auth/reset-password`](../requests.http#L877) | 11. Invalid token - expected 400 |
| 102 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L890) | ADMIN login - expected 200 |
| 103 | [`GET http://localhost:8080/api/users?q={{reset_email}}`](../requests.http#L904) | Find user - expected 200 |
| 104 | [`POST http://localhost:8080/api/users/{{reset_user_id}}/password-reset`](../requests.http#L910) | 1. ADMIN requests password reset - 202 |
| 105 | [`POST http://localhost:8080/api/users/{{reset_user_id}}/password-reset`](../requests.http#L916) | No second email should be sent during cooldown |
| 106 | [`POST http://localhost:8080/api/users/999999/password-reset`](../requests.http#L921) | 3. Nonexistent user - 404 |
| 107 | [`POST http://localhost:8080/api/users/{{reset_user_id}}/password-reset`](../requests.http#L926) | 4. Without authorization - 401 |
| 108 | [`POST http://localhost:8080/api/auth/reset-password`](../requests.http#L929) | 5. сбрасываем пароль |
| 109 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L938) | Проверяем вход с новым паролем |
| 110 | [`GET http://localhost:8080/api/categories`](../requests.http#L954) | Categories |
| 111 | [`GET http://localhost:8080/api/users?role=TEACHER`](../requests.http#L957) | Teachers |
| 112 | [`POST http://localhost:8080/api/courses`](../requests.http#L962) | 1. Create course - expected 201 |
| 113 | [`GET http://localhost:8080/api/courses/{{course_id}}`](../requests.http#L981) | 2. Get course - expected 200 |
| 114 | [`GET http://localhost:8080/api/courses?page=0&size=10`](../requests.http#L985) | 3. List courses - expected 200 |
| 115 | [`POST http://localhost:8080/api/courses`](../requests.http#L989) | 4. Category not found - expected 404 |
| 116 | [`POST http://localhost:8080/api/courses`](../requests.http#L1004) | 5. Invalid duration - expected 400 |
| 117 | [`GET http://localhost:8080/api/courses/999999`](../requests.http#L1019) | 6. Missing course - expected 404 |
| 118 | [`PUT http://localhost:8080/api/courses/{{course_id}}`](../requests.http#L1028) | 1. Update course - expected 200 |
| 119 | [`GET http://localhost:8080/api/courses/{{course_id}}`](../requests.http#L1043) | 2. Read updated course - expected 200 |
| 120 | [`GET http://localhost:8080/api/courses?q=Java`](../requests.http#L1047) | 3. Search courses - expected 200 |
| 121 | [`GET http://localhost:8080/api/courses?categoryId=1`](../requests.http#L1051) | 4. Filter by category - expected 200 |
| 122 | [`GET http://localhost:8080/api/courses?teacherId=3`](../requests.http#L1055) | 5. Filter by teacher - expected 200 |
| 123 | [`GET http://localhost:8080/api/courses?status=ACTIVE`](../requests.http#L1059) | 6. Filter ACTIVE courses - expected 200 |
| 124 | [`GET http://localhost:8080/api/courses?sortBy=title&direction=desc`](../requests.http#L1063) | 7. Sort by title descending - expected 200 |
| 125 | [`GET http://localhost:8080/api/courses?page=0&size=2`](../requests.http#L1067) | 8. Pagination - expected 200 |
| 126 | [`GET http://localhost:8080/api/courses?q=Java&categoryId=1&status=ACTIVE&sortBy=startDate&direction=asc`](../requests.http#L1071) | 9. Combined filters - expected 200 |
| 127 | [`GET http://localhost:8080/api/courses?sortBy=unknown`](../requests.http#L1075) | 10. Invalid sort field - expected 400 |
| 128 | [`PUT http://localhost:8080/api/courses/{{course_id}}`](../requests.http#L1079) | 11. Invalid duration - expected 400 |
| 129 | [`PUT http://localhost:8080/api/courses/999999`](../requests.http#L1094) | 12. Missing course - expected 404 |
| 130 | [`POST http://localhost:8080/api/courses`](../requests.http#L1114) | 1. Create a temporary course - 201 |
| 131 | [`POST http://localhost:8080/api/courses/{{temporary_course_id}}/archive`](../requests.http#L1136) | 2. Archive course - 200 |
| 132 | [`POST http://localhost:8080/api/courses/{{temporary_course_id}}/archive`](../requests.http#L1141) | 3. Archive again - 200 |
| 133 | [`GET http://localhost:8080/api/courses/{{temporary_course_id}}`](../requests.http#L1146) | 4. Read archived course - 200 |
| 134 | [`GET http://localhost:8080/api/courses?status=ARCHIVED&page=0&size=10`](../requests.http#L1150) | 5. Filter archived courses - 200 |
| 135 | [`DELETE http://localhost:8080/api/courses/{{temporary_course_id}}`](../requests.http#L1154) | 6. Delete course without dependencies - 204 |
| 136 | [`GET http://localhost:8080/api/courses/{{temporary_course_id}}`](../requests.http#L1159) | 7. Read deleted course - 404 |
| 137 | [`DELETE http://localhost:8080/api/courses/999999`](../requests.http#L1163) | 8. Delete missing course - 404 |
| 138 | [`POST http://localhost:8080/api/tags`](../requests.http#L1173) | 1. Create tag - expected 201 |
| 139 | [`GET http://localhost:8080/api/tags?page=0&size=10`](../requests.http#L1187) | 2. List tags - expected 200 |
| 140 | [`GET http://localhost:8080/api/tags?q=jpa`](../requests.http#L1191) | 3. Search tags - expected 200 |
| 141 | [`GET http://localhost:8080/api/tags/{{tag_id}}`](../requests.http#L1195) | 4. Get tag - expected 200 |
| 142 | [`POST http://localhost:8080/api/tags`](../requests.http#L1199) | 5. Duplicate tag - expected 409 |
| 143 | [`PUT http://localhost:8080/api/tags/{{tag_id}}`](../requests.http#L1209) | 6. Update tag - expected 200 |
| 144 | [`GET http://localhost:8080/api/tags?q=hibernate&sortBy=name&direction=asc&page=0&size=5`](../requests.http#L1219) | 7. Search after update - expected 200 |
| 145 | [`POST http://localhost:8080/api/tags`](../requests.http#L1223) | 8. Invalid name - expected 400 |
| 146 | [`GET http://localhost:8080/api/tags?page=-1&size=10`](../requests.http#L1233) | 9. Invalid pagination - expected 400 |
| 147 | [`DELETE http://localhost:8080/api/tags/{{tag_id}}`](../requests.http#L1237) | 10. Delete unused tag - expected 204 |
| 148 | [`GET http://localhost:8080/api/tags/{{tag_id}}`](../requests.http#L1242) | 11. Read deleted tag - expected 404 |
| 149 | [`POST http://localhost:8080/api/tags`](../requests.http#L1251) | 1. Create first tag - 201 |
| 150 | [`POST http://localhost:8080/api/tags`](../requests.http#L1265) | 2. Create second tag - 201 |
| 151 | [`POST http://localhost:8080/api/courses/{{course_id}}/tags/{{tag_1_id}}`](../requests.http#L1279) | 3. Add first tag - 204 |
| 152 | [`POST http://localhost:8080/api/courses/{{course_id}}/tags/{{tag_2_id}}`](../requests.http#L1284) | 4. Add second tag - 204 |
| 153 | [`POST http://localhost:8080/api/courses/{{course_id}}/tags/{{tag_1_id}}`](../requests.http#L1289) | 5. Add first tag again - 204 |
| 154 | [`GET http://localhost:8080/api/courses/{{course_id}}/tags?page=0&size=1`](../requests.http#L1294) | 6. List course tags - 200 |
| 155 | [`DELETE http://localhost:8080/api/tags/{{tag_1_id}}`](../requests.http#L1298) | 7. Try deleting a used tag - 409 |
| 156 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/tags/{{tag_1_id}}`](../requests.http#L1303) | 8. Remove tag from course - 204 |
| 157 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/tags/{{tag_1_id}}`](../requests.http#L1308) | 9. Remove tag again - 204 |
| 158 | [`DELETE http://localhost:8080/api/tags/{{tag_1_id}}`](../requests.http#L1313) | 10. Delete unused tag - 204 |
| 159 | [`GET http://localhost:8080/api/courses/{{course_id}}/tags?page=0&size=10`](../requests.http#L1318) | 11. List remaining tags - 200 |
| 160 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L1327) | 1. Login ADMIN - 200 |
| 161 | [`POST http://localhost:8080/api/courses/3/modules`](../requests.http#L1343) | 1. Create module - 201 |
| 162 | [`POST http://localhost:8080/api/courses/3/modules`](../requests.http#L1355) | 2. Create second module - 201 |
| 163 | [`GET http://localhost:8080/api/courses/3/modules?page=0&size=10`](../requests.http#L1367) | 3. List modules - 200 |
| 164 | [`GET http://localhost:8080/api/courses/3/modules?page=0&size=1`](../requests.http#L1371) | 4. Pagination - 200 |
| 165 | [`POST http://localhost:8080/api/courses/3/modules`](../requests.http#L1375) | 5. Duplicate position - 409 |
| 166 | [`POST http://localhost:8080/api/courses/3/modules`](../requests.http#L1386) | 6. Invalid position - 400 |
| 167 | [`GET http://localhost:8080/api/courses/3/modules/1`](../requests.http#L1397) | 1. Get module - 200 |
| 168 | [`PUT http://localhost:8080/api/courses/3/modules/1`](../requests.http#L1401) | 2. Update module - 200 |
| 169 | [`PATCH http://localhost:8080/api/courses/3/modules/1/position`](../requests.http#L1412) | 3. Swap positions - 200 |
| 170 | [`GET http://localhost:8080/api/courses/3/modules?page=0&size=10`](../requests.http#L1422) | 4. Check new order - 200 |
| 171 | [`PATCH http://localhost:8080/api/courses/3/modules/1/position`](../requests.http#L1426) | 5. Invalid position - 400 |
| 172 | [`DELETE http://localhost:8080/api/courses/3/modules/2`](../requests.http#L1436) | 6. Delete empty module - 204 |
| 173 | [`GET http://localhost:8080/api/courses/3/modules/2`](../requests.http#L1441) | 7. Deleted module - 404 |
| 174 | [`POST http://localhost:8080/api/courses/3/modules/1/lessons`](../requests.http#L1446) | 1. Create lesson - 201 |
| 175 | [`POST http://localhost:8080/api/courses/3/modules/1/lessons`](../requests.http#L1463) | 2. Create second lesson - 201 |
| 176 | [`GET http://localhost:8080/api/courses/3/modules/1/lessons?page=0&size=10`](../requests.http#L1476) | 3. Get all lessons - 200 |
| 177 | [`GET http://localhost:8080/api/courses/3/modules/1/lessons?page=0&size=1`](../requests.http#L1480) | 4. Pagination - 200 |
| 178 | [`GET http://localhost:8080/api/courses/3/modules/1/lessons/{{lesson_id}}`](../requests.http#L1484) | 5. Get lesson by ID - 200 |
| 179 | [`POST http://localhost:8080/api/courses/3/modules/1/lessons`](../requests.http#L1488) | 6. Duplicate position - 409 |
| 180 | [`POST http://localhost:8080/api/courses/3/modules/1/lessons`](../requests.http#L1500) | 7. Invalid position - 400 |
| 181 | [`PUT http://localhost:8080/api/courses/3/modules/1/lessons/{{lesson_id}}`](../requests.http#L1513) | 1. Update lesson - 200 |
| 182 | [`PATCH http://localhost:8080/api/courses/3/modules/1/lessons/{{lesson_id}}/position`](../requests.http#L1525) | 2. Swap lesson positions - 200 |
| 183 | [`GET http://localhost:8080/api/courses/3/modules/1/lessons?page=0&size=10`](../requests.http#L1535) | 3. Verify lesson order - 200 |
| 184 | [`PATCH http://localhost:8080/api/courses/3/modules/1/lessons/{{lesson_id}}/position`](../requests.http#L1539) | 4. Invalid position - 400 |
| 185 | [`POST http://localhost:8080/api/courses/3/modules/1/lessons`](../requests.http#L1549) | 5. Create temporary lesson - 201 |
| 186 | [`DELETE http://localhost:8080/api/courses/3/modules/1/lessons/{{temp_lesson_id}}`](../requests.http#L1565) | 6. Delete temporary lesson - 204 |
| 187 | [`GET http://localhost:8080/api/courses/3/modules/1/lessons/{{temp_lesson_id}}`](../requests.http#L1570) | 7. Verify deleted lesson - 404 |
| 188 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L1575) | Create first session - 201 |
| 189 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L1592) | Create adjacent session - 201 |
| 190 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L1605) | Overlapping session - 409 |
| 191 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L1618) | Invalid time range - 400 |
| 192 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions?page=0&size=10`](../requests.http#L1631) | List sessions - 200 |
| 193 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions?page=0&size=1`](../requests.http#L1635) | First page - 200 |
| 194 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions?page=1&size=1`](../requests.http#L1639) | Second page - 200 |
| 195 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}`](../requests.http#L1643) | Get session by ID - 200 |
| 196 | [`PUT http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}`](../requests.http#L1650) | Update session - 200 |
| 197 | [`PATCH http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}/time`](../requests.http#L1663) | Reschedule session - 200 |
| 198 | [`PATCH http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}/time`](../requests.http#L1674) | Overlapping reschedule - 409 |
| 199 | [`PATCH http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}/time`](../requests.http#L1684) | Invalid interval - 400 |
| 200 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L1696) | Create temporary session - 201 |
| 201 | [`DELETE http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{temp_session_id}}`](../requests.http#L1713) | Delete temporary session - 204 |
| 202 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{temp_session_id}}`](../requests.http#L1718) | Verify deletion - 404 |
| 203 | [`PUT http://localhost:8080/api/courses/1/teacher`](../requests.http#L1721) | Reassign course teacher - 200 |
| 204 | [`GET http://localhost:8080/api/users?page=0&size=100`](../requests.http#L1730) | Get all users — ADMIN |
| 205 | [`GET http://localhost:8080/api/courses?page=0&size=10&sortBy=title&direction=desc`](../requests.http#L1734) | Courses sorted by title DESC — 200 |
| 206 | [`GET http://localhost:8080/api/courses/3/modules/1/lessons`](../requests.http#L1737) | GET lessons without JWT — expected 401 |
| 207 | [`GET http://localhost:8080/api/users?page=0&size=10`](../requests.http#L1740) | TEACHER cannot list users — expected 403 |
| 208 | [`GET http://localhost:8080/api/users?page=0&size=10`](../requests.http#L1744) | Users without authorization — expected 401 |
| 209 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L1763) | 1. Login ADMIN - expected 200 |
| 210 | [`POST http://localhost:8080/api/users`](../requests.http#L1779) | 1. Create teacher with STUDENT + TEACHER roles — 201 |
| 211 | [`POST http://localhost:8080/api/users`](../requests.http#L1796) | 2. Create student — 201 |
| 212 | [`POST http://localhost:8080/api/categories`](../requests.http#L1813) | 3. Create category — 201 |
| 213 | [`POST http://localhost:8080/api/courses`](../requests.http#L1828) | 4. Create course — 201 |
| 214 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L1847) | 5. Login as student — 200 |
| 215 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L1861) | 6. Login as teacher — 200 |
| 216 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1879) | 7. First enrollment — expected 201 Created |
| 217 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1889) | Must return the SAME enrollment ID |
| 218 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments/me`](../requests.http#L1894) | 9. Get my enrollment — expected 200, ACTIVE |
| 219 | [`DELETE http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments/me`](../requests.http#L1899) | 10. Withdraw from course — expected 204 No Content |
| 220 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments/me`](../requests.http#L1905) | endedAt must NOT be null |
| 221 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1911) | Must reuse the SAME enrollment ID |
| 222 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments/me`](../requests.http#L1917) | endedAt must be null |
| 223 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1927) | Teacher has STUDENT + TEACHER roles |
| 224 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1932) | 15. ADMIN without STUDENT role — expected 403 |
| 225 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1937) | 16. Enrollment without JWT — expected 401 |
| 226 | [`POST http://localhost:8080/api/courses/999999/enrollments`](../requests.http#L1941) | 17. Enroll in nonexistent course — expected 404 |
| 227 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/archive`](../requests.http#L1951) | 18. Archive test course — expected 200 |
| 228 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}`](../requests.http#L1956) | 19. Check archived course — expected 200, ARCHIVED |
| 229 | [`POST http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1960) | 20. Enroll in archived course — expected 409 |
| 230 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments/me`](../requests.http#L1966) | Existing enrollment must remain ACTIVE |
| 231 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments?page=0&size=10`](../requests.http#L1976) | 1. Teacher gets students of own course — 200 |
| 232 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments?status=ACTIVE&page=0&size=10`](../requests.http#L1981) | 2. Teacher gets ACTIVE students — 200 |
| 233 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments?status=WITHDRAWN`](../requests.http#L1987) | 3. Teacher gets WITHDRAWN students — 200 |
| 234 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L1992) | 4. ADMIN gets course enrollments — 200 |
| 235 | [`GET http://localhost:8080/api/users/me/enrollments?page=0&size=10`](../requests.http#L1997) | 5. Student gets own courses — 200 |
| 236 | [`GET http://localhost:8080/api/users/me/enrollments?status=ACTIVE`](../requests.http#L2002) | 6. Student filters own ACTIVE enrollments — 200 |
| 237 | [`GET http://localhost:8080/api/users/me/enrollments?status=WITHDRAWN`](../requests.http#L2008) | 7. Student filters own WITHDRAWN enrollments — 200 |
| 238 | [`GET http://localhost:8080/api/users/me/enrollments?page=0&size=1&sortBy=enrolledAt&direction=desc`](../requests.http#L2013) | 8. Pagination and sorting — 200 |
| 239 | [`GET http://localhost:8080/api/courses/{{enrollment_course_id}}/enrollments`](../requests.http#L2022) | 9. Student cannot list all course participants — 403 |
| 240 | [`GET http://localhost:8080/api/courses/3/enrollments`](../requests.http#L2028) | Course 3 belongs to another teacher |
| 241 | [`GET http://localhost:8080/api/users/me/enrollments`](../requests.http#L2033) | 11. ADMIN without STUDENT role cannot use student endpoint — 403 |
| 242 | [`GET http://localhost:8080/api/users/me/enrollments`](../requests.http#L2038) | 12. Unauthenticated user — 401 |
| 243 | [`GET http://localhost:8080/api/users/me/enrollments?page=-1`](../requests.http#L2042) | 13. Invalid page — 400 |
| 244 | [`GET http://localhost:8080/api/users/me/enrollments?size=101`](../requests.http#L2047) | 14. Invalid size — 400 |
| 245 | [`GET http://localhost:8080/api/users/me/enrollments?sortBy=passwordHash`](../requests.http#L2052) | 15. Invalid sort field — 400 |
| 246 | [`GET http://localhost:8080/api/users/me/enrollments?status=UNKNOWN`](../requests.http#L2057) | 16. Invalid status — 400 |
| 247 | [`GET http://localhost:8080/api/courses/999999/enrollments`](../requests.http#L2062) | 17. Nonexistent course — 404 |
| 248 | [`POST http://localhost:8080/api/courses`](../requests.http#L2077) | 1. Create ACTIVE course — expected 201 |
| 249 | [`POST http://localhost:8080/api/courses/{{course_id}}/modules`](../requests.http#L2096) | 2. Create module — expected 201 |
| 250 | [`POST http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons`](../requests.http#L2112) | 3. Create lesson — expected 201 |
| 251 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L2129) | 4. Create ONLINE lesson session — expected 201 |
| 252 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L2150) | 1. Login ADMIN - expected 200 |
| 253 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L2165) | 5. Login as STUDENT — expected 200 |
| 254 | [`POST http://localhost:8080/api/auth/login`](../requests.http#L2179) | 6. Login as TEACHER — expected 200 |
| 255 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons`](../requests.http#L2194) | 1. Guest cannot read lessons — expected 401 |
| 256 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L2198) | 2. Guest cannot read sessions — expected 401 |
| 257 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons`](../requests.http#L2202) | 3. ADMIN can read lessons — expected 200 |
| 258 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`](../requests.http#L2207) | 4. ADMIN can read lesson details — expected 200 |
| 259 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L2212) | 5. ADMIN can read sessions — expected 200 |
| 260 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}`](../requests.http#L2217) | 6. ADMIN can read session details — expected 200 |
| 261 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L2222) | 7. Enroll test student — expected 201 or 200 |
| 262 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons`](../requests.http#L2227) | 8. Enrolled student reads lessons — expected 200 |
| 263 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`](../requests.http#L2232) | 9. Enrolled student reads lesson details — expected 200 |
| 264 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L2237) | 10. Enrolled student reads sessions — expected 200 |
| 265 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L2242) | 11. Withdraw test student — expected 204 |
| 266 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons`](../requests.http#L2247) | 12. Withdrawn student cannot read lessons — 403 |
| 267 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons/{{lesson_id}}`](../requests.http#L2252) | 13. Withdrawn student cannot read lesson details — 403 |
| 268 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions`](../requests.http#L2257) | 14. Withdrawn student cannot read sessions — 403 |
| 269 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/sessions/{{session_id}}`](../requests.http#L2262) | 15. Withdrawn student cannot read session details — 403 |
| 270 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L2267) | 16. Reactivate enrollment — expected 200 |
| 271 | [`GET http://localhost:8080/api/courses/{{course_id}}/modules/{{module_id}}/lessons`](../requests.http#L2272) | 17. Reactivated student reads lessons — 200 |
| 272 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2282) | 1. ADMIN creates assignment — expected 201 |
| 273 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2302) | 2. Get assignment by ID — expected 200 |
| 274 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments?page=0&size=10`](../requests.http#L2307) | 3. List assignments — expected 200 |
| 275 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2312) | 4. TEACHER creates assignment — expected 201 |
| 276 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2325) | 5. STUDENT lists assignments — expected 200 |
| 277 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2330) | 6. STUDENT reads assignment details — expected 200 |
| 278 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2335) | 7. STUDENT cannot create assignment — expected 403 |
| 279 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2348) | 8. Guest cannot read assignments — expected 401 |
| 280 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2352) | 9. Invalid maxScore — expected 400 |
| 281 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments/999999`](../requests.http#L2365) | 10. Nonexistent assignment — expected 404 |
| 282 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments?page=0&size=10&sortBy=dueAt&direction=desc`](../requests.http#L2370) | 11. Sort assignments by dueAt DESC — expected 200 |
| 283 | [`PUT http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2379) | 1. Teacher updates assignment — expected 200 |
| 284 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2392) | 2. Read updated assignment — expected 200 |
| 285 | [`PUT http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2397) | 3. Student cannot update assignment — expected 403 |
| 286 | [`PUT http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2410) | 4. Invalid maxScore — expected 400 |
| 287 | [`POST http://localhost:8080/api/lessons/{{lesson_id}}/assignments`](../requests.http#L2423) | 5. Create temporary assignment — expected 201 |
| 288 | [`DELETE http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_delete_id}}`](../requests.http#L2440) | 6. Delete temporary assignment — expected 204 |
| 289 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_delete_id}}`](../requests.http#L2445) | 7. Read deleted assignment — expected 404 |
| 290 | [`DELETE http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2450) | 8. Student cannot delete main assignment — expected 403 |
| 291 | [`GET http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2455) | 9. Main assignment still exists — expected 200 |
| 292 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions/me`](../requests.http#L2464) | 1. No submission yet — expected 404 |
| 293 | [`POST http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2469) | 2. Student submits homework — expected 201 |
| 294 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions/me`](../requests.http#L2488) | 3. Student reads own submission — expected 200 |
| 295 | [`POST http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2493) | 4. Duplicate submission — expected 409 |
| 296 | [`POST http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2503) | 5. Teacher without STUDENT role cannot submit — 403 |
| 297 | [`POST http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2513) | 6. Guest cannot submit — expected 401 |
| 298 | [`POST http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2522) | 7. Empty content — expected 400 |
| 299 | [`POST http://localhost:8080/api/assignments/999999/submissions`](../requests.http#L2532) | 8. Unknown assignment — expected 404 |
| 300 | [`PUT http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2542) | 9. Teacher cannot update submitted assignment — 409 |
| 301 | [`DELETE http://localhost:8080/api/lessons/{{lesson_id}}/assignments/{{assignment_id}}`](../requests.http#L2555) | 10. ADMIN cannot delete submitted assignment — 409 |
| 302 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L2560) | 11. Withdraw student — expected 204 |
| 303 | [`POST http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2565) | 12. Withdrawn student cannot submit new work — 403 |
| 304 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions/me`](../requests.http#L2575) | 13. Withdrawn student can still read own work — 200 |
| 305 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L2580) | 14. Restore enrollment — expected 200 |
| 306 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions/me`](../requests.http#L2585) | 15. Previously submitted work still exists — 200 |
| 307 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2595) | 1. TEACHER lists all submissions — 200 |
| 308 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions?status=SUBMITTED&page=0&size=10`](../requests.http#L2600) | 2. TEACHER filters SUBMITTED works — 200 |
| 309 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2605) | 3. ADMIN lists submissions — 200 |
| 310 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2610) | 4. STUDENT cannot list all submissions — 403 |
| 311 | [`PUT http://localhost:8080/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`](../requests.http#L2615) | 5. TEACHER grades submission — 200 |
| 312 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions/me`](../requests.http#L2634) | 6. STUDENT sees own grade — 200 |
| 313 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions?status=GRADED`](../requests.http#L2639) | 7. TEACHER filters GRADED submissions — 200 |
| 314 | [`PUT http://localhost:8080/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`](../requests.http#L2644) | 8. Score above maxScore — 400 |
| 315 | [`PUT http://localhost:8080/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`](../requests.http#L2655) | 9. Negative score — 400 |
| 316 | [`PUT http://localhost:8080/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`](../requests.http#L2666) | 10. STUDENT cannot grade own submission — 403 |
| 317 | [`PUT http://localhost:8080/api/assignments/{{assignment_id}}/submissions/{{submission_id}}/grade`](../requests.http#L2677) | 11. TEACHER corrects previous grade — 200 |
| 318 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions/me`](../requests.http#L2688) | 12. STUDENT sees corrected grade — 200 |
| 319 | [`PUT http://localhost:8080/api/assignments/{{assignment_id}}/submissions/999999/grade`](../requests.http#L2693) | 13. Nonexistent submission — 404 |
| 320 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions?page=-1`](../requests.http#L2704) | 14. Invalid pagination — 400 |
| 321 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions?sortBy=unknown`](../requests.http#L2709) | 15. Unknown sort field — 400 |
| 322 | [`GET http://localhost:8080/api/assignments/{{assignment_id}}/submissions`](../requests.http#L2714) | 16. Guest cannot list submissions — 401 |
| 323 | [`POST http://localhost:8080/api/modules/{{module_id}}/quiz`](../requests.http#L2723) | 1. ADMIN creates Quiz — expected 201 |
| 324 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L2742) | 2. ADMIN gets Quiz by ID — expected 200 |
| 325 | [`GET http://localhost:8080/api/modules/{{module_id}}/quiz`](../requests.http#L2747) | 3. STUDENT gets Quiz by module — expected 200 |
| 326 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L2752) | 4. STUDENT gets Quiz by ID — expected 200 |
| 327 | [`POST http://localhost:8080/api/modules/{{module_id}}/quiz`](../requests.http#L2757) | 5. Duplicate Quiz for same module — expected 409 |
| 328 | [`POST http://localhost:8080/api/modules/{{module_id}}/quiz`](../requests.http#L2768) | 6. STUDENT cannot create Quiz — expected 403 |
| 329 | [`GET http://localhost:8080/api/modules/{{module_id}}/quiz`](../requests.http#L2779) | 7. Guest cannot access Quiz — expected 401 |
| 330 | [`POST http://localhost:8080/api/modules/{{module_id}}/quiz`](../requests.http#L2783) | 8. Invalid time limit — expected 400 |
| 331 | [`POST http://localhost:8080/api/modules/999999/quiz`](../requests.http#L2794) | 9. Unknown module — expected 404 |
| 332 | [`GET http://localhost:8080/api/quizzes/999999`](../requests.http#L2805) | 10. Unknown Quiz — expected 404 |
| 333 | [`POST http://localhost:8080/api/courses/{{course_id}}/modules`](../requests.http#L2810) | 11. TEACHER creates second module — expected 201 |
| 334 | [`POST http://localhost:8080/api/modules/{{quiz_teacher_module_id}}/quiz`](../requests.http#L2829) | 12. TEACHER creates Quiz — expected 201 |
| 335 | [`GET http://localhost:8080/api/quizzes/{{quiz_teacher_quiz_id}}`](../requests.http#L2847) | 13. STUDENT reads teacher's Quiz — expected 200 |
| 336 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L2856) | 1. ADMIN creates SINGLE_CHOICE question — 201 |
| 337 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L2896) | 2. TEACHER creates MULTIPLE_CHOICE question — 201 |
| 338 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions/manage`](../requests.http#L2945) | 3. ADMIN gets questions with correct answers — 200 |
| 339 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions/manage`](../requests.http#L2950) | 4. TEACHER gets questions with correct answers — 200 |
| 340 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L2955) | 5. STUDENT reads questions without correct answers — 200 |
| 341 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions/manage`](../requests.http#L2974) | 6. STUDENT cannot access management endpoint — 403 |
| 342 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L2979) | 7. Duplicate question position — 409 |
| 343 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L2995) | 8. SINGLE_CHOICE with two correct answers — 400 |
| 344 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3011) | 9. MULTIPLE_CHOICE with one correct answer — 400 |
| 345 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3027) | 10. Question with one option — 400 |
| 346 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3042) | 11. STUDENT cannot create question — 403 |
| 347 | [`GET http://localhost:8080/api/quizzes/999999/questions`](../requests.http#L3058) | 12. Unknown Quiz — 404 |
| 348 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3063) | 13. Guest cannot read questions — 401 |
| 349 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L3067) | 14. Withdraw student — 204 |
| 350 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3072) | 15. Withdrawn student cannot read questions — 403 |
| 351 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L3077) | 16. Reactivate enrollment — 200 |
| 352 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3082) | 17. Student can read questions again — 200 |
| 353 | [`PUT http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3092) | 1. TEACHER updates Quiz settings — 200 |
| 354 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3104) | 2. STUDENT reads updated Quiz — 200 |
| 355 | [`PUT http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3117) | 3. Invalid maxAttempts — 400 |
| 356 | [`PUT http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3129) | 4. STUDENT cannot update Quiz — 403 |
| 357 | [`PUT http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3141) | 5. Restore original Quiz settings — 200 |
| 358 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/questions`](../requests.http#L3153) | 6. Create temporary question — 201 |
| 359 | [`PUT http://localhost:8080/api/quizzes/{{quiz_id}}/questions/{{question_temp_id}}`](../requests.http#L3176) | 7. TEACHER updates temporary question — 200 |
| 360 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions/manage`](../requests.http#L3201) | 8. Read updated questions — 200 |
| 361 | [`PUT http://localhost:8080/api/quizzes/{{quiz_id}}/questions/{{question_temp_id}}`](../requests.http#L3206) | 9. Update to occupied position — 409 |
| 362 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_id}}/questions/{{question_temp_id}}`](../requests.http#L3222) | 10. STUDENT cannot delete question — 403 |
| 363 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_id}}/questions/{{question_temp_id}}`](../requests.http#L3227) | 11. TEACHER deletes temporary question — 204 |
| 364 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions/manage`](../requests.http#L3232) | 12. Main Quiz contains only original two questions — 200 |
| 365 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_id}}/questions/999999`](../requests.http#L3244) | 13. Delete nonexistent question — 404 |
| 366 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3249) | 14. STUDENT cannot delete Quiz — 403 |
| 367 | [`POST http://localhost:8080/api/quizzes/{{quiz_teacher_quiz_id}}/questions`](../requests.http#L3258) | 15. Create question in secondary Quiz — 201 |
| 368 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_teacher_quiz_id}}`](../requests.http#L3274) | 16. Delete secondary Quiz with questions — 204 |
| 369 | [`GET http://localhost:8080/api/quizzes/{{quiz_teacher_quiz_id}}`](../requests.http#L3279) | 17. Deleted Quiz is not available — 404 |
| 370 | [`GET http://localhost:8080/api/modules/{{quiz_teacher_module_id}}/quiz`](../requests.http#L3284) | 18. Secondary module has no Quiz — 404 |
| 371 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}`](../requests.http#L3289) | 19. Main Quiz still exists — 200 |
| 372 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/questions/manage`](../requests.http#L3294) | 20. Original questions and answers still exist — 200 |
| 373 | [`POST http://localhost:8080/api/modules/{{quiz_teacher_module_id}}/quiz`](../requests.http#L3303) | 1. Create Quiz with 1 minute time limit — 201 |
| 374 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3318) | 2. Cannot start empty Quiz — 409 |
| 375 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/questions`](../requests.http#L3323) | 3. Create question for attempt testing — 201 |
| 376 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3343) | 4. Guest cannot start Quiz — 401 |
| 377 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3347) | 5. ADMIN without STUDENT role cannot start Quiz — 403 |
| 378 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L3352) | 6. Withdraw student — 204 |
| 379 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3357) | 7. Withdrawn student cannot start Quiz — 403 |
| 380 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L3362) | 8. Restore enrollment — 200 |
| 381 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3372) | 9. Student starts first attempt — 201 |
| 382 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3388) | 10. Repeated start returns existing attempt — 200 |
| 383 | [`GET http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts/{{quiz_attempt_1_id}}`](../requests.http#L3404) | 11. Student reads own attempt — 200 |
| 384 | [`PUT http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}`](../requests.http#L3413) | 12. Teacher cannot update Quiz after attempt — 409 |
| 385 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}`](../requests.http#L3425) | 13. ADMIN cannot delete Quiz after attempt — 409 |
| 386 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/questions`](../requests.http#L3430) | 14. Teacher cannot add question after attempt — 409 |
| 387 | [`DELETE http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/questions/{{attempt_test_question_id}}`](../requests.http#L3446) | 15. Teacher cannot delete question after attempt — 409 |
| 388 | [`GET http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts/{{quiz_attempt_1_id}}`](../requests.http#L3456) | 16. Read expired first attempt — 200 |
| 389 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3468) | 17. Student starts second attempt — 201 |
| 390 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3483) | 18. Repeated start returns second attempt — 200 |
| 391 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts`](../requests.http#L3504) | 19. Third attempt is forbidden — 409 |
| 392 | [`GET http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts/{{quiz_attempt_2_id}}`](../requests.http#L3509) | 20. Read expired second attempt — 200 |
| 393 | [`GET http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts/999999`](../requests.http#L3521) | 21. Nonexistent attempt — 404 |
| 394 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{quiz_attempt_1_id}}`](../requests.http#L3526) | 22. Attempt belongs to another Quiz — 404 |
| 395 | [`POST http://localhost:8080/api/quizzes/999999/attempts`](../requests.http#L3531) | 23. Unknown Quiz — 404 |
| 396 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3540) | 1. Start first attempt — expected 201 |
| 397 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3554) | 2. Answer option belongs to another question — 400 |
| 398 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3571) | 3. Duplicate question — expected 400 |
| 399 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3590) | 4. Duplicate answer option — expected 400 |
| 400 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3608) | 5. SINGLE_CHOICE with two options — expected 400 |
| 401 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3626) | 6. Unknown question — expected 400 |
| 402 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}`](../requests.http#L3641) | 7. Attempt is still active — expected 200 |
| 403 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3658) | 8. Submit correct answers — expected 200, score 100 |
| 404 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}`](../requests.http#L3691) | 9. Read completed attempt — expected 200 |
| 405 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3696) | 10. Cannot submit completed attempt again — 409 |
| 406 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3710) | 11. Start second attempt — expected 201 |
| 407 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_2_id}}/submit`](../requests.http#L3724) | 12. Submit partially correct answers — 200, score 50 |
| 408 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_2_id}}`](../requests.http#L3755) | 13. Read second attempt — expected 200 |
| 409 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3760) | 14. Third attempt exceeds limit — expected 409 |
| 410 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3769) | 15. ADMIN without STUDENT role cannot submit — 403 |
| 411 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/submit`](../requests.http#L3779) | 16. Guest cannot submit — expected 401 |
| 412 | [`POST http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts/{{quiz_attempt_1_id}}/submit`](../requests.http#L3788) | 17. Cannot submit expired attempt — expected 409 |
| 413 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{quiz_attempt_1_id}}/submit`](../requests.http#L3798) | 18. Attempt does not belong to specified Quiz — 404 |
| 414 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/999999/submit`](../requests.http#L3808) | 19. Nonexistent attempt — expected 404 |
| 415 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/me`](../requests.http#L3823) | 1. STUDENT reads own attempt history — 200 |
| 416 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/me?status=SUBMITTED`](../requests.http#L3828) | 2. STUDENT filters submitted attempts — 200 |
| 417 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/me?sortBy=score&direction=desc`](../requests.http#L3833) | 3. STUDENT sorts attempts by score DESC — 200 |
| 418 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/result`](../requests.http#L3838) | 4. STUDENT reads first attempt details — 200 |
| 419 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_2_id}}/result`](../requests.http#L3856) | 5. STUDENT reads second attempt details — 200 |
| 420 | [`GET http://localhost:8080/api/quizzes/{{quiz_attempt_test_id}}/attempts/me?status=EXPIRED`](../requests.http#L3869) | 6. STUDENT reads expired attempts — 200 |
| 421 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3874) | 7. STUDENT cannot list all student results — 403 |
| 422 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/manage`](../requests.http#L3879) | 8. STUDENT cannot access management details — 403 |
| 423 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3888) | 9. TEACHER lists Quiz results — 200 |
| 424 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts?studentId={{enrollment_student_id}}`](../requests.http#L3893) | 10. TEACHER filters results by student — 200 |
| 425 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts?status=SUBMITTED&sortBy=score&direction=desc`](../requests.http#L3898) | 11. TEACHER filters submitted attempts — 200 |
| 426 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts?page=0&size=1&sortBy=score&direction=desc`](../requests.http#L3903) | 12. TEACHER reads first page — 200 |
| 427 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts?page=1&size=1&sortBy=score&direction=desc`](../requests.http#L3908) | 13. TEACHER reads second page — 200 |
| 428 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/manage`](../requests.http#L3913) | 14. TEACHER reads detailed attempt result — 200 |
| 429 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3918) | 15. ADMIN lists Quiz results — 200 |
| 430 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_2_id}}/manage`](../requests.http#L3923) | 16. ADMIN reads detailed attempt result — 200 |
| 431 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts?sortBy=unknown`](../requests.http#L3932) | 17. Unknown sort field — 400 |
| 432 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/me?page=-1`](../requests.http#L3937) | 18. Invalid pagination — 400 |
| 433 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts?status=UNKNOWN`](../requests.http#L3942) | 19. Invalid status — 400 |
| 434 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/999999/result`](../requests.http#L3947) | 20. Nonexistent attempt — 404 |
| 435 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{quiz_attempt_1_id}}/result`](../requests.http#L3952) | 21. Attempt belongs to another Quiz — 404 |
| 436 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3957) | 22. Guest cannot read results — 401 |
| 437 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L3965) | 23. Withdraw student — 204 |
| 438 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/me`](../requests.http#L3970) | 24. Withdrawn student can read own history — 200 |
| 439 | [`GET http://localhost:8080/api/quizzes/{{quiz_id}}/attempts/{{grading_attempt_1_id}}/result`](../requests.http#L3975) | 25. Withdrawn student can read own result — 200 |
| 440 | [`POST http://localhost:8080/api/quizzes/{{quiz_id}}/attempts`](../requests.http#L3980) | 26. Withdrawn student cannot start Quiz — 403 |
| 441 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L3985) | 27. Restore enrollment — 200 |
| 442 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/rating`](../requests.http#L3995) | 1. Guest reads empty course rating — 200 |
| 443 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L3999) | 2. Student has no review yet — 404 |
| 444 | [`POST http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4004) | 3. Student creates review — 201 |
| 445 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4024) | 4. Student reads own review — 200 |
| 446 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4029) | 5. Guest reads public reviews — 200 |
| 447 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/rating`](../requests.http#L4033) | 6. Guest reads course rating — 200 |
| 448 | [`POST http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4045) | 7. Duplicate review — 409 |
| 449 | [`PUT http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4056) | 8. Student updates own review — 200 |
| 450 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/rating`](../requests.http#L4078) | 9. Course rating is recalculated — 200 |
| 451 | [`PUT http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4090) | 10. Invalid rating above maximum — 400 |
| 452 | [`PUT http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4101) | 11. Invalid rating below minimum — 400 |
| 453 | [`POST http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4111) | 12. Guest cannot create review — 401 |
| 454 | [`POST http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4121) | 13. Teacher cannot review own course — 403 |
| 455 | [`POST http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4132) | 14. ADMIN without STUDENT role cannot review — 403 |
| 456 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews?page=0&size=10&sortBy=rating&direction=desc`](../requests.http#L4142) | 15. Sort reviews by rating — 200 |
| 457 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews?sortBy=unknown`](../requests.http#L4146) | 16. Invalid sort field — 400 |
| 458 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews?page=-1`](../requests.http#L4150) | 17. Invalid pagination — 400 |
| 459 | [`GET http://localhost:8080/api/courses/999999/reviews/rating`](../requests.http#L4154) | 18. Unknown course — 404 |
| 460 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L4162) | 19. Withdraw student — 204 |
| 461 | [`PUT http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4167) | 20. Withdrawn student cannot update review — 403 |
| 462 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4177) | 21. Withdrawn student can read own review — 200 |
| 463 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L4182) | 22. Restore enrollment — 200 |
| 464 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4191) | 23. Student deletes own review — 204 |
| 465 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4196) | 24. Deleted review no longer exists — 404 |
| 466 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/rating`](../requests.http#L4201) | 25. Course has no rating after deletion — 200 |
| 467 | [`POST http://localhost:8080/api/courses/{{course_id}}/reviews`](../requests.http#L4213) | 26. Recreate review without comment — 201 |
| 468 | [`PUT http://localhost:8080/api/courses/{{course_id}}/reviews/me`](../requests.http#L4227) | 27. Update final review — 200 |
| 469 | [`GET http://localhost:8080/api/courses/{{course_id}}/reviews/rating`](../requests.http#L4238) | 28. Verify final course rating — 200 |
| 470 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook`](../requests.http#L4248) | 1. TEACHER reads own course gradebook — 200 |
| 471 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook`](../requests.http#L4253) | 2. ADMIN reads course gradebook — 200 |
| 472 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?type=ASSIGNMENT`](../requests.http#L4258) | 3. Only assignments — expected 200 |
| 473 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?type=QUIZ`](../requests.http#L4275) | 4. Only quizzes — expected 200 |
| 474 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?moduleId={{module_id}}`](../requests.http#L4292) | 5. Filter by module — expected 200 |
| 475 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?enrollmentStatus=ACTIVE`](../requests.http#L4297) | 6. Filter ACTIVE students — expected 200 |
| 476 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?enrollmentStatus=WITHDRAWN`](../requests.http#L4302) | 7. Filter WITHDRAWN students — expected 200 |
| 477 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?reviewStatus=GRADED`](../requests.http#L4307) | 8. Students with graded assignments — 200 |
| 478 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?reviewStatus=SUBMITTED`](../requests.http#L4312) | 9. Students with assignments awaiting review — 200 |
| 479 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?reviewStatus=NOT_SUBMITTED`](../requests.http#L4317) | 10. Students with unsubmitted assignments — 200 |
| 480 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?q=NonexistentStudent999999`](../requests.http#L4322) | 11. Search nonexistent student — empty page, 200 |
| 481 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?sortBy=studentName&direction=asc`](../requests.http#L4327) | 12. Sort students by name — expected 200 |
| 482 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?sortBy=studentId&direction=desc`](../requests.http#L4332) | 13. Sort students by ID descending — 200 |
| 483 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?page=0&size=1`](../requests.http#L4337) | 14. First page with one student — 200 |
| 484 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?page=1&size=1`](../requests.http#L4342) | 15. Second page — expected 200 |
| 485 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?page=-1`](../requests.http#L4351) | 16. Invalid page — expected 400 |
| 486 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?size=101`](../requests.http#L4356) | 17. Invalid page size — expected 400 |
| 487 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?sortBy=unknown`](../requests.http#L4361) | 18. Unknown sort field — expected 400 |
| 488 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?direction=unknown`](../requests.http#L4366) | 19. Invalid sort direction — expected 400 |
| 489 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?moduleId=999999`](../requests.http#L4371) | 20. Unknown module — expected 404 |
| 490 | [`GET http://localhost:8080/api/courses/999999/gradebook`](../requests.http#L4376) | 21. Unknown course — expected 404 |
| 491 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?type=QUIZ&reviewStatus=GRADED`](../requests.http#L4381) | 22. Invalid combination of filters — 400 |
| 492 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook`](../requests.http#L4390) | 23. STUDENT cannot view gradebook — 403 |
| 493 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook`](../requests.http#L4395) | 24. Guest cannot view gradebook — 401 |
| 494 | [`GET http://localhost:8080/api/courses/3/gradebook`](../requests.http#L4399) | 25. Teacher cannot view another teacher's course — 403 |
| 495 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L4408) | 26. Withdraw student — expected 204 |
| 496 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?enrollmentStatus=WITHDRAWN`](../requests.http#L4413) | 27. Withdrawn student remains in gradebook — 200 |
| 497 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook?enrollmentStatus=ACTIVE`](../requests.http#L4418) | 28. Student no longer in ACTIVE list — 200 |
| 498 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L4423) | 29. Restore enrollment — expected 200 |
| 499 | [`GET http://localhost:8080/api/courses/{{course_id}}/gradebook`](../requests.http#L4428) | 30. Verify final gradebook — expected 200 |
| 500 | [`GET http://localhost:8080/api/users/me/progress`](../requests.http#L4438) | 1. Student reads progress for all courses — 200 |
| 501 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4443) | 2. Student reads progress for specific course — 200 |
| 502 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4462) | 3. Verify assignment results — 200 |
| 503 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4489) | 4. Verify Quiz best result — 200 |
| 504 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4514) | 5. Verify expired Quiz attempts — 200 |
| 505 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4539) | 6. Verify upcoming deadlines — 200 |
| 506 | [`GET http://localhost:8080/api/users/me/progress?enrollmentStatus=ACTIVE`](../requests.http#L4560) | 7. Filter active enrollments — 200 |
| 507 | [`GET http://localhost:8080/api/users/me/progress?enrollmentStatus=WITHDRAWN`](../requests.http#L4565) | 8. Filter withdrawn enrollments — 200 |
| 508 | [`GET http://localhost:8080/api/users/me/progress?sortBy=courseTitle&direction=asc`](../requests.http#L4570) | 9. Sort courses by title — 200 |
| 509 | [`GET http://localhost:8080/api/users/me/progress?sortBy=courseId&direction=desc`](../requests.http#L4575) | 10. Sort courses by ID descending — 200 |
| 510 | [`GET http://localhost:8080/api/users/me/progress?page=0&size=1`](../requests.http#L4580) | 11. First page — 200 |
| 511 | [`GET http://localhost:8080/api/users/me/progress?page=1&size=1`](../requests.http#L4585) | 12. Second page — 200 |
| 512 | [`GET http://localhost:8080/api/users/me/progress?courseId=999999`](../requests.http#L4590) | 13. Unknown course — empty page, 200 |
| 513 | [`GET http://localhost:8080/api/users/me/progress?courseId=0`](../requests.http#L4607) | 14. Invalid courseId — 400 |
| 514 | [`GET http://localhost:8080/api/users/me/progress?page=-1`](../requests.http#L4612) | 15. Invalid page — 400 |
| 515 | [`GET http://localhost:8080/api/users/me/progress?size=101`](../requests.http#L4617) | 16. Invalid page size — 400 |
| 516 | [`GET http://localhost:8080/api/users/me/progress?sortBy=unknown`](../requests.http#L4622) | 17. Unknown sort field — 400 |
| 517 | [`GET http://localhost:8080/api/users/me/progress?direction=unknown`](../requests.http#L4627) | 18. Invalid direction — 400 |
| 518 | [`GET http://localhost:8080/api/users/me/progress?enrollmentStatus=UNKNOWN`](../requests.http#L4632) | 19. Invalid enrollment status — 400 |
| 519 | [`GET http://localhost:8080/api/users/me/progress`](../requests.http#L4637) | 20. Guest cannot read progress — 401 |
| 520 | [`GET http://localhost:8080/api/users/me/progress`](../requests.http#L4641) | 21. ADMIN without STUDENT role cannot read progress — 403 |
| 521 | [`DELETE http://localhost:8080/api/courses/{{course_id}}/enrollments/me`](../requests.http#L4650) | 22. Withdraw student — 204 |
| 522 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4655) | 23. Withdrawn student retains educational history — 200 |
| 523 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}&enrollmentStatus=ACTIVE`](../requests.http#L4674) | 24. Withdrawn course excluded from active filter — 200 |
| 524 | [`POST http://localhost:8080/api/courses/{{course_id}}/enrollments`](../requests.http#L4686) | 25. Restore enrollment — 200 |
| 525 | [`GET http://localhost:8080/api/users/me/progress?courseId={{course_id}}`](../requests.http#L4691) | 26. Verify final progress — 200 |
| 526 | [`POST {{resetBaseUrl}}/api/auth/register`](../requests.http#L4718) | S04-01. Register test user |
| 527 | [`POST {{resetBaseUrl}}/api/auth/forgot-password`](../requests.http#L4728) | S04-02. Forgot password - existing email |
| 528 | [`POST {{resetBaseUrl}}/api/auth/forgot-password`](../requests.http#L4736) | S04-02. Forgot password - existing email |
| 529 | [`POST {{resetBaseUrl}}/api/auth/forgot-password`](../requests.http#L4744) | S04-03. Forgot password - unknown email |
| 530 | [`POST {{resetBaseUrl}}/api/auth/reset-password`](../requests.http#L4752) | S04-04. Reset password |
| 531 | [`POST {{resetBaseUrl}}/api/auth/login`](../requests.http#L4761) | S04-05. Login with old password |
| 532 | [`POST {{resetBaseUrl}}/api/auth/login`](../requests.http#L4770) | S04-06. Login with new password |
| 533 | [`POST {{resetBaseUrl}}/api/auth/reset-password`](../requests.http#L4779) | S04-07. Reuse reset token |

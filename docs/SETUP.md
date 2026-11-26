# CourseFlow — настройка и запуск

[README](../README.md) · [Тестирование](TESTING.md) · [API](API.md)

## 1. Требования и варианты размещения

| Вариант | Требования |
|---|---|
| Локальная разработка backend и frontend | Java 17, Git, Node.js 22 (не ниже 20.19), npm, доступ к PostgreSQL 18 |
| Модульные и архитектурные тесты backend | Java 17, Gradle Wrapper |
| Интеграционные и нагрузочные тесты | Java 17, доступ к Docker; PostgreSQL запускается Testcontainers |
| Запуск готовых контейнеров | Docker, Compose v2 |

Gradle Wrapper входит в репозиторий.

```bash
git clone https://github.com/vkuryndin/CourseFlow.git
cd CourseFlow
```

При разработке CourseFlow использовалась следующая конфигурация:

| Компонент | Размещение |
|---|---|
| Java, Gradle, React, IntelliJ IDEA | Рабочая станция Windows |
| PostgreSQL 18 | Отдельная виртуальная машина Ubuntu, `192.168.179.129:5432` |
| Docker для Testcontainers | Та же Ubuntu VM, подключение через SSH (порт 22) |
| Backend dev | http://localhost:8080 |
| Frontend dev | http://localhost:5173 |
| Демонстрационная среда AWS | http://3.79.152.70:8081 |

При развёртывании на другой рабочей станции PostgreSQL и Docker могут работать локально, на отдельных серверах или на виртуальной машине. Адреса и учётные данные задаются конфигурацией среды. Для запуска готового приложения достаточно Docker Compose.

## 2. Подготовка PostgreSQL

Для локального запуска необходима база `courseflow` и пользователь PostgreSQL с правами владельца этой базы. В среде разработки CourseFlow используется роль `appuser`. При размещении БД на отдельном сервере она должна быть доступна с рабочей станции.

Hibernate создаёт и обновляет схему при запуске приложения. Для интеграционных и нагрузочных тестов Testcontainers автоматически запускает отдельный контейнер PostgreSQL. При использовании Docker Compose база и пользователь создаются контейнером PostgreSQL.

## 3. Конфигурация backend

| Файл | Назначение |
|---|---|
| `src/main/resources/application.yml` | Общие настройки, журналирование, Actuator, Tomcat |
| `src/main/resources/application-dev.yml` | Локальная разработка, SMTP, Swagger UI |
| `src/main/resources/application-aws.yml` | Контейнерное развёртывание |
| `src/test/resources/application-test.yml` | Тестовая конфигурация, временная БД, тестовые параметры SMTP и JWT |

Профиль `dev` используется по умолчанию. В `dev` и `aws` схема обновляется (`ddl-auto=update`), в `test` создаётся заново (`create`). Во всех профилях `open-in-view=false`.

### Обязательные параметры локального запуска

Для подключения backend в профиле `dev` задаются следующие переменные. Их значения зависят от среды разработки.

| Переменная | Назначение |
|---|---|
| `DB_URL` | JDBC-адрес БД, например `jdbc:postgresql://localhost:5432/courseflow` |
| `DB_USERNAME` | Пользователь PostgreSQL, например `appuser` |
| `DB_PASSWORD` | Пароль пользователя PostgreSQL |
| `JWT_SECRET_BASE64` | Ключ подписи JWT в Base64 (не менее 32 байт до кодирования) |
| `MAIL_USERNAME` | Gmail-адрес отправителя |
| `MAIL_APP_PASSWORD` | Пароль приложения Google |
| `COURSEFLOW_ADMIN_EMAIL` | Email системного администратора |
| `COURSEFLOW_ADMIN_PASSWORD` | Пароль системного администратора по умолчанию |

В профиле `dev` email и пароль администратора необходимы при создании системного администратора. При последующих запусках сохраняется существующая учётная запись; изменение переменной не меняет её пароль. Для Gmail SMTP используется пароль приложения Google. Отправка почты выполняется через `smtp.gmail.com:587` с STARTTLS; таймауты подключения, чтения и записи составляют 5 секунд.

Пример конфигурации локального запуска:

```dotenv
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://localhost:5432/courseflow
DB_USERNAME=appuser
DB_PASSWORD=<db-password>
JWT_SECRET_BASE64=<base64-secret>
COURSEFLOW_ADMIN_EMAIL=admin@courseflow.local
COURSEFLOW_ADMIN_PASSWORD=<admin-password>
MAIL_USERNAME=<gmail-address>
MAIL_APP_PASSWORD=<gmail-app-password>
```

Эти значения задаются в конфигурации IntelliJ IDEA или в переменных окружения процесса. Файл `.env` используется отдельно для Docker Compose. Для среды CourseFlow адрес БД — `jdbc:postgresql://192.168.179.129:5432/courseflow`.

### Дополнительные параметры backend

Следующие настройки имеют значения по умолчанию и при обычном локальном запуске не требуют ручной настройки.

| Переменная | Значение по умолчанию | Назначение |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Профиль приложения |
| `SERVER_PORT` | `8080` | HTTP-порт backend |
| `DB_POOL_SIZE` | `10` | Размер пула соединений в `dev` и `aws` |
| `DB_TRANSACTION_TIMEOUT` | `30s` | Таймаут транзакций |
| `COURSEFLOW_LOG_LEVEL` | `INFO` | Уровень логирования приложения |
| `COURSEFLOW_SQL_LOG_LEVEL` | `OFF` | Уровень логирования SQL |
| `COURSEFLOW_TRUSTED_PROXY_IP` | Не задан | Адрес доверенного reverse proxy |
| `COURSEFLOW_PASSWORD_RESET_URL` | `http://localhost:5173/reset-password` (dev) | Адрес страницы восстановления пароля |

В профиле `dev` разрешены источники запросов `http://localhost:5173` и `http://127.0.0.1:5173`. В профиле `aws` необходимо указать `COURSEFLOW_CORS_ALLOWED_ORIGINS` и `COURSEFLOW_PASSWORD_RESET_URL`; Docker Compose задаёт их автоматически для локального контейнерного запуска. При сетевом доступе значения настраиваются под адрес размещения (см. разделы 6–7).

Интеграционные и нагрузочные тесты используют профиль `test` с отдельными тестовыми параметрами JWT, SMTP и системного администратора. Адрес и учётные данные временной БД передаются Testcontainers; параметры рабочей БД для этих тестов не требуются.

## 4. IntelliJ IDEA и Testcontainers

В среде разработки CourseFlow используются три локальные конфигурации IntelliJ IDEA с Java 17. Каталог `.idea/` исключён из Git; конфигурации запуска создаются на рабочей станции с параметрами её окружения.

| Параметр | CourseFlowApplication | CourseFlow Integration Tests | CourseFlow Performance Tests |
|---|---|---|---|
| Тип | Spring Boot | JUnit | JUnit |
| Модуль | `CourseFlow.main` | `CourseFlow.test` | `CourseFlow.test` |
| Main class / пакет тестов | `org.courseflow.CourseFlowApplication` | `org.courseflow.integration` | `org.courseflow.performance` |
| Test kind | — | All in package | All in package |
| VM options | Стандартные | Стандартные | `-ea -Dcourseflow.performance=true` |
| Рабочий каталог | Корень проекта | `$MODULE_WORKING_DIR$` | `$MODULE_WORKING_DIR$` |
| Переменные | Параметры `dev` | Docker (см. ниже) | Docker (см. ниже) |

### Локальный Docker

Если Docker установлен на компьютере разработчика, Testcontainers автоматически использует его для запуска тестового контейнера PostgreSQL. Дополнительная настройка SSH-подключения не требуется. Такая же схема используется при выполнении тестов в GitHub Actions.

### Docker на удалённой виртуальной машине

В среде разработки CourseFlow Docker работает на Ubuntu VM `192.168.179.129`, а тесты запускаются на Windows. Класс `DockerSshTunnel` открывает локальный SSH-туннель к `/var/run/docker.sock` через JSch и освобождает его после завершения работы. Такой вариант подходит также для любого другого доступного Linux-хоста с Docker.

| Переменная | Значение в среде разработки | Назначение |
|---|---|---|
| `DOCKER_HOST` | `tcp://127.0.0.1:23750` | Локальный адрес туннеля |
| `SSH_HOST` | `192.168.179.129` | Адрес сервера Docker |
| `SSH_USER` | Пользователь Ubuntu | SSH-учётная запись с доступом к Docker socket |
| `SSH_PASSWORD` | Пароль SSH-пользователя | Аутентификация JSch |
| `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | `/var/run/docker.sock` | Путь к socket для служебных контейнеров |
| `TESTCONTAINERS_HOST_OVERRIDE` | `192.168.179.129` | Адрес хоста опубликованных портов контейнеров |

Для другого сервера указываются его адрес и учётные данные. Параметры одинаковы для интеграционных и нагрузочных тестов. SSH-туннель использует порт 22 на сервере и порт 23750 на рабочей станции; ключ сервера проверяется по локальному `~/.ssh/known_hosts`. Testcontainers запускает `postgres:18` и передаёт JDBC-адрес тестовому контексту Spring. Интеграционные и нагрузочные тесты с удалённым Docker запускаются последовательно, поскольку используют один локальный порт туннеля.

## 5. Локальный запуск

Backend запускается конфигурацией `CourseFlowApplication` в IntelliJ IDEA. Альтернативный запуск из терминала с заданными переменными окружения:

```powershell
.\gradlew.bat bootRun
```

В Linux/macOS используется `./gradlew bootRun`.

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

При локальном запуске Vite обращается к backend по адресу `http://localhost:8080`. Если backend размещён по другому адресу, его можно указать через `COURSEFLOW_BACKEND_URL` в `frontend/.env` (шаблон — `frontend/.env.example`). Vite обслуживает frontend на порту 5173 и проксирует `/api` и `/actuator`. Команда `npm run build` создаёт каталог `frontend/dist/` для размещения в Nginx.

Команды тестирования frontend и backend приведены в [TESTING.md](TESTING.md).

| Назначение | Адрес |
|---|---|
| Интерфейс | http://localhost:5173 |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Проверка готовности | http://localhost:8080/actuator/health/readiness |

При первом запуске `DemoDataInitializer` создаёт демонстрационные данные. Учётные данные генерируемых пользователей записываются в `~/.courseflow/demo-credentials-*.txt` в домашнем каталоге пользователя приложения.

## 6. Docker Compose

`docker-compose.yml` определяет проект `courseflow` с сервисами `postgres`, `backend`, `frontend`. Образы backend и frontend загружаются из GHCR; PostgreSQL — из Docker Hub.

Для запуска создайте `.env` по шаблону `.env.example`.

**Обязательные параметры:**

| Переменная | Назначение |
|---|---|
| `DB_PASSWORD` | Пароль PostgreSQL (база и роль `courseflow`) |
| `JWT_SECRET_BASE64` | Ключ подписи JWT |
| `COURSEFLOW_ADMIN_EMAIL` | Email системного администратора |
| `COURSEFLOW_ADMIN_PASSWORD` | Пароль системного администратора по умолчанию |
| `MAIL_USERNAME` | Gmail-адрес отправителя |
| `MAIL_APP_PASSWORD` | Пароль приложения Google |

Переменные `COURSEFLOW_ADMIN_EMAIL` и `COURSEFLOW_ADMIN_PASSWORD` обязательны для Docker Compose независимо от наличия администратора в базе.

Compose передаёт backend профиль `aws`, JDBC-адрес `jdbc:postgresql://postgres:5432/courseflow`, имя пользователя `courseflow`, порт 8080 и IP доверенного Nginx. В отличие от запуска из IDEA, отдельно задавать эти параметры не нужно.

**Дополнительные параметры Compose:**

| Переменная | Значение по умолчанию | Назначение |
|---|---|---|
| `COURSEFLOW_IMAGE_PREFIX` | `ghcr.io/vkuryndin/courseflow` | Префикс имён образов |
| `COURSEFLOW_BIND_ADDRESS` | `127.0.0.1` | Адрес публикации порта 8081; `0.0.0.0` для сетевого доступа |
| `COURSEFLOW_CORS_ALLOWED_ORIGINS` | `http://localhost:8081,http://127.0.0.1:8081` | Допустимые источники запросов (CORS) |
| `COURSEFLOW_PASSWORD_RESET_URL` | `http://localhost:8081/reset-password` | Адрес восстановления пароля |
| `COURSEFLOW_SUBNET` | `172.30.253.0/24` | Подсеть контейнеров |
| `COURSEFLOW_FRONTEND_IP` | `172.30.253.10` | Адрес frontend и доверенного reverse proxy |
| `COURSEFLOW_BACKEND_IP` | `172.30.253.11` | Адрес backend |
| `COURSEFLOW_DB_IP` | `172.30.253.12` | Адрес PostgreSQL |

При локальном контейнерном запуске эти значения можно оставить без изменений, если подсеть свободна. Для AWS адрес публикации, допустимые источники запросов и ссылка восстановления задаются по фактическому адресу размещения (раздел 7).

| Сервис | Образ / порт | Ресурсы |
|---|---|---|
| PostgreSQL | `postgres:18-alpine`, 5432 | 256 МБ; `shared_buffers=32MB`, `work_mem=1MB`, `maintenance_work_mem=16MB`, `max_connections=20` |
| Backend | `courseflow-backend:latest`, 8080 | 512 МБ; heap 64–256 МБ; SerialGC, до 40 потоков Tomcat; пул БД 10 |
| Frontend | `courseflow-frontend:latest`, 80 | Порт хоста 8081 |

Том `postgres-data` монтируется в `/var/lib/postgresql`, том `demo-credentials` — в `/home/courseflow/.courseflow`. Для контейнеров предусмотрены проверки состояния контейнеров, автоматический перезапуск и ротация логов (3 файла по 10 МБ). Все порты, кроме 8081, используются внутри сети Compose.

```bash
cp .env.example .env
# заполнить параметры .env
docker compose config --quiet
docker compose pull
docker compose up -d --wait
docker compose ps
```

Для остановки с сохранением данных используется `docker compose stop`. Обновление выполняется командами `docker compose pull` и `docker compose up -d --wait`. Системный администратор и демонстрационные данные сохраняются в томах; удаление томов (`down -v`) соответствует сбросу данных.

## 7. Развёртывание в AWS

Демонстрационная среда размещена на EC2 t3.micro: 2 vCPU, 1 ГБ оперативной памяти, Ubuntu 24.04, EBS 40 ГБ, swap 4,5 ГБ. Входящие соединения: SSH — 22, интерфейс приложения — 8081. Backend и PostgreSQL доступны внутри Compose. Демонстрационная среда предназначена для учебных данных. Текущее развёртывание использует HTTP. Переход на HTTPS указан в [направлениях дальнейшего развития](../README.md#дальнейшее-развитие).

На сервере с установленными Git, Docker и Compose:

```bash
git clone https://github.com/vkuryndin/CourseFlow.git ~/courseflow
cd ~/courseflow
```

Заполните `.env` по разделу 6. Для действующей демонстрационной среды используются адреса:

```dotenv
COURSEFLOW_BIND_ADDRESS=0.0.0.0
COURSEFLOW_CORS_ALLOWED_ORIGINS=http://3.79.152.70:8081
COURSEFLOW_PASSWORD_RESET_URL=http://3.79.152.70:8081/reset-password
```

При размещении на другом сервере используйте его адрес или доменное имя. Первый запуск выполняется по инструкции Docker Compose. Образы CourseFlow загружаются из GHCR.

### Доставка через GitHub Actions

[CourseFlow Deploy to AWS](https://github.com/vkuryndin/CourseFlow/actions/workflows/deploy.yml) запускается вручную для ветки `master` после успешного CI. Workflow рассчитан на пользователя `ubuntu` и каталог `$HOME/courseflow`.

Для доставки используется SSH-ключ развёртывания. На EC2 должен быть разрешён вход с этим ключом, а в GitHub Actions заданы параметры подключения и проверки ключа сервера.

| GitHub Secret | Значение |
|---|---|
| `AWS_HOST` | `3.79.152.70` или адрес целевого сервера |
| `AWS_SSH_KEY` | Приватный ключ развёртывания в формате OpenSSH |
| `AWS_SSH_KNOWN_HOSTS` | Строка `адрес ssh-ed25519 публичный-ключ-сервера` |

Секреты задаются в настройках репозитория GitHub Actions. Runner должен иметь сетевой доступ к SSH-серверу. Workflow загружает оба образа `latest`, сверяет их Git SHA, обновляет конфигурацию до соответствующего коммита и последовательно проверяет готовность backend и frontend. PostgreSQL при таком обновлении сохраняется.

## 8. Диагностика

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres
curl -fsS http://127.0.0.1:8081/actuator/health/readiness
docker stats --no-stream
free -h
vmstat 1 5
```

| Ситуация | Параметры диагностики |
|---|---|
| Ошибка запуска backend | JDBC URL, права роли, пароль БД, JWT-ключ, логи |
| Недоступность интерфейса | Порт 8081, bind-адрес, правила входящих соединений, готовность Nginx/backend |
| Ошибка отправки писем | Gmail SMTP 587, пароль приложения, STARTTLS, адрес восстановления |
| Ошибка Testcontainers | Доступ к Docker, SSH, known_hosts, параметры удалённого хоста, порт 23750 |
| Ошибка доставки | GitHub Secrets, сетевой доступ, ревизии образов, состояние контейнеров |

Команды тестирования приведены в [TESTING.md](TESTING.md).

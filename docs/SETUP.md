# CourseFlow — настройка и запуск

[README](../README.md) · [Тестирование](TESTING.md) · [API](API.md)

## 1. Требования и варианты размещения

Java 17, Git, Node.js 22 (минимальная версия Node.js — 20.19), npm, PostgreSQL 18. Gradle Wrapper входит в репозиторий. Docker и Compose v2 используются для контейнерного запуска; интеграционные и нагрузочные тесты требуют Docker.

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

Для локальной разработки создайте базу `courseflow` и роль с правами владельца базы. Пример SQL для нового экземпляра PostgreSQL:

```sql
CREATE ROLE appuser LOGIN;
\password appuser
CREATE DATABASE courseflow OWNER appuser;
```

Для подключения с другой машины настройте прослушивание сетевого интерфейса PostgreSQL, правила `pg_hba.conf` и доступ к порту 5432. Hibernate формирует таблицы автоматически при запуске. Для интеграционных и нагрузочных тестов PostgreSQL создаётся автоматически средствами Testcontainers.

## 3. Конфигурация backend

| Файл | Назначение |
|---|---|
| `src/main/resources/application.yml` | Общие настройки, журналирование, Actuator, Tomcat |
| `src/main/resources/application-dev.yml` | Локальная разработка, SMTP, Swagger UI |
| `src/main/resources/application-aws.yml` | Контейнерное развёртывание |
| `src/test/resources/application-test.yml` | Тестовая конфигурация, временная БД, тестовые параметры SMTP и JWT |

Профиль `dev` используется по умолчанию. В `dev` и `aws` схема обновляется (`ddl-auto=update`), в `test` создаётся заново (`create`). Во всех профилях `open-in-view=false`.

### Переменные окружения приложения

| Переменная | Назначение и пример |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` или `aws` |
| `DB_URL` | JDBC-адрес: `jdbc:postgresql://localhost:5432/courseflow`; для VM — `jdbc:postgresql://192.168.179.129:5432/courseflow` |
| `DB_USERNAME` | Роль PostgreSQL, например `appuser` |
| `DB_PASSWORD` | Пароль роли PostgreSQL |
| `JWT_SECRET_BASE64` | Ключ подписи JWT в Base64, минимум 32 байта до кодирования |
| `COURSEFLOW_ADMIN_EMAIL` | Email системного администратора, создаваемого при первом запуске |
| `COURSEFLOW_ADMIN_PASSWORD` | Начальный пароль системного администратора |
| `MAIL_USERNAME` | Gmail-адрес отправителя |
| `MAIL_APP_PASSWORD` | Пароль приложения Gmail для SMTP |
| `COURSEFLOW_PASSWORD_RESET_URL` | Страница восстановления пароля; dev по умолчанию `http://localhost:5173/reset-password` |
| `COURSEFLOW_CORS_ALLOWED_ORIGINS` | Разрешённые адреса frontend через запятую (профиль `aws`) |
| `COURSEFLOW_TRUSTED_PROXY_IP` | IP доверенного reverse proxy, по умолчанию пустой |
| `DB_POOL_SIZE` | Максимальный размер пула, по умолчанию 10 |
| `DB_TRANSACTION_TIMEOUT` | Таймаут транзакций, по умолчанию `30s` |
| `SERVER_PORT` | HTTP-порт backend, по умолчанию 8080 |
| `COURSEFLOW_LOG_LEVEL` | Уровень журналирования приложения, `INFO` |
| `COURSEFLOW_SQL_LOG_LEVEL` | Журнал SQL, `OFF` |

SMTP настроен на `smtp.gmail.com:587`, STARTTLS, таймауты 5 секунд. Для Gmail используется пароль приложения, создаваемый в настройках Google-аккаунта с двухэтапной аутентификацией. В профиле `dev` разрешены `http://localhost:5173` и `http://127.0.0.1:5173`; дополнительные адреса задаются свойством `courseflow.cors.allowed-origins`.

Пример переменных для PowerShell (локальная БД):

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/courseflow'
$env:DB_USERNAME = 'appuser'
$env:DB_PASSWORD = '<db-password>'
$env:JWT_SECRET_BASE64 = '<base64-secret>'
$env:COURSEFLOW_ADMIN_EMAIL = 'admin@courseflow.local'
$env:COURSEFLOW_ADMIN_PASSWORD = '1234567890!qw'
$env:MAIL_USERNAME = '<gmail-address>'
$env:MAIL_APP_PASSWORD = '<gmail-app-password>'
```

Значения в угловых скобках заменяются параметрами среды. Для генерации JWT-ключа можно использовать `openssl rand -base64 32`. При повторном запуске сохраняется существующий системный администратор; изменение его пароля выполняется средствами приложения.

## 4. IntelliJ IDEA и Testcontainers

В проекте предусмотрены три конфигурации запуска. Используется Java 17; переменные окружения передаются из локальных настроек IDE или среды процесса.

| Параметр | CourseFlowApplication | CourseFlow Integration Tests | CourseFlow Performance Tests |
|---|---|---|---|
| Тип | Spring Boot | JUnit | JUnit |
| Модуль | `CourseFlow.main` | `CourseFlow.test` | `CourseFlow.test` |
| Main class / пакет тестов | `org.courseflow.CourseFlowApplication` | `org.courseflow.integration` | `org.courseflow.performance` |
| Test kind | — | All in package | All in package |
| VM options | Стандартные | `-ea` | `-ea -Dcourseflow.performance=true` |
| Рабочий каталог | Корень проекта | `$MODULE_WORKING_DIR$` | `$MODULE_WORKING_DIR$` |
| Переменные | Параметры `dev` | Docker (см. ниже) | Docker (см. ниже) |

### Локальный Docker

При наличии Docker на рабочей станции Testcontainers использует стандартный механизм обнаружения Docker. Аналогичная конфигурация используется на GitHub-hosted runners. Переменные `SSH_HOST`, `SSH_USER`, `SSH_PASSWORD` и параметры переопределения удалённого Docker в этом варианте отсутствуют.

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

Для другого сервера заменяются `SSH_HOST`, `TESTCONTAINERS_HOST_OVERRIDE` и учётные данные. Порт SSH в `DockerSshTunnel` — 22, локальный порт — 23750. Проверка ключа SSH-сервера выполняется по `~/.ssh/known_hosts` пользователя, запускающего JVM. Параметры одинаковы для интеграционных и нагрузочных тестов. Testcontainers запускает образ `postgres:18` и передаёт JDBC-адрес тестовому контексту Spring.

## 5. Локальный запуск

Backend (после настройки переменных из раздела 3):

```powershell
.\gradlew.bat bootRun
```

Для Linux/macOS используется `bash ./gradlew bootRun`. Для запуска из JAR выполните задачу `bootJar` и передайте исполняемый файл из `build/libs/` команде `java -jar`.

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

Переменная `COURSEFLOW_BACKEND_URL` в `frontend/.env` определяет адрес backend для Vite. Шаблон находится в `frontend/.env.example`, значение по умолчанию — `http://localhost:8080`. Vite обслуживает frontend на порту 5173 и проксирует `/api` и `/actuator`. Команда `npm run build` создаёт каталог `frontend/dist/` для размещения в Nginx.

Проверки frontend из каталога `frontend`:

```bash
npm run lint
npm test
npm run test:bundle
npm run check:contract
npx playwright install chromium
npm run test:e2e:production
npm run report:bundle
```

`npm test` включает все семь архитектурных проверок и Vitest. `test:e2e:production` сначала выполняет `npm run build`, затем запускает Playwright на порту 4173; при ошибке компиляции браузерные тесты не запускаются. Для Linux CI установка Chromium выполняется с `--with-deps`. `npm run test:e2e` проверяет dev-сервер на порту 5173. Оба режима используют один набор сценариев с подменой ответов API.

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

Создайте `.env` из `.env.example` и заполните параметры:

| Переменная | Назначение / значение по умолчанию |
|---|---|
| `COURSEFLOW_IMAGE_PREFIX` | `ghcr.io/vkuryndin/courseflow` |
| `DB_PASSWORD` | Пароль PostgreSQL (БД и роль `courseflow`) |
| `JWT_SECRET_BASE64` | Ключ подписи JWT |
| `COURSEFLOW_ADMIN_EMAIL` | Email системного администратора |
| `COURSEFLOW_ADMIN_PASSWORD` | Начальный пароль системного администратора |
| `MAIL_USERNAME` | Gmail-адрес отправителя |
| `MAIL_APP_PASSWORD` | Пароль приложения Gmail |
| `COURSEFLOW_BIND_ADDRESS` | `127.0.0.1` для локального доступа; `0.0.0.0` для доступа по сети |
| `COURSEFLOW_CORS_ALLOWED_ORIGINS` | `http://localhost:8081,http://127.0.0.1:8081` |
| `COURSEFLOW_PASSWORD_RESET_URL` | `http://localhost:8081/reset-password` |
| `COURSEFLOW_SUBNET` | `172.30.253.0/24` |
| `COURSEFLOW_FRONTEND_IP` | `172.30.253.10` |
| `COURSEFLOW_BACKEND_IP` | `172.30.253.11` |
| `COURSEFLOW_DB_IP` | `172.30.253.12` |

Статические адреса должны принадлежать выбранной свободной подсети Docker. IP frontend используется как доверенный адрес Nginx для backend.

| Сервис | Образ / порт | Ресурсы |
|---|---|---|
| PostgreSQL | `postgres:18-alpine`, 5432 | 256 МиБ; `shared_buffers=32MB`, `work_mem=1MB`, `maintenance_work_mem=16MB`, `max_connections=20` |
| Backend | `courseflow-backend:latest`, 8080 | 512 МиБ; heap 64–256 МиБ; SerialGC, до 40 потоков Tomcat; пул БД 10 |
| Frontend | `courseflow-frontend:latest`, 80 | Порт хоста 8081 |

Том `postgres-data` монтируется в `/var/lib/postgresql`, том `demo-credentials` — в `/home/courseflow/.courseflow`. Для контейнеров предусмотрены health checks, автоматический перезапуск и ротация логов (3 файла по 10 МБ). Все порты, кроме 8081, используются внутри сети Compose.

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

Демонстрационная среда размещена на EC2 t3.micro: 2 vCPU, 1 ГиБ RAM, Ubuntu 24.04, EBS 40 ГБ, swap 4,5 ГиБ. Входящие соединения: SSH — 22, интерфейс приложения — 8081. Backend и PostgreSQL доступны внутри Compose.

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

При размещении на другом сервере используйте его адрес или доменное имя. Первый запуск выполняется по инструкции Docker Compose. Образы CourseFlow в GHCR доступны для публичной загрузки.

### Доставка через GitHub Actions

[CourseFlow Deploy to AWS](https://github.com/vkuryndin/CourseFlow/actions/workflows/deploy.yml) запускается вручную для ветки `master` после успешного CI. Workflow рассчитан на пользователя `ubuntu` и каталог `$HOME/courseflow`.

Для автоматизации используется отдельная пара SSH-ключей Ed25519; публичный ключ добавляется в `~/.ssh/authorized_keys` пользователя сервера. Публичный ключ SSH-сервера (`/etc/ssh/ssh_host_ed25519_key.pub`) используется для проверки его подлинности.

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
| HTTP 429 при сборке Gradle | Ограничение запросов Maven Central; повторный запуск неудачного job |

Настройки с рабочими паролями и ключами хранятся локально либо в секретах среды. Команды тестирования приведены в [TESTING.md](TESTING.md).

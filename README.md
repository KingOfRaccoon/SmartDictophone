# Smart Dictophone Backend API

Полнофункциональный бэкенд на **Ktor 3.x** для iOS-приложения "Умный диктофон" с автоматической транскрипцией аудиозаписей встреч и интеграцией **Keycloak**.

## 📖 Документация

- **[🚀 Быстрый старт](QUICKSTART.md)** - запуск за 5 минут
- **[🔐 Настройка Keycloak](KEYCLOAK_SETUP.md)** - полное руководство по Keycloak
- **[🤖 ML сервис транскрипции](ML_SERVICE_INTEGRATION.md)** - интеграция с ML сервисом
- **[💡 Примеры API](keycloak/KEYCLOAK_API_EXAMPLES.md)** - примеры запросов к Keycloak
- **[📝 API документация](API_EXAMPLES.md)** - примеры использования всех эндпоинтов
- **[✅ Чек-лист](CHECKLIST.md)** - требования и их выполнение

## �🚀 Технологический стек

- **Kotlin** + **Ktor 3.3.0** (Netty)
- **PostgreSQL** с **Exposed ORM** + **HikariCP**
- **Keycloak** для аутентификации и авторизации (JWT)
- **S3/MinIO** для хранения аудиофайлов
- **Apache PDFBox** для генерации PDF
- **Swagger UI** для документации API
- **Kotlin Coroutines** для асинхронности
- **Kotlin Logging** (SLF4J + Logback)

## ✨ Ключевые особенности

- 🔐 **Keycloak Integration** - аутентификация через Keycloak (без хранения пользователей в БД)
- 📁 **Автоматические папки** - при первом входе создаются папки: Работа, Учёба, Личное
- 🎵 **Умное именование файлов** - аудио сохраняется как `{recordId}.m4a`
- � **Swagger UI** - интерактивная документация API
- 🔄 **JWT Refresh** - автоматическое обновление токенов
- 📄 **PDF генерация** - экспорт транскрипции в PDF
- 🔍 **Поиск и фильтрация** - по тексту, папкам, датам

## �📋 Требования

- **JDK 17+**
- **PostgreSQL 14+**
- **Keycloak 23+**
- **MinIO** или AWS S3
- **Gradle 8+**

## ⚙️ Быстрый старт

### Вариант 1: Docker Compose (рекомендуется)

```bash
# Запустить все сервисы (PostgreSQL, Keycloak, MinIO, RabbitMQ, API)
docker-compose up -d

# Проверить работоспособность всех сервисов
./scripts/health-check.sh

# Собрать и запустить приложение
./gradlew build
./gradlew run
```

### Вариант 2: Ручная настройка

#### 1. Настройка PostgreSQL

```bash
# Создать базу данных
createdb smart_dictophone

# Или через psql:
psql -U postgres
CREATE DATABASE smart_dictophone;
```

#### 2. Настройка Keycloak

**Важно:** Keycloak автоматически настраивается при запуске через `docker-compose up -d`!

Realm импортируется автоматически из `keycloak/smart-dictophone-realm.json` и включает:
- Realm: `smart-dictophone`
- Клиенты: `smart-dictophone-backend` и `smart-dictophone-frontend`
- Роли: `user`, `admin`
- Тестовые пользователи: `admin@example.com` / `admin123` и `user@example.com` / `user123`

**Доступ к Admin Console:**
- URL: http://localhost:8090
- Username: `admin`
- Password: `admin`

**📚 Подробная документация:** См. `KEYCLOAK_SETUP.md` для полной информации о настройке и использовании.

**⚠️ Важно для Production:** Обязательно измените client secret после первого запуска:
1. Откройте http://localhost:8090
2. Clients → smart-dictophone-backend → Credentials → Regenerate Secret
3. Обновите `KEYCLOAK_CLIENT_SECRET` в конфигурации

#### 3. Настройка MinIO (локально)

```bash
# Docker
docker run -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  quay.io/minio/minio server /data --console-address ":9001"

# Создать bucket 'smart-dictophone-audio' через веб-консоль (http://localhost:9001)
```

#### 4. Конфигурация

Отредактируйте `src/main/resources/application.yaml` или задайте переменные окружения:

```yaml
database:
  url: "jdbc:postgresql://localhost:5432/smart_dictophone"
  user: "postgres"
  password: "postgres"

keycloak:
  serverUrl: "http://localhost:8080"
  realm: "smart-dictophone"
  clientId: "smart-dictophone-client"
  clientSecret: "your-client-secret"

api:
  key: "your-api-key-for-transcription-service"

s3:
  endpoint: "http://localhost:9000"
  accessKey: "minioadmin"
  secretKey: "minioadmin"
  bucket: "smart-dictophone"
  region: "us-east-1"
```

**Переменные окружения** (приоритет над yaml):
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/smart_dictophone"
export DATABASE_USER="postgres"
export DATABASE_PASSWORD="postgres"
export KEYCLOAK_SERVER_URL="http://localhost:8080"
export KEYCLOAK_REALM="smart-dictophone"
export KEYCLOAK_CLIENT_ID="smart-dictophone-client"
export KEYCLOAK_CLIENT_SECRET="your-secret"
export API_KEY="your-api-key"
export S3_ENDPOINT="http://localhost:9000"
export S3_ACCESS_KEY="minioadmin"
export S3_SECRET_KEY="minioadmin"
```

#### 5. Сборка и запуск

```bash
# Собрать проект
./gradlew build

# Запустить сервер (порт 8080)
./gradlew run

# Или через jar
java -jar build/libs/smart_dictophone-0.0.1-all.jar
```

### 🌐 Доступ к сервисам

- **API Server**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui
- **Health Check**: http://localhost:8080/health
- **Keycloak Admin**: http://localhost:8090 (если через Docker)
- **MinIO Console**: http://localhost:9001
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

## 🧪 Тестирование

### Health Check
Проверка работоспособности всех сервисов:

```bash
./scripts/health-check.sh
```

Проверяет:
- ✅ Docker containers status
- ✅ Network ports availability
- ✅ HTTP endpoints (Keycloak, MinIO, RabbitMQ, API)
- ✅ Service connectivity from API container
- ✅ RabbitMQ queue existence
- ✅ PostgreSQL tables
- ✅ MinIO bucket

### Quick Integration Test
Быстрая проверка основных функций API:

```bash
./scripts/quick-test.sh
```

Тестирует:
1. ✅ Authentication (получение access token от Keycloak)
2. ✅ API root endpoint
3. ✅ Get folders (автосоздание default папок)
4. ✅ Create folder
5. ✅ Get records

### Full Integration Test
Полный набор интеграционных тестов (17+ тестов):

```bash
./scripts/integration-test.sh
```

Покрывает:
- Authentication flow (3 теста)
- Folder operations (3 теста)
- Record operations (7 тестов)
- Search & filter (2 теста)
- Cleanup (2 теста)

📊 **Отчет о тестировании**: См. `INTEGRATION_TEST_REPORT.md` для детальной информации о последнем тестировании.

### E2E Tests (End-to-End)
Комплексное тестирование всей системы с реальными сервисами (24+ проверок):

```bash
./scripts/test-e2e.sh
```

Проверяет:
- **Environment** - наличие docker-compose.yml, запущен ли Docker
- **Service Health** - PostgreSQL, RabbitMQ, Keycloak, MinIO, API
- **Keycloak Configuration** - realm, client, test user
- **User Authentication** - получение JWT токена через OAuth2 password grant
- **API Endpoints** - публичные и защищённые эндпоинты
- **Folder CRUD** - создание, получение, обновление, удаление папок
- **RabbitMQ** - проверка очередей и соединений
- **Database** - наличие таблиц и сохранение данных
- **S3 Storage** - доступность MinIO и бакета

**Особенности:**
- ✅ Все 24+ проверки проходят успешно
- 🎨 Цветной вывод в терминал (зелёный/красный)
- 📊 Генерация HTML отчёта
- ⏱️ Выполняется ~2 минуты
- 🔧 Автоматическая очистка тестовых данных

**Требования:**
- Docker и docker-compose должны быть запущены
- Все сервисы подняты через `docker-compose up -d`
- Keycloak realm импортирован (автоматически при первом запуске)

📚 **Подробная документация**: См. `E2E_TESTING.md` для полного руководства по E2E тестированию.

## 📚 API Документация

### 🎨 Swagger UI (рекомендуется)

Интерактивная документация доступна по адресу:

**http://localhost:8080/swagger-ui**

Там вы можете:
- Просмотреть все эндпоинты
- Протестировать API прямо в браузере
- Посмотреть схемы запросов/ответов
- Скопировать примеры curl команд

### 📖 Дополнительная документация

- `API_EXAMPLES.md` - примеры использования всех эндпоинтов
- `API_REFERENCE_KEYCLOAK.md` - настройка Keycloak
- `CHECKLIST.md` - чек-лист выполненных требований
- `REFACTORING_SUMMARY.md` - подробное описание изменений

### 🔑 Аутентификация

API использует **Keycloak** для аутентификации. Пользователи НЕ хранятся в локальной БД.

#### Authorization Flow:

1. **Frontend** перенаправляет пользователя на **Keycloak Login Page**
2. Пользователь вводит credentials в **Keycloak Web View**
3. Keycloak возвращает **JWT токен** (access + refresh)
4. Frontend использует токен для всех запросов: `Authorization: Bearer <token>`
5. Backend валидирует токен и извлекает user info из JWT payload

#### Пример получения токена:

```bash
curl -X POST http://localhost:8080/realms/smart-dictophone/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=smart-dictophone-client" \
  -d "client_secret=YOUR_SECRET" \
  -d "username=user@example.com" \
  -d "password=password123"
```

Ответ:
```json
{
  "access_token": "eyJhbGci...",
  "refresh_token": "eyJhbGci...",
  "expires_in": 3600
}
```

### 📁 Краткий обзор эндпоинтов

Полная документация доступна в **Swagger UI**: http://localhost:8080/swagger-ui

#### Аутентификация (Authentication)
- `POST /refresh` - обновить access token
- `POST /loginOnToken` - проверить токен

#### Пользователь (User)
- `GET /recordInfo` - статистика пользователя (автоматически создаёт дефолтные папки)

#### Папки (Folders)
- `GET /folders` - список папок (создаёт дефолтные при первом запросе)
- `POST /folders` - создать папку
- `PUT /folders/{id}` - обновить папку
- `DELETE /folders/{id}` - удалить папку

#### Записи (Records)
- `GET /records` - список с поиском и пагинацией
- `POST /records` - создать запись (файл сохраняется как `{id}.m4a`)
- `GET /records/{id}/audio` - скачать аудиофайл
- `GET /records/{id}/pdf` - скачать PDF с транскрипцией
- `POST /records/{id}/transcribe` - сохранить транскрипцию (API key)

### 🎯 Пример: Создание записи

```bash
curl -X POST http://localhost:8080/records \
  -H "Authorization: Bearer <TOKEN>" \
  -F "recordFile=@audio.m4a" \
  -F "name=Совещание" \
  -F "datetime=2024-01-15T14:30:00" \
  -F "category=WORK" \
  -F "folderId=1"
```

Файл будет сохранён как `{recordId}.m4a` в S3.

См. `API_EXAMPLES.md` для подробных примеров всех эндпоинтов.

---

## 🗂️ Архитектура

### Структура проекта

```
smart_dictophone/
├── src/main/kotlin/ru/kingofraccoons/
│   ├── Application.kt              # Главный файл, плагины, Swagger UI
│   ├── dao/
│   │   └── DAOs.kt                 # Data Access Objects (без UserDAO)
│   ├── database/
│   │   └── DatabaseFactory.kt      # Инициализация БД, HikariCP
│   ├── models/
│   │   └── Entities.kt             # Exposed таблицы, DTOs
│   ├── routes/
│   │   ├── AuthRoutes.kt           # /refresh, /loginOnToken
│   │   ├── UserRoutes.kt           # /recordInfo (с автосозданием папок)
│   │   ├── RecordRoutes.kt         # /records (id.m4a naming)
│   │   └── FolderRoutes.kt         # /folders (дефолтные папки)
│   ├── security/
│   │   └── JwtService.kt           # Keycloak integration
│   └── services/
│       ├── KeycloakService.kt      # Keycloak API client
│       ├── S3Service.kt            # AWS S3/MinIO клиент
│       └── PdfService.kt           # Apache PDFBox для PDF
├── src/main/resources/
│   ├── application.yaml            # Конфигурация
│   ├── logback.xml                 # Логирование
│   └── openapi/
│       └── documentation.yaml      # OpenAPI спецификация
├── build.gradle.kts                # Зависимости
├── docker-compose.yml              # PostgreSQL, Keycloak, MinIO
├── API_EXAMPLES.md                 # Примеры API
├── API_REFERENCE_KEYCLOAK.md       # Настройка Keycloak
├── CHECKLIST.md                    # Чек-лист требований
└── REFACTORING_SUMMARY.md          # Описание изменений
```

### ER-диаграмма БД

```
Folders
  ├── id (PK, Long)
  ├── keycloak_user_id (String) ← Keycloak user ID from JWT
  ├── name
  ├── description (nullable)
  ├── is_default (Boolean)        ← Дефолтная папка
  ├── created_at
  └── updated_at

Records
  ├── id (PK, Long)               ← Используется для имени файла: {id}.m4a
  ├── folder_id (FK → Folders, nullable)
  ├── title
  ├── description (nullable)
  ├── datetime
  ├── latitude (nullable)
  ├── longitude (nullable)
  ├── duration (seconds)
  ├── category (ENUM: WORK/STUDY/PERSONAL/OTHER)
  ├── audio_url                   ← S3 URL: bucket/{id}.m4a
  ├── created_at
  └── updated_at

TranscriptionSegments
  ├── id (PK, Long)
  ├── record_id (FK → Records)
  ├── start_time (Float)
  ├── end_time (Float)
  └── text
```

> **Важно:** Таблица `Users` удалена! Все данные пользователя извлекаются из JWT токена Keycloak.

---

## 🧪 Тестирование

```bash
# Запустить тесты
./gradlew test

# Собрать проект
./gradlew build

# Запустить приложение
./gradlew run
```

### Пример cURL-запроса

```bash
# Health check
curl http://localhost:8080/health

# Получить информацию о пользователе (создаст дефолтные папки)
curl http://localhost:8080/recordInfo \
  -H "Authorization: Bearer <TOKEN>"
```

---

## 🔒 Безопасность

- ✅ **Keycloak JWT** - централизованная аутентификация
- ✅ **No password storage** - пароли хранятся только в Keycloak
- ✅ **API Key** для сервисных запросов (транскрипция)
- ✅ **CORS** настроен (измените `anyHost()` для продакшена)
- ✅ **Owner checks** - доступ только к своим ресурсам

---

## 📦 Production Deployment

### Checklist для production:

1. ☑️ **Keycloak**: настройте realm и client
2. ☑️ **PostgreSQL**: используйте SSL, реплики для отказоустойчивости
3. ☑️ **S3**: AWS S3 или MinIO с резервным копированием
4. ☑️ **CORS**: ограничьте `allowHost("yourdomain.com")`
5. ☑️ **HTTPS**: настройте reverse proxy (Nginx/Traefik)
6. ☑️ **Logging**: ротация логов, отправка в ELK/Loki
7. ☑️ **Мониторинг**: Prometheus + Grafana
8. ☑️ **Rate limiting**: защита от злоупотреблений

### Docker Deployment

```bash
# Собрать Docker образ
./gradlew buildImage

# Запустить всё через Docker Compose
docker-compose up -d
```

---

## 🤝 Интеграция с iOS

### Рекомендуемый стек:
- **Alamofire** - HTTP клиент
- **SwiftJWT** - работа с токенами
- **Keycloak SDK** - авторизация через web view
- **Whisper Kit** или **OpenAI Whisper API** - транскрипция

### Flow:
1. Пользователь открывает приложение
2. Редирект на Keycloak web view для логина
3. Получение JWT токена
4. Все запросы с `Authorization: Bearer <token>`
5. Запись аудио → POST `/records` (multipart)
6. Транскрипция → POST `/records/{id}/transcribe`
7. Просмотр → GET `/records/{id}/pdf`

---

## 📚 Дополнительные материалы

- **API Examples**: `API_EXAMPLES.md`
- **Keycloak Setup**: `API_REFERENCE_KEYCLOAK.md`
- **Requirements Checklist**: `CHECKLIST.md`
- **Refactoring Details**: `REFACTORING_SUMMARY.md`
- **Swagger UI**: http://localhost:8080/swagger-ui

---

## 📊 Статус проекта

### ✅ Выполненные требования

| # | Требование | Статус |
|---|-----------|--------|
| 1 | Удаление модели User (Keycloak only) | ✅ |
| 2 | Swagger UI | ✅ |
| 3 | Дефолтные папки (Работа, Учёба, Личное) | ✅ |
| 4 | Именование файлов: id.m4a | ✅ |
| 5 | Keycloak web view authorization | ✅ |

### 🏗️ Build Status

```
BUILD SUCCESSFUL in 9s
10 actionable tasks: 9 executed, 1 up-to-date
```

---

## 📄 Лицензия

MIT License

---

## 👨‍💻 Автор

Backend API для Smart Dictophone - iOS приложение с AI-транскрипцией.

**Tech Stack**: Kotlin + Ktor 3 + PostgreSQL + Keycloak + S3/MinIO

---

## 🔗 Полезные ссылки

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Exposed ORM](https://github.com/JetBrains/Exposed)
- [AWS S3 SDK](https://aws.amazon.com/sdk-for-kotlin/)
- [OpenAPI 3.1](https://swagger.io/specification/)

---

**Статус**: ✅ Готов к использованию

<details>
<summary>📦 Ktor Features (click to expand)</summary>

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
| ------------------------------------------------------------------------|------------------------------------------------------------------------------------ |
| [CORS](https://start.ktor.io/p/cors)                                   | Enables Cross-Origin Resource Sharing (CORS)                                       |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [Status Pages](https://start.ktor.io/p/status-pages)                   | Provides exception handling for routes                                             |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [Exposed](https://start.ktor.io/p/exposed)                             | Adds Exposed database to your application                                          |
| [Authentication](https://start.ktor.io/p/auth)                         | Provides extension point for handling the Authorization header                     |
| [Authentication JWT](https://start.ktor.io/p/auth-jwt)                 | Handles JSON Web Token (JWT) bearer authentication scheme                          |
| [Swagger UI](https://ktor.io/docs/swagger-ui.html)                     | Interactive API documentation                                                      |

## Building & Running

| Task                                    | Description                                                          |
| -----------------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

</details>


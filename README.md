# Smart Dictophone Backend API

Полнофункциональный бэкенд на **Ktor 3.x** для iOS-приложения "Умный диктофон" с автоматической транскрипцией аудиозаписей встреч.

## 🚀 Технологический стек

- **Kotlin** + **Ktor 3.3.0** (Netty)
- **PostgreSQL** с **Exposed ORM** + **HikariCP**
- **JWT Authentication** (Access/Refresh токены)
- **S3/MinIO** для хранения аудиофайлов
- **Apache PDFBox** для генерации PDF
- **BCrypt** для хеширования паролей
- **Kotlin Coroutines** для асинхронности
- **Kotlin Logging** (SLF4J + Logback)

## 📋 Требования

- **JDK 17+**
- **PostgreSQL 14+**
- **MinIO** или AWS S3
- **Gradle 8+**

## ⚙️ Установка и запуск

### 1. Настройка PostgreSQL

```bash
# Создать базу данных
createdb smart_dictophone

# Или через psql:
psql -U postgres
CREATE DATABASE smart_dictophone;
```

### 2. Настройка MinIO (локально)

```bash
# Docker
docker run -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  quay.io/minio/minio server /data --console-address ":9001"

# Создать bucket 'smart-dictophone-audio' через веб-консоль (http://localhost:9001)
```

### 3. Конфигурация

Отредактируйте `src/main/resources/application.yaml` или задайте переменные окружения:

```yaml
database:
  url: "jdbc:postgresql://localhost:5432/smart_dictophone"
  user: "postgres"
  password: "postgres"

jwt:
  secret: "your-256-bit-secret-key-change-in-production"

api:
  key: "your-api-key-for-whisper-ml"

s3:
  endpoint: "http://localhost:9000"
  accessKey: "minioadmin"
  secretKey: "minioadmin"
  bucket: "smart-dictophone-audio"
```

**Переменные окружения** (приоритет над yaml):
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/smart_dictophone"
export DATABASE_USER="postgres"
export DATABASE_PASSWORD="postgres"
export JWT_SECRET="your-secret-key"
export API_KEY="your-api-key"
export S3_ENDPOINT="http://localhost:9000"
export S3_ACCESS_KEY="minioadmin"
export S3_SECRET_KEY="minioadmin"
```

### 4. Сборка и запуск

```bash
# Собрать проект
./gradlew build

# Запустить сервер (порт 8080)
./gradlew run

# Или через jar
java -jar build/libs/smart_dictophone-0.0.1-all.jar
```

Сервер доступен на `http://localhost:8080`

## 📚 API Документация

### Аутентификация

#### POST `/login`
**Вход в систему**

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "id": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Errors:** `400`, `401`

---

#### POST `/register`
**Регистрация нового пользователя**

**Request:**
```json
{
  "email": "newuser@example.com",
  "password": "password123",
  "fullname": "Jane Smith"
}
```

**Response (201):** Аналогично `/login`

**Errors:** `400`, `409` (email уже существует)

---

#### POST `/loginOnToken`
**Обновление токенов через Bearer токен**

**Headers:**
```
Authorization: Bearer <access_or_refresh_token>
```

**Response (200):** Аналогично `/login`

**Errors:** `400`, `401`

---

### Пользователь

#### GET `/recordInfo`
**Информация о пользователе и статистика**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
{
  "id": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "countRecords": 42,
  "countMinutes": 240
}
```

**Errors:** `401`, `404`

---

### Записи (Records)

#### GET `/records`
**Список записей с пагинацией и фильтрацией**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Query Parameters:**
- `search` (optional): поиск по названию/описанию
- `folderId` (optional): фильтр по папке
- `page` (optional, default=0): номер страницы
- `size` (optional, default=20): размер страницы

**Response (200):**
```json
{
  "content": [
    {
      "id": 1,
      "folderId": 3,
      "title": "Meeting with Team",
      "description": null,
      "datetime": "2025-10-09T14:30:00",
      "latitude": 37.7749,
      "longitude": -122.4194,
      "duration": 1800,
      "category": "Work",
      "audioUrl": "http://localhost:9000/smart-dictophone-audio/audio/uuid-meeting.m4a",
      "createdAt": "2025-10-09T10:00:00",
      "updatedAt": "2025-10-09T10:00:00"
    }
  ],
  "totalElements": 42,
  "totalPages": 3
}
```

**Errors:** `401`

---

#### POST `/records`
**Создание новой записи с загрузкой аудио**

**Headers:**
```
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

**Form Data:**
- `datetime` (required): ISO8601 datetime (e.g., `2025-10-09T14:30:00`)
- `name` (required): название записи
- `category` (required): `Work`, `Study`, или `Personal`
- `recordFile` (required): аудиофайл (binary, m4a)
- `folderId` (optional): ID папки
- `place` (optional): координаты в формате `lat,lng` (e.g., `37.7749,-122.4194`)

**Response (201):** Record object

**Errors:** `400`, `401`

---

#### GET `/records/{id}/audio`
**Скачать аудиофайл записи**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):** Binary audio/m4a

**Errors:** `401`, `404`

---

#### GET `/records/{id}/pdf`
**Скачать PDF с транскрипцией**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):** Binary application/pdf

**Errors:** `401`, `404` (нет транскрипции)

---

#### POST `/records/{id}/transcribe`
**Сохранить транскрипцию записи (из Whisper ML)**

**Headers:**
```
X-API-Key: <your-api-key>
```

**Request:**
```json
{
  "segments": [
    {
      "start": 0.0,
      "end": 5.2,
      "text": "Hello, this is the meeting transcript."
    },
    {
      "start": 5.2,
      "end": 10.8,
      "text": "We will discuss the project roadmap."
    }
  ]
}
```

**Response (200):**
```json
{
  "message": "Transcription saved successfully"
}
```

**Errors:** `400`, `401`, `404`

---

### Папки (Folders)

#### GET `/folders`
**Список всех папок пользователя**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200):**
```json
[
  {
    "id": 1,
    "userId": 1,
    "name": "Work Meetings",
    "description": "All work-related meetings",
    "createdAt": "2025-10-01T10:00:00",
    "updatedAt": "2025-10-01T10:00:00"
  }
]
```

**Errors:** `401`

---

#### POST `/folders`
**Создать новую папку**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Request:**
```json
{
  "name": "Study Notes",
  "description": "University lectures"
}
```

**Response (201):** Folder object

**Errors:** `400`, `401`

---

#### PUT `/folders/{id}`
**Обновить папку**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Request:**
```json
{
  "name": "Updated Folder Name",
  "description": "New description"
}
```

**Response (200):** Updated Folder object

**Errors:** `400`, `401`, `404`

---

#### DELETE `/folders/{id}`
**Удалить папку**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (204):** No Content

**Errors:** `401`, `404`

---

## 🗂️ Структура проекта

```
smart_dictophone/
├── src/main/kotlin/ru/kingofraccoons/
│   ├── Application.kt              # Главный файл, конфигурация плагинов
│   ├── dao/
│   │   └── DAOs.kt                 # Data Access Objects (UserDAO, FolderDAO, etc.)
│   ├── database/
│   │   └── DatabaseFactory.kt      # Инициализация БД, HikariCP
│   ├── models/
│   │   └── Entities.kt             # Exposed таблицы, DTOs, Request/Response
│   ├── routes/
│   │   ├── AuthRoutes.kt           # /login, /register, /loginOnToken
│   │   ├── RecordRoutes.kt         # /records, /recordInfo, /transcribe
│   │   └── FolderRoutes.kt         # /folders CRUD
│   ├── security/
│   │   └── JwtService.kt           # JWT генерация/верификация, BCrypt
│   └── services/
│       ├── S3Service.kt            # AWS S3/MinIO клиент
│       └── PdfService.kt           # Apache PDFBox для PDF
├── src/main/resources/
│   ├── application.yaml            # Конфигурация (БД, JWT, S3, API)
│   └── logback.xml                 # Логирование
├── build.gradle.kts                # Зависимости Gradle
└── README.md                       # Эта документация
```

## 🗄️ ER-диаграмма БД

```
Users
  ├── id (PK, Long)
  ├── email (unique)
  ├── password_hash
  ├── full_name
  ├── created_at
  └── updated_at

Folders
  ├── id (PK, Long)
  ├── user_id (FK → Users)
  ├── name
  ├── description
  ├── created_at
  └── updated_at

Records
  ├── id (PK, Long)
  ├── folder_id (FK → Folders, nullable)
  ├── title
  ├── description
  ├── datetime
  ├── latitude (nullable)
  ├── longitude (nullable)
  ├── duration (seconds)
  ├── category (ENUM: Work/Study/Personal)
  ├── audio_url
  ├── created_at
  └── updated_at

TranscriptionSegments
  ├── id (PK, Long)
  ├── record_id (FK → Records)
  ├── start (Float)
  ├── end (Float)
  └── text
```

## 🧪 Тестирование

```bash
# Запустить тесты
./gradlew test

# Пример cURL-запроса
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test123","fullname":"Test User"}'
```

## 🔒 Безопасность

- Пароли хешируются с **BCrypt** (salt rounds = 10)
- JWT токены с HMAC-SHA256
- Access Token: 1 час
- Refresh Token: 30 дней
- API Key валидация для транскрипции (X-API-Key header)
- CORS настроен (по умолчанию `anyHost()`, измените для продакшена)

## 📦 Production Deployment

1. **Измените** `jwt.secret` на криптостойкий (256+ бит)
2. **Настройте** PostgreSQL с SSL
3. **Используйте** AWS S3 вместо MinIO
4. **Ограничьте** CORS: `allowHost("yourdomain.com")`
5. **Настройте** HTTPS (reverse proxy: Nginx/Traefik)
6. **Логи**: настройте Logback для production (ротация, уровни)

```bash
# Пример Docker Compose для продакшена
docker-compose up -d
```

## 🤝 Интеграция с iOS

Для iOS-приложения используйте:
- **Alamofire** для HTTP-запросов
- **JWT Decoder** для токенов
- **Multipart Upload** для аудиофайлов
- **Whisper ML** локально, затем POST `/records/{id}/transcribe`

## 📄 Лицензия

MIT License

## 👨‍💻 Автор

Backend разработан для проекта "Smart Dictophone" — iOS-приложение с AI-транскрипцией встреч.

---

**Статус**: ✅ Готово к разработке и тестированию

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

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
| [Authentication Basic](https://start.ktor.io/p/auth-basic)             | Handles 'Basic' username / password authentication scheme                          |
| [Authentication JWT](https://start.ktor.io/p/auth-jwt)                 | Handles JSON Web Token (JWT) bearer authentication scheme                          |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
| -----------------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```


# Smart Dictophone API

Бэкенд API на **Kotlin + Ktor 3.x** для мобильного приложения «Умный диктофон»: хранение аудио в **S3/MinIO**, постановка задач на транскрипцию через **RabbitMQ**, интеграция с **Keycloak**, экспорт PDF и Swagger/OpenAPI.

## Возможности

- **Keycloak**: регистрация/логин/refresh, валидация JWT по публичному ключу realm
- **Папки**: дефолтные папки пользователя (`Работа/Учёба/Личное`) + CRUD
- **Записи**: загрузка M4A, список с поиском/пагинацией, скачивание аудио и PDF
- **Транскрипция**: API отправляет `recordId` в RabbitMQ, ML-сервис возвращает сегменты через `POST /records/{id}/transcribe` (защищено `X-API-Key`)
- **Документация**: Swagger UI (`/swagger-ui`) и OpenAPI (`/openapi.json?raw=true`)

## Архитектура

```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│   Mobile    │────│     API      │────│  Keycloak   │
│     App     │    │ (Ktor + JWT) │    │    Auth     │
└─────────────┘    └──────────────┘    └─────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
    ┌─────────▼───┐   ┌────▼────┐   ┌───▼───┐
    │ PostgreSQL  │   │   S3    │   │  ML   │
    │  Database   │   │ Storage │   │Service│
    └─────────────┘   └─────────┘   └───────┘
```

## Технологический стек

- **Kotlin**, **Ktor (Netty)**, **Exposed**, **HikariCP**
- **PostgreSQL**
- **Keycloak** (JWT, public key realm)
- **MinIO / S3**
- **RabbitMQ**
- **PDFBox**
- **OpenAPI 3.1 + Swagger UI**

## Быстрый запуск

### 1) Запуск стенда через Docker Compose (рекомендуется)

```bash
docker compose up -d --build
```

Или используйте скрипт:

```bash
./start.sh
```

> `start.sh` делает `docker compose down -v` и полностью пересоздаёт volumes (данные БД/MinIO будут удалены).

> `ml-service` собирается из публичного репозитория `mrkuloff/voice-recorder-ml-service` (Git context в `docker-compose.yml`). Для сборки нужен доступ к сети и включённый BuildKit.

### 2) Проверка здоровья

```bash
./scripts/health-check.sh
```

### 3) Полезные адреса (по умолчанию)

- API: `http://localhost:8888`
- Swagger UI: `http://localhost:8888/swagger-ui`
- OpenAPI JSON (сырой): `http://localhost:8888/openapi.json?raw=true`
- Keycloak: `http://localhost:8090` (admin/admin)
- MinIO Console: `http://localhost:9001` (minioadmin/minioadmin)
- RabbitMQ Management: `http://localhost:15672` (rmuser/rmpassword)

## Локальный запуск API (без Docker для приложения)

Поднимите инфраструктуру (PostgreSQL, Keycloak, MinIO, RabbitMQ) через Compose и запустите приложение локально:

```bash
./gradlew run
```

Конфигурация берётся из переменных окружения и `src/main/resources/application.yaml`. Пример переменных — в `.env.example`.

## Конфигурация

Основные переменные окружения (см. `src/main/resources/application.yaml`):

```bash
# Server
PORT=8888
PUBLIC_BASE_URL=https://api.smartdictophone.com

# Database
DATABASE_URL=jdbc:postgresql://postgres:5432/smart_dictophone
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
DATABASE_MAX_POOL_SIZE=10

# Keycloak
KEYCLOAK_SERVER_URL=http://keycloak:8080
KEYCLOAK_PUBLIC_URL=http://localhost:8090
KEYCLOAK_REALM=smart-dictophone
KEYCLOAK_CLIENT_ID=smart-dictophone-backend
KEYCLOAK_CLIENT_SECRET=your-backend-client-secret
KEYCLOAK_FRONTEND_CLIENT_ID=smart-dictophone-frontend
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=admin

# ML callback protection
API_KEY=change-me

# S3/MinIO
S3_ENDPOINT=http://minio:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=smart-dictophone-audio

# RabbitMQ
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=rmuser
RABBITMQ_PASSWORD=rmpassword
RABBITMQ_QUEUE=audio-transcription

# PDF fonts (optional)
PDF_FONT_REGULAR=fonts/NotoSans-Regular.ttf
PDF_FONT_BOLD=fonts/NotoSans-Bold.ttf
```

### PDF шрифты

- Для корректного рендеринга кириллицы добавьте TTF/OTF в `src/main/resources/fonts/` (например, `NotoSans-Regular.ttf` и `NotoSans-Bold.ttf`).
- Либо укажите пути через `PDF_FONT_REGULAR` и `PDF_FONT_BOLD` (абсолютный или относительный путь).
- Если шрифты не найдены, используется стандартный шрифт PDFBox (латиница).

## Keycloak

- В `docker-compose.yml` realm импортируется автоматически из `keycloak/smart-dictophone-realm.json`.
- Admin UI: `http://localhost:8090` (admin/admin)
- Получить client secret можно скриптом `./scripts/get-client-secret.sh` (нужны `curl` и `jq`).

## API Документация

- **Swagger UI**: `http://localhost:8888/swagger-ui`
- **OpenAPI JSON**: `http://localhost:8888/openapi.json?raw=true`

## Основные эндпоинты

### Аутентификация
- `POST /register` — регистрация пользователя в Keycloak
- `POST /login` — вход по email/паролю (через Keycloak)
- `POST /refresh` — обновление access token (через refresh token)
- `POST /loginOnToken` — проверка токена и получение базовой информации

### Пользователь
- `GET /recordInfo` — профиль и статистика (создаёт дефолтные папки при первом вызове)

### Папки
- `GET /folders` — список папок
- `POST /folders` — создать папку
- `PUT /folders/{id}` — обновить папку
- `DELETE /folders/{id}` — удалить папку (в текущей реализации удаляет записи папки и их аудио в S3)

### Записи
- `GET /records` — список записей (`search`, `folderId`, `page`, `size`)
- `POST /records` — загрузка новой записи (multipart: `recordFile`, `name`, `datetime`, `category`, `folderId?`, `place?`)
- `GET /records/{id}/audio` — скачать аудио
- `GET /records/{id}/pdf` — скачать PDF с транскрипцией

### ML сервис
- `POST /records/{id}/transcribe` — сохранить сегменты транскрипции (требуется `X-API-Key`)

### Система
- `GET /health` — healthcheck
- `GET /` — краткая информация об API

## QA Postman Collection

- **Файл**: `files/postman/smart-dictophone-qa.postman_collection.json`
- **Запуск**: `npx newman run files/postman/smart-dictophone-qa.postman_collection.json -e <env.json>`

## Сборка и тесты

```bash
./gradlew test
./gradlew build
./gradlew buildFatJar
java -jar build/libs/smart_dictophone-all.jar
```

## Безопасность

- JWT токены выдаёт Keycloak, время жизни определяется настройками realm в Keycloak.
- Эндпоинт `POST /records/{id}/transcribe` защищён ключом `X-API-Key` (значение — `API_KEY`).
- CORS сейчас настроен максимально либерально (`anyHost()`); для продакшена стоит ограничить домены.

## Развёртывание

```bash
docker build -t smartdictophone-api .
docker run -d --name smartdictophone-api --env-file .env -p 8888:8888 smartdictophone-api
```

Пример конфигурации обратного прокси (Nginx):

```nginx
server {
    listen 443 ssl;
    server_name api.yourcompany.com;

    ssl_certificate /path/to/certificate.crt;
    ssl_certificate_key /path/to/private.key;

    location / {
        proxy_pass http://localhost:8888;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Поддержка

- Issues: https://github.com/KingOfRaccoon/SmartDictophone/issues

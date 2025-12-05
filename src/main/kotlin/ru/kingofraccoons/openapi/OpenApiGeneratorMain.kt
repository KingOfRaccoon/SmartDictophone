package ru.kingofraccoons.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Standalone генератор OpenAPI спецификации
 * Запускается как отдельная программа для генерации документации при компиляции
 */
fun main() {
    println("Starting OpenAPI specification generation...")
    
    // Автоматически сканируем route-файлы через рефлексию и парсинг исходников
    println("Scanning route files...")
    RouteScanner.scanSourceFiles()
    
    val totalEndpoints = ApiRegistry.getAllEndpoints().size
    println("Found $totalEndpoints endpoints")
    
    // Если эндпоинты не найдены, используем fallback регистрацию
    if (totalEndpoints == 0) {
        println("No endpoints found via scanning, using manual registration...")
        registerAllEndpoints()
    }
    
    // Генерируем спецификацию
    val spec = OpenApiGenerator.generateSpec(
        title = "Smart Dictophone API",
        version = "1.0.0",
        description = """
            REST API для умного диктофона с автоматической транскрипцией и организацией записей.
            
            ## Основные возможности
            - 🔐 Аутентификация через Keycloak (JWT)
            - 📁 Организация записей по папкам
            - 🎙️ Загрузка и хранение аудиофайлов
            - 📝 Автоматическая транскрипция (через RabbitMQ)
            - 📄 Экспорт транскрипций в PDF
            - 🔍 Поиск по записям
            - 📊 Статистика использования
            
            ## Аутентификация
            API использует Keycloak для аутентификации. Получите токен через `/auth/login` 
            и используйте его в заголовке `Authorization: Bearer {token}`.
        """.trimIndent(),
        servers = listOf(
            OpenApiGenerator.Server("http://localhost:8888", "Локальная разработка"),
            OpenApiGenerator.Server("https://api.smartdictophone.com", "Production")
        ),
        securitySchemes = mapOf(
            "BearerAuth" to OpenApiGenerator.SecurityScheme(
                type = "http",
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "JWT токен от Keycloak. Получите через POST /auth/login"
            ),
            "ApiKeyAuth" to OpenApiGenerator.SecurityScheme(
                type = "apiKey",
                `in` = "header",
                name = "X-API-Key",
                description = "API ключ для сервисных запросов (внутреннее использование)"
            )
        )
    )
    
    // Сохраняем в JSON
    val json = Json {
        prettyPrint = true
        encodeDefaults = true  // Включаем defaults чтобы openapi: 3.1.0 был включен
    }
    val jsonContent = json.encodeToString(spec)
    
    // Очищаем null значения из JSON перед конвертацией в YAML
    val cleanedJsonContent = removeNullValues(jsonContent)
    
    // Сохраняем в YAML
    val yamlContent = convertJsonStringToYaml(cleanedJsonContent)
    
    // Определяем пути
    val resourcesDir = File("src/main/resources/openapi")
    resourcesDir.mkdirs()
    
    val jsonFile = File(resourcesDir, "documentation.json")
    val yamlFile = File(resourcesDir, "documentation.yaml")
    
    jsonFile.writeText(cleanedJsonContent)
    yamlFile.writeText(yamlContent)
    
    println("✅ OpenAPI specification generated:")
    println("   - JSON: ${jsonFile.absolutePath}")
    println("   - YAML: ${yamlFile.absolutePath}")
    println("   - Total endpoints: ${ApiRegistry.getAllEndpoints().size}")
}

/**
 * Регистрация всех endpoints приложения
 * Вызывается перед генерацией спецификации
 */
private fun registerAllEndpoints() {
    // Очищаем реестр
    ApiRegistry.clear()
    
    // Регистрируем endpoints вручную (симуляция route определений)
    registerAuthEndpoints()
    registerUserEndpoints()
    registerRecordEndpoints()
    registerFolderEndpoints()
}

private fun registerAuthEndpoints() {
    // POST /auth/register
    apiDoc("POST", "/auth/register") {
        summary = "Регистрация нового пользователя"
        description = "Создаёт нового пользователя в Keycloak и автоматически выполняет вход"
        tags = listOf("Authentication")
        
        requestBody(
            description = "Данные для регистрации",
            example = """
                {
                  "username": "john_doe",
                  "email": "john@example.com",
                  "password": "SecurePass123",
                  "firstName": "John",
                  "lastName": "Doe"
                }
            """.trimIndent()
        )
        
        response(io.ktor.http.HttpStatusCode.Created, "Пользователь успешно создан и авторизован")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Неверный формат данных или отсутствуют обязательные поля")
        response(io.ktor.http.HttpStatusCode.Conflict, "Пользователь с таким email или username уже существует")
    }
    
    // POST /auth/login
    apiDoc("POST", "/auth/login") {
        summary = "Вход в систему"
        description = "Аутентифицирует пользователя через Keycloak и возвращает JWT токен"
        tags = listOf("Authentication")
        
        requestBody(
            description = "Учетные данные для входа",
            example = """
                {
                  "email": "john@example.com",
                  "password": "SecurePass123"
                }
            """.trimIndent()
        )
        
        response(io.ktor.http.HttpStatusCode.OK, "Успешная аутентификация, токен возвращен")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Отсутствуют обязательные поля")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Неверные учетные данные")
    }
    
    // POST /auth/refresh
    apiDoc("POST", "/auth/refresh") {
        summary = "Обновление токена"
        description = "Обновляет JWT токен с использованием refresh token"
        tags = listOf("Authentication")
        
        requestBody(
            description = "Refresh token",
            example = """
                {
                  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                }
            """.trimIndent()
        )
        
        response(io.ktor.http.HttpStatusCode.OK, "Токен успешно обновлен")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Refresh token не предоставлен")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный refresh token")
    }
}

private fun registerUserEndpoints() {
    // GET /recordInfo
    apiDoc("GET", "/recordInfo") {
        summary = "Получить профиль и статистику пользователя"
        description = """
            Возвращает информацию о пользователе из JWT токена Keycloak и статистику записей.
            При первом запросе автоматически создаёт дефолтные папки.
        """.trimIndent()
        tags = listOf("Users")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        
        response(io.ktor.http.HttpStatusCode.OK, "Информация о пользователе и статистика", example = """
            {
              "keycloakUserId": "uuid",
              "username": "john_doe",
              "email": "john@example.com",
              "fullName": "John Doe",
              "countRecords": 42,
              "countMinutes": 180
            }
        """.trimIndent())
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
    }
}

private fun registerRecordEndpoints() {
    // GET /records
    apiDoc("GET", "/records") {
        summary = "Получить список записей"
        description = "Возвращает список аудиозаписей пользователя с поддержкой поиска, фильтрации и пагинации"
        tags = listOf("Records")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        parameter("search", "Поисковый запрос по названию или описанию", required = false, location = ParameterLocation.QUERY)
        parameter("folderId", "ID папки для фильтрации", required = false, type = "integer", location = ParameterLocation.QUERY)
        parameter("page", "Номер страницы (начиная с 0)", required = false, type = "integer", location = ParameterLocation.QUERY)
        parameter("size", "Количество элементов на странице", required = false, type = "integer", location = ParameterLocation.QUERY)
        
        response(io.ktor.http.HttpStatusCode.OK, "Список записей с пагинацией")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
    }
    
    // POST /records
    apiDoc("POST", "/records") {
        summary = "Создать новую запись"
        description = """
            Загружает аудиофайл и создаёт новую запись.
            Файл сохраняется в S3 и автоматически отправляется на транскрипцию.
        """.trimIndent()
        tags = listOf("Records")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        
        requestBody(
            description = """
                Multipart form data с полями:
                - recordFile (file): аудиофайл в формате m4a
                - name (string): название записи
                - datetime (string): дата и время в формате ISO-8601
                - category (string): MEETING, LECTURE, INTERVIEW, NOTE, OTHER
                - folderId (integer): ID папки
                - place (string, optional): место записи
            """.trimIndent(),
            contentType = "multipart/form-data"
        )
        
        response(io.ktor.http.HttpStatusCode.Created, "Запись успешно создана")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Отсутствуют обязательные поля")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
        response(io.ktor.http.HttpStatusCode.NotFound, "Папка не найдена")
    }
    
    // GET /records/{id}
    apiDoc("GET", "/records/{id}") {
        summary = "Получить запись по ID"
        description = "Возвращает детальную информацию о записи"
        tags = listOf("Records")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
        
        response(io.ktor.http.HttpStatusCode.OK, "Информация о записи")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
        response(io.ktor.http.HttpStatusCode.NotFound, "Запись не найдена")
        response(io.ktor.http.HttpStatusCode.Forbidden, "Нет доступа к записи")
    }
    
    // DELETE /records/{id}
    apiDoc("DELETE", "/records/{id}") {
        summary = "Удалить запись"
        description = "Удаляет запись и связанный аудиофайл из S3"
        tags = listOf("Records")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
        
        response(io.ktor.http.HttpStatusCode.NoContent, "Запись успешно удалена")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
        response(io.ktor.http.HttpStatusCode.NotFound, "Запись не найдена")
        response(io.ktor.http.HttpStatusCode.Forbidden, "Нет доступа к записи")
    }
    
    // GET /records/{id}/audio
    apiDoc("GET", "/records/{id}/audio") {
        summary = "Скачать аудиофайл"
        description = "Возвращает аудиофайл записи из S3"
        tags = listOf("Records")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
        
        response(io.ktor.http.HttpStatusCode.OK, "Аудиофайл", contentType = "audio/mp4")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
        response(io.ktor.http.HttpStatusCode.NotFound, "Запись или аудиофайл не найдены")
        response(io.ktor.http.HttpStatusCode.Forbidden, "Нет доступа к записи")
    }

    // POST /records/{id}/transcribe
    apiDoc("POST", "/records/{id}/transcribe") {
        summary = "Сохранить транскрипцию записи"
        description = """
            Принимает сегменты транскрипции от ML-сервиса, сохраняет их и заполняет поле description полным текстом транскрипции (сегменты сортируются по start и склеиваются пробелом).
            Требуется API ключ в заголовке X-API-Key.
        """.trimIndent()
        tags = listOf("Records", "ML Service")

        parameter("X-API-Key", "API ключ", required = true, location = ParameterLocation.HEADER)
        parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)

        requestBody(
            description = """
                JSON с сегментами транскрипции:
                {
                  "segments": [
                    { "start": 0.0, "end": 1.2, "text": "Первый сегмент" },
                    { "start": 1.2, "end": 2.5, "text": "Второй сегмент" }
                  ]
                }
            """.trimIndent()
        )

        response(io.ktor.http.HttpStatusCode.OK, "Транскрипция сохранена, description обновлён")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Неверный ID записи или пустые сегменты")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Неверный API ключ")
        response(io.ktor.http.HttpStatusCode.NotFound, "Запись не найдена")
    }
    
    // POST /transcribe
    apiDoc("POST", "/transcribe") {
        summary = "Транскрибировать аудио"
        description = "Запускает процесс транскрипции для существующей записи (внутренний endpoint)"
        tags = listOf("Records")
        
        parameter("X-API-Key", "API ключ", required = true, location = ParameterLocation.HEADER)
        
        requestBody(
            description = "Данные для транскрипции",
            example = """
                {
                  "recordId": 123,
                  "language": "ru"
                }
            """.trimIndent()
        )
        
        response(io.ktor.http.HttpStatusCode.OK, "Транскрипция успешно сохранена")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Неверные параметры")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный API ключ")
        response(io.ktor.http.HttpStatusCode.NotFound, "Запись не найдена")
    }
}

private fun registerFolderEndpoints() {
    // GET /folders
    apiDoc("GET", "/folders") {
        summary = "Получить список папок"
        description = "Возвращает все папки пользователя"
        tags = listOf("Folders")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        
        response(io.ktor.http.HttpStatusCode.OK, "Список папок")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
    }
    
    // POST /folders
    apiDoc("POST", "/folders") {
        summary = "Создать новую папку"
        description = "Создаёт новую папку для организации записей"
        tags = listOf("Folders")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        
        requestBody(
            description = "Данные новой папки",
            example = """
                {
                  "name": "Рабочие встречи",
                  "color": "#FF5733"
                }
            """.trimIndent()
        )
        
        response(io.ktor.http.HttpStatusCode.Created, "Папка успешно создана")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Отсутствует название папки")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
    }
    
    // PUT /folders/{id}
    apiDoc("PUT", "/folders/{id}") {
        summary = "Обновить папку"
        description = "Обновляет название или цвет папки"
        tags = listOf("Folders")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        parameter("id", "ID папки", required = true, type = "integer", location = ParameterLocation.PATH)
        
        requestBody(
            description = "Обновленные данные папки",
            example = """
                {
                  "name": "Новое название",
                  "color": "#00FF00"
                }
            """.trimIndent()
        )
        
        response(io.ktor.http.HttpStatusCode.OK, "Папка успешно обновлена")
        response(io.ktor.http.HttpStatusCode.BadRequest, "Неверные данные")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
        response(io.ktor.http.HttpStatusCode.NotFound, "Папка не найдена")
        response(io.ktor.http.HttpStatusCode.Forbidden, "Нельзя изменить системную папку")
    }
    
    // DELETE /folders/{id}
    apiDoc("DELETE", "/folders/{id}") {
        summary = "Удалить папку"
        description = "Удаляет папку (системные папки удалить нельзя)"
        tags = listOf("Folders")
        
        parameter("Authorization", "Bearer токен", required = true, location = ParameterLocation.HEADER)
        parameter("id", "ID папки", required = true, type = "integer", location = ParameterLocation.PATH)
        
        response(io.ktor.http.HttpStatusCode.NoContent, "Папка успешно удалена")
        response(io.ktor.http.HttpStatusCode.Unauthorized, "Недействительный токен")
        response(io.ktor.http.HttpStatusCode.NotFound, "Папка не найдена")
        response(io.ktor.http.HttpStatusCode.Forbidden, "Нельзя удалить системную папку")
    }
}

/**
 * Удаляет все null значения из JSON строки
 */
private fun removeNullValues(jsonContent: String): String {
    val objectMapper = ObjectMapper()
    val jsonNode = objectMapper.readTree(jsonContent)
    
    fun cleanNode(node: com.fasterxml.jackson.databind.JsonNode): com.fasterxml.jackson.databind.JsonNode {
        return when {
            node.isObject -> {
                val obj = objectMapper.createObjectNode()
                node.fields().forEach { (key, value) ->
                    if (!value.isNull) {
                        obj.set<com.fasterxml.jackson.databind.JsonNode>(key, cleanNode(value))
                    }
                }
                obj
            }
            node.isArray -> {
                val arr = objectMapper.createArrayNode()
                node.forEach { item ->
                    if (!item.isNull) {
                        arr.add(cleanNode(item))
                    }
                }
                arr
            }
            else -> node
        }
    }
    
    val cleanedNode = cleanNode(jsonNode)
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cleanedNode)
}

/**
 * Конвертирует JSON строку в YAML используя Jackson и SnakeYAML
 */
private fun convertJsonStringToYaml(jsonContent: String): String {
    // Используем Jackson для парсинга JSON
    val objectMapper = ObjectMapper()
    val map = objectMapper.readValue(jsonContent, Any::class.java)
    
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        width = 120
    }
    
    val yaml = Yaml(options)
    return yaml.dump(map)
}

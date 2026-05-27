package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.http.content.forEachPart
import io.ktor.http.content.PartData
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.dao.UserProfileDAO
import ru.kingofraccoons.models.*
import ru.kingofraccoons.openapi.ParameterLocation
import ru.kingofraccoons.openapi.apiDoc
import ru.kingofraccoons.services.KeycloakService
import ru.kingofraccoons.services.S3Service
import java.io.ByteArrayInputStream

fun Route.userRoutes(
    recordDAO: RecordDAO,
    folderDAO: FolderDAO,
    userProfileDAO: UserProfileDAO,
    keycloakService: KeycloakService,
    s3Service: S3Service
) {
    authenticate("auth-jwt") {
        apiDoc("GET", "/recordInfo") {
            summary = "Получить профиль и статистику пользователя"
            description = "Возвращает информацию о пользователе из JWT токена Keycloak (ID, username, email, имя), статистику записей и данные профиля (telegram, avatarUrl, emailForTranscripts). При первом запросе автоматически создаёт дефолтные папки."
            tags = listOf("Users")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(
                HttpStatusCode.OK,
                "Информация о пользователе и статистика",
                "application/json",
                example = """
                    {
                        "keycloakUserId": "uuid",
                        "username": "john_doe",
                        "email": "john@example.com",
                        "fullName": "John Doe",
                        "countRecords": 42,
                        "countMinutes": 180,
                        "telegram": "@john_doe",
                        "avatarUrl": "https://s3.example.com/avatars/123.jpg",
                        "emailForTranscripts": "transcripts@example.com"
                    }
                """.trimIndent()
            )
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * GET /recordInfo - получить статистику пользователя с данными профиля
         */
        get("/recordInfo") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))

            val keycloakUserId = principal.payload.subject
            val email = principal.payload.getClaim("email")?.asString()
            val fullName = principal.payload.getClaim("name")?.asString()
                ?: principal.payload.getClaim("preferred_username")?.asString()

            val userResult = keycloakService.getUserById(keycloakUserId)
            val originalUsername = if (userResult.isSuccess) {
                keycloakService.getOriginalUsername(userResult.getOrThrow())
            } else {
                principal.payload.getClaim("preferred_username")?.asString() ?: "unknown"
            }

            if (!folderDAO.hasDefaultFolders(keycloakUserId)) {
                folderDAO.createDefaultFolders(keycloakUserId)
            }

            val countRecords = recordDAO.countByKeycloakUserId(keycloakUserId).toInt()
            val totalSeconds = recordDAO.sumDurationByKeycloakUserId(keycloakUserId)
            val countMinutes = (totalSeconds / 60).toInt()

            val profile = userProfileDAO.findOrCreate(keycloakUserId)

            call.respond(
                HttpStatusCode.OK,
                UserInfo(
                    keycloakUserId = keycloakUserId,
                    username = originalUsername,
                    email = email,
                    fullName = fullName,
                    countRecords = countRecords,
                    countMinutes = countMinutes,
                    telegram = profile.telegram,
                    avatarUrl = profile.avatarUrl,
                    emailForTranscripts = profile.emailForTranscripts ?: email
                )
            )
        }

        apiDoc("PUT", "/users/profile") {
            summary = "Обновить профиль пользователя"
            description = "Обновляет данные профиля: telegram, email для получения протоколов, ФИО"
            tags = listOf("Users")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Профиль обновлён")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * PUT /users/profile - обновить профиль
         */
        put("/users/profile") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val request = call.receiveOrBadRequest<UpdateProfileRequest>() ?: return@put

            val updatedProfile = userProfileDAO.update(
                keycloakUserId = keycloakUserId,
                telegram = request.telegram,
                emailForTranscripts = request.emailForTranscripts
            )

            call.respond(HttpStatusCode.OK, updatedProfile ?: ErrorResponse("Failed to update profile", 500))
        }

        apiDoc("POST", "/users/avatar") {
            summary = "Загрузить аватар"
            description = "Загружает изображение аватара в S3 и обновляет URL в профиле"
            tags = listOf("Users")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Аватар загружен")
            response(HttpStatusCode.BadRequest, "Файл не предоставлен")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * POST /users/avatar - загрузить аватар
         */
        post("/users/avatar") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject

            val multipart = call.receiveMultipart()
            var imageBytes: ByteArray? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "avatar") {
                    val channel = part.provider()
                    @Suppress("DEPRECATION")
                    imageBytes = channel.readRemaining().readBytes()
                }
                part.dispose()
            }

            if (imageBytes == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Avatar file is required", 400))
                return@post
            }

            val avatarUrl = s3Service.uploadFile(
                ByteArrayInputStream(imageBytes),
                "avatars/$keycloakUserId.jpg",
                "image/jpeg"
            )

            val updatedProfile = userProfileDAO.updateAvatarUrl(keycloakUserId, avatarUrl)
            call.respond(HttpStatusCode.OK, mapOf("avatarUrl" to avatarUrl, "profile" to updatedProfile))
        }

        apiDoc("GET", "/users/storage") {
            summary = "Получить информацию о хранилище"
            description = "Возвращает приблизительный размер хранилища пользователя и количество записей"
            tags = listOf("Users")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Информация о хранилище")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * GET /users/storage - размер хранилища
         */
        get("/users/storage") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val countRecords = recordDAO.countByKeycloakUserId(keycloakUserId).toInt()
            val totalSeconds = recordDAO.sumDurationByKeycloakUserId(keycloakUserId)

            // Приблизительный расчёт: ~1MB в минуту для m4a
            val approxBytesPerMinute = 1L * 1024 * 1024
            val totalMinutes = totalSeconds / 60
            val storageUsedBytes = totalMinutes * approxBytesPerMinute

            call.respond(
                HttpStatusCode.OK,
                StorageInfo(
                    storageUsedBytes = storageUsedBytes,
                    recordCount = countRecords
                )
            )
        }

        apiDoc("PUT", "/users/transcript-email") {
            summary = "Изменить email для получения протоколов"
            description = "Обновляет email, на который отправляются протоколы встреч"
            tags = listOf("Users")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Email обновлён")
            response(HttpStatusCode.BadRequest, "Некорректный email")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * PUT /users/transcript-email - изменить email для протоколов
         */
        put("/users/transcript-email") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val request = call.receiveOrBadRequest<UpdateTranscriptEmailRequest>() ?: return@put

            if (request.email.isBlank() || !request.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Valid email is required", 400))
                return@put
            }

            val updatedProfile = userProfileDAO.update(
                keycloakUserId = keycloakUserId,
                emailForTranscripts = request.email
            )

            call.respond(HttpStatusCode.OK, mapOf("emailForTranscripts" to (updatedProfile?.emailForTranscripts ?: request.email)))
        }

        apiDoc("DELETE", "/users/cache") {
            summary = "Очистить кэш"
            description = "Очищает клиентский кэш. Серверная заглушка — фактическая очистка выполняется на клиенте."
            tags = listOf("Users")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Кэш очищен")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * DELETE /users/cache - очистить кэш (заглушка)
         */
        delete("/users/cache") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            call.respond(HttpStatusCode.OK, mapOf("message" to "Cache cleared"))
        }
    }
}

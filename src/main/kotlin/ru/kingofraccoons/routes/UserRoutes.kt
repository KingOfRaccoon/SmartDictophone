package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.models.UserInfo
import ru.kingofraccoons.services.KeycloakService

/**
 * User information routes
 * Получение информации о пользователе из Keycloak JWT токена
 */
fun Route.userRoutes(
    recordDAO: RecordDAO,
    folderDAO: FolderDAO,
    keycloakService: KeycloakService
) {
    authenticate("auth-jwt") {
        /**
         * GET /recordInfo - получить статистику пользователя
         * Возвращает информацию из токена и статистику записей
         */
        get("/recordInfo") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))

            // Извлекаем данные из JWT токена Keycloak
            val keycloakUserId = principal.payload.subject // 'sub' claim содержит Keycloak user ID
            val email = principal.payload.getClaim("email")?.asString()
            val fullName = principal.payload.getClaim("name")?.asString()
                ?: principal.payload.getClaim("preferred_username")?.asString()
            
            // Получаем оригинальный username из Keycloak
            val userResult = keycloakService.getUserById(keycloakUserId)
            val originalUsername = if (userResult.isSuccess) {
                keycloakService.getOriginalUsername(userResult.getOrThrow())
            } else {
                principal.payload.getClaim("preferred_username")?.asString() ?: "unknown"
            }

            // Создаем дефолтные папки при первом входе
            if (!folderDAO.hasDefaultFolders(keycloakUserId)) {
                folderDAO.createDefaultFolders(keycloakUserId)
            }

            // Получаем статистику
            val countRecords = recordDAO.countByKeycloakUserId(keycloakUserId).toInt()
            val totalSeconds = recordDAO.sumDurationByKeycloakUserId(keycloakUserId)
            val countMinutes = (totalSeconds / 60).toInt()

            call.respond(
                HttpStatusCode.OK,
                UserInfo(
                    keycloakUserId = keycloakUserId,
                    username = originalUsername,
                    email = email,
                    fullName = fullName,
                    countRecords = countRecords,
                    countMinutes = countMinutes
                )
            )
        }
    }
}

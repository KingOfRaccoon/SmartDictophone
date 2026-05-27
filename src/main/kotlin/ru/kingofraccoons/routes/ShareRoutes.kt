package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.dao.SharedRecordDAO
import ru.kingofraccoons.models.*
import ru.kingofraccoons.openapi.ParameterLocation
import ru.kingofraccoons.openapi.apiDoc
import ru.kingofraccoons.services.KeycloakService

fun Route.shareRoutes(
    sharedRecordDAO: SharedRecordDAO,
    recordDAO: RecordDAO,
    folderDAO: FolderDAO,
    keycloakService: KeycloakService
) {
    authenticate("auth-jwt") {
        apiDoc("POST", "/records/{id}/share") {
            summary = "Поделиться записью"
            description = "Предоставляет доступ к записи другому пользователю по email"
            tags = listOf("Sharing")
            parameter("Authorization", "Bearer {token}", required = true, location = ParameterLocation.HEADER)
            parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
            response(HttpStatusCode.OK, "Доступ предоставлен")
            response(HttpStatusCode.BadRequest, "Некорректные данные")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
            response(HttpStatusCode.NotFound, "Запись не найдена")
        }

        /**
         * POST /records/{id}/share - поделиться записью
         */
        post("/records/{id}/share") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val recordId = call.parameters["id"]?.toLongOrNull()
            if (recordId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID", 400))
                return@post
            }

            val record = recordDAO.findById(recordId)
            if (record == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Record not found", 404))
                return@post
            }

            if (!record.belongsTo(keycloakUserId, folderDAO)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't have access to this record", 403))
                return@post
            }

            val request = call.receiveOrBadRequest<ShareRecordRequest>() ?: return@post

            val validRoles = listOf("owner", "editor", "viewer")
            if (request.role !in validRoles) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid role. Allowed: ${validRoles.joinToString()}", 400))
                return@post
            }

            // Поиск пользователя по email через Keycloak
            val targetUserId = keycloakService.getUserIdByEmail(request.email)
            if (targetUserId == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User with email ${request.email} not found", 404))
                return@post
            }

            if (targetUserId == keycloakUserId) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot share with yourself", 400))
                return@post
            }

            val sharedRecord = sharedRecordDAO.share(recordId, keycloakUserId, targetUserId, request.role)
            call.respond(HttpStatusCode.OK, sharedRecord)
        }

        apiDoc("GET", "/records/{id}/shared-users") {
            summary = "Получить список пользователей с доступом"
            description = "Возвращает список пользователей, которым предоставлен доступ к записи"
            tags = listOf("Sharing")
            parameter("Authorization", "Bearer {token}", required = true, location = ParameterLocation.HEADER)
            parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
            response(HttpStatusCode.OK, "Список пользователей")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * GET /records/{id}/shared-users - список пользователей с доступом
         */
        get("/records/{id}/shared-users") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val recordId = call.parameters["id"]?.toLongOrNull()
            if (recordId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID", 400))
                return@get
            }

            val record = recordDAO.findById(recordId)
            if (record == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Record not found", 404))
                return@get
            }

            // Владелец или shared-пользователь может смотреть список
            val isOwner = record.belongsTo(keycloakUserId, folderDAO)
            val isShared = sharedRecordDAO.hasAccess(recordId, keycloakUserId)
            if (!isOwner && !isShared) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't have access to this record", 403))
                return@get
            }

            val sharedRecords = sharedRecordDAO.findByRecordId(recordId)
            val sharedUsers = sharedRecords.map { sr ->
                val userInfo = keycloakService.getUserById(sr.sharedWithUserId).getOrNull()
                val email = userInfo?.email
                val fullName = userInfo?.let { u ->
                    listOfNotNull(u.firstName, u.lastName).ifEmpty { null }?.joinToString(" ")
                }
                SharedUser(
                    userId = sr.sharedWithUserId,
                    email = email,
                    fullName = fullName,
                    role = sr.role
                )
            }

            call.respond(HttpStatusCode.OK, sharedUsers)
        }

        apiDoc("DELETE", "/records/{id}/shared-users/{userId}") {
            summary = "Убрать доступ у пользователя"
            description = "Отзывает доступ к записи у указанного пользователя"
            tags = listOf("Sharing")
            parameter("Authorization", "Bearer {token}", required = true, location = ParameterLocation.HEADER)
            parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
            parameter("userId", "ID пользователя для удаления доступа", required = true, type = "string", location = ParameterLocation.PATH)
            response(HttpStatusCode.NoContent, "Доступ убран")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * DELETE /records/{id}/shared-users/{userId} - убрать доступ
         */
        delete("/records/{id}/shared-users/{userId}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val recordId = call.parameters["id"]?.toLongOrNull()
            val targetUserId = call.parameters["userId"]

            if (recordId == null || targetUserId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid parameters", 400))
                return@delete
            }

            val record = recordDAO.findById(recordId)
            if (record == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Record not found", 404))
                return@delete
            }

            if (!record.belongsTo(keycloakUserId, folderDAO)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only the owner can revoke access", 403))
                return@delete
            }

            val deleted = sharedRecordDAO.delete(recordId, targetUserId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Shared access not found", 404))
            }
        }

        apiDoc("GET", "/records/shared") {
            summary = "Получить записи, которыми поделились со мной"
            description = "Возвращает список записей, к которым текущему пользователю предоставлен доступ"
            tags = listOf("Sharing")
            parameter("Authorization", "Bearer {token}", required = true, location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Список shared-записей")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * GET /records/shared - записи, которыми поделились со мной
         */
        get("/records/shared") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

            val keycloakUserId = principal.payload.subject
            val sharedRecords = sharedRecordDAO.findBySharedWithUserId(keycloakUserId)
            val records = sharedRecords.mapNotNull { sr ->
                recordDAO.findById(sr.recordId)?.let { record ->
                    mapOf(
                        "record" to record,
                        "role" to sr.role,
                        "sharedByUserId" to sr.sharedByUserId,
                        "sharedAt" to sr.createdAt
                    )
                }
            }

            call.respond(HttpStatusCode.OK, records)
        }
    }
}


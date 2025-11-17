package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.dao.TranscriptionDAO
import ru.kingofraccoons.models.*
import ru.kingofraccoons.openapi.ParameterLocation
import ru.kingofraccoons.openapi.apiDoc
import ru.kingofraccoons.services.S3Service

/**
 * Folder management routes
 * Управление папками пользователя с автоматическим созданием дефолтных папок
 */
fun Route.folderRoutes(
    folderDAO: FolderDAO,
    recordDAO: RecordDAO,
    transcriptionDAO: TranscriptionDAO,
    s3Service: S3Service
) {
    authenticate("auth-jwt") {
        apiDoc("GET", "/folders") {
            summary = "Получить все папки пользователя"
            description = "Возвращает список всех папок пользователя. При первом запросе автоматически создаёт дефолтные папки (Избранное, Архив, Корзина)."
            tags = listOf("Folders")

            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)

                        response(
                                HttpStatusCode.OK,
                                "Список папок",
                                "application/json",
                                example = """
                                        [
                                            {
                                                "id": 1,
                                                "keycloakUserId": "uuid",
                                                "name": "Избранное",
                                                "description": "Избранные записи",
                                                "createdAt": "2024-01-01T00:00:00Z",
                                                "updatedAt": "2024-01-01T00:00:00Z"
                                            }
                                        ]
                                """.trimIndent()
                        )

            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }
        
        /**
         * GET /folders - получить все папки пользователя
         * Автоматически создаёт дефолтные папки при первом запросе
         */
        get("/folders") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))
            
            val keycloakUserId = principal.payload.subject
            
            // Создаём дефолтные папки если их нет
            if (!folderDAO.hasDefaultFolders(keycloakUserId)) {
                folderDAO.createDefaultFolders(keycloakUserId)
            }
            
            val folders = folderDAO.findByKeycloakUserId(keycloakUserId)
            call.respond(HttpStatusCode.OK, folders)
        }
        
        apiDoc("POST", "/folders") {
            summary = "Создать новую папку"
            description = "Создаёт новую пользовательскую папку для организации записей."
            tags = listOf("Folders")

            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)

                        requestBody(
                                description = "Данные для создания папки",
                                required = true,
                                contentType = "application/json",
                                example = """
                                        {
                                            "name": "Рабочие записи",
                                            "description": "Записи с рабочих встреч"
                                        }
                                """.trimIndent()
                        )

                        response(
                                HttpStatusCode.Created,
                                "Папка успешно создана",
                                "application/json",
                                example = """
                                        {
                                            "id": 5,
                                            "keycloakUserId": "uuid",
                                            "name": "Рабочие записи",
                                            "description": "Записи с рабочих встреч",
                                            "createdAt": "2024-01-01T00:00:00Z",
                                            "updatedAt": "2024-01-01T00:00:00Z"
                                        }
                                """.trimIndent()
                        )

            response(HttpStatusCode.BadRequest, "Некорректные данные (пустое имя или ошибка создания)")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
        }

        /**
         * POST /folders - создать новую папку
         */
        post("/folders") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))
            
            val keycloakUserId = principal.payload.subject

            val request = call.receiveOrBadRequest<CreateFolderRequest>() ?: return@post

            if (request.name.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Folder name is required", 400)
                )
                return@post
            }

            val folder = folderDAO.create(
                keycloakUserId = keycloakUserId,
                name = request.name,
                description = request.description
            )

            if (folder == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to create folder", 400)
                )
                return@post
            }

            call.respond(HttpStatusCode.Created, folder)
        }
        
        apiDoc("PUT", "/folders/{id}") {
            summary = "Обновить папку"
            description = "Обновляет название и/или описание существующей папки пользователя."
            tags = listOf("Folders")

            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            parameter("id", "ID папки для обновления", required = true, type = "integer", location = ParameterLocation.PATH)

                        requestBody(
                                description = "Обновлённые данные папки",
                                required = true,
                                contentType = "application/json",
                                example = """
                                        {
                                            "name": "Важные записи",
                                            "description": "Самые важные записи"
                                        }
                                """.trimIndent()
                        )

                        response(
                                HttpStatusCode.OK,
                                "Папка успешно обновлена",
                                "application/json",
                                example = """
                                        {
                                            "id": 5,
                                            "keycloakUserId": "uuid",
                                            "name": "Важные записи",
                                            "description": "Самые важные записи",
                                            "createdAt": "2024-01-01T00:00:00Z",
                                            "updatedAt": "2024-01-01T00:10:00Z"
                                        }
                                """.trimIndent()
                        )

            response(HttpStatusCode.BadRequest, "Некорректный ID папки или пустое имя")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
            response(HttpStatusCode.Forbidden, "Нет прав на обновление этой папки")
            response(HttpStatusCode.NotFound, "Папка не найдена")
        }

        /**
         * PUT /folders/{id} - обновить папку
         */
        put("/folders/{id}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))
            
            val keycloakUserId = principal.payload.subject
            
            val folderId = call.parameters["id"]?.toLongOrNull()
            if (folderId == null) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid folder ID", 400)
                )
            }
            
            // Проверяем что папка существует и принадлежит пользователю
            val existingFolder = folderDAO.findById(folderId)
            if (existingFolder == null) {
                return@put call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Folder not found", 404)
                )
            }
            
            if (existingFolder.keycloakUserId != keycloakUserId) {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("You don't have permission to update this folder", 403)
                )
            }
            
            val request = call.receiveOrBadRequest<UpdateFolderRequest>() ?: return@put

            if (request.name.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Folder name is required", 400)
                )
                return@put
            }

            val updatedFolder = folderDAO.update(
                id = folderId,
                name = request.name,
                description = request.description
            )

            if (updatedFolder == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to update folder", 400)
                )
                return@put
            }

            call.respond(HttpStatusCode.OK, updatedFolder)
        }
        
        apiDoc("DELETE", "/folders/{id}") {
            summary = "Удалить папку"
            description = "Удаляет папку пользователя. Записи из папки не удаляются, только связь с папкой."
            tags = listOf("Folders")

            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            parameter("id", "ID папки для удаления", required = true, type = "integer", location = ParameterLocation.PATH)

            response(HttpStatusCode.NoContent, "Папка успешно удалена")
            response(HttpStatusCode.BadRequest, "Некорректный ID папки")
            response(HttpStatusCode.Unauthorized, "Недействительный токен")
            response(HttpStatusCode.Forbidden, "Нет прав на удаление этой папки")
            response(HttpStatusCode.NotFound, "Папка не найдена")
            response(HttpStatusCode.InternalServerError, "Ошибка при удалении папки")
        }

        /**
         * DELETE /folders/{id} - удалить папку
         */
        delete("/folders/{id}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))
            
            val keycloakUserId = principal.payload.subject
            
            val folderId = call.parameters["id"]?.toLongOrNull()
            if (folderId == null) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid folder ID", 400)
                )
            }
            
            // Проверяем что папка существует и принадлежит пользователю
            val existingFolder = folderDAO.findById(folderId)
            if (existingFolder == null) {
                return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Folder not found", 404)
                )
            }
            
            if (existingFolder.keycloakUserId != keycloakUserId) {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("You don't have permission to delete this folder", 403)
                )
            }
            
            val recordsToRemove = recordDAO.findByFolderId(folderId)
            recordsToRemove.forEach { record ->
                try {
                    transcriptionDAO.deleteByRecordId(record.id)
                    val deletedRecord = recordDAO.delete(record.id)
                    if (!deletedRecord) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to delete record ${record.id}", 500)
                        )
                        return@delete
                    }
                    if (record.audioUrl.isNotBlank()) {
                        s3Service.deleteFileByUrl(record.audioUrl)
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to cleanup folder records: ${e.message}", 500)
                    )
                    return@delete
                }
            }

            val deleted = folderDAO.delete(folderId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to delete folder", 500)
                )
            }
        }
    }
}

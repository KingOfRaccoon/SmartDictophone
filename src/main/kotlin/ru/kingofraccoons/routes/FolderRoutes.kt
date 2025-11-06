package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.models.*

/**
 * Folder management routes
 * Управление папками пользователя с автоматическим созданием дефолтных папок
 */
fun Route.folderRoutes(folderDAO: FolderDAO) {
    authenticate("auth-jwt") {
        
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

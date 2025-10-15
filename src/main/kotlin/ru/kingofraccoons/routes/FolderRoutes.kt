package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.models.*

fun Route.folderRoutes(folderDAO: FolderDAO) {
    authenticate("auth-jwt") {
        
        get("/folders") {
            val userId = call.requireUserId() ?: return@get
            
            val folders = folderDAO.findByUserId(userId)
            call.respond(HttpStatusCode.OK, folders)
        }
        
        post("/folders") {
            val userId = call.requireUserId() ?: return@post

            val request = call.receiveOrBadRequest<CreateFolderRequest>() ?: return@post

            if (request.name.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Folder name is required", 400)
                )
                return@post
            }

            val folder = folderDAO.create(
                userId = userId,
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
        
        put("/folders/{id}") {
            val userId = call.requireUserId() ?: return@put
            
            val folderId = call.parameters["id"]?.toLongOrNull()
            if (folderId == null) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid folder ID", 400)
                )
            }
            
            // Check if folder exists and belongs to user
            val existingFolder = folderDAO.findById(folderId)
            if (existingFolder == null) {
                return@put call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Folder not found", 404)
                )
            }
            
            if (existingFolder.userId != userId) {
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
        
        delete("/folders/{id}") {
            val userId = call.requireUserId() ?: return@delete
            
            val folderId = call.parameters["id"]?.toLongOrNull()
            if (folderId == null) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid folder ID", 400)
                )
            }
            
            // Check if folder exists and belongs to user
            val existingFolder = folderDAO.findById(folderId)
            if (existingFolder == null) {
                return@delete call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Folder not found", 404)
                )
            }
            
            if (existingFolder.userId != userId) {
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

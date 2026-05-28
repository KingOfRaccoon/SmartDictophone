package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.SharedRecordDAO
import ru.kingofraccoons.models.ErrorResponse
import ru.kingofraccoons.models.Record

/**
 * Safely deserialises the request body. Returns `null` and writes a `BadRequest`
 * response when the payload has an unexpected format.
 *
 * Безопасно десериализует тело запроса. Возвращает `null` и отправляет ответ BadRequest
 * если формат запроса невалидный.
 */
suspend inline fun <reified T : Any> ApplicationCall.receiveOrBadRequest(
    errorMessage: String = "Invalid request format"
): T? = try {
    receive<T>()
} catch (ex: Exception) {
    respond(
        HttpStatusCode.BadRequest,
        ErrorResponse(errorMessage, HttpStatusCode.BadRequest.value)
    )
    null
}

suspend fun Record.belongsTo(userId: String, folderDAO: FolderDAO): Boolean {
    val folderId = folderId ?: return false
    val folder = folderDAO.findById(folderId) ?: return false
    return folder.keycloakUserId == userId
}

suspend fun Record.hasAccess(userId: String, folderDAO: FolderDAO, sharedRecordDAO: SharedRecordDAO): Boolean {
    return belongsTo(userId, folderDAO) || sharedRecordDAO.hasAccess(id, userId)
}

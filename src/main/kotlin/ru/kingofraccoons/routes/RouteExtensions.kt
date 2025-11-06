package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import ru.kingofraccoons.models.ErrorResponse

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

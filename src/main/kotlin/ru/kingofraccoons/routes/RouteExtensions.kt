package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import ru.kingofraccoons.models.ErrorResponse

/**
 * Extracts the `userId` claim from the current JWT principal.
 * Sends an `Unauthorized` response and returns `null` when the claim is missing.
 */
suspend fun ApplicationCall.requireUserId(): Long? {
    val principal = principal<JWTPrincipal>()
    val userId = principal?.payload?.getClaim("userId")?.asLong()

    return if (userId != null) {
        userId
    } else {
        respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse("Invalid token", HttpStatusCode.Unauthorized.value)
        )
        null
    }
}

/**
 * Safely deserialises the request body. Returns `null` and writes a `BadRequest`
 * response when the payload has an unexpected format.
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

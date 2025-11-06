package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.models.*
import ru.kingofraccoons.services.KeycloakService

/**
 * Authentication routes
 * Обновление токенов через Keycloak
 * Регистрация и логин происходят через Keycloak web view
 */
fun Route.authRoutes(keycloakService: KeycloakService) {
    
    /**
     * POST /refresh - обновить access token используя refresh token
     */
    post("/refresh") {
        val refreshToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Refresh token is required", 400)
                )
                return@post
            }

        val tokenResult = keycloakService.refreshToken(refreshToken)

        if (tokenResult.isFailure) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid or expired refresh token", 401)
            )
            return@post
        }

        val keycloakTokens = tokenResult.getOrThrow()

        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "accessToken" to keycloakTokens.access_token,
                "refreshToken" to (keycloakTokens.refresh_token ?: refreshToken)
            )
        )
    }
    
    /**
     * POST /loginOnToken - проверить валидность токена и получить информацию о пользователе
     */
    authenticate("auth-jwt") {
        post("/loginOnToken") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid token", 401)
                )
            
            val keycloakUserId = principal.payload.subject
            val email = principal.payload.getClaim("email")?.asString()
            val fullName = principal.payload.getClaim("name")?.asString()
                ?: principal.payload.getClaim("preferred_username")?.asString()
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "keycloakUserId" to keycloakUserId,
                    "email" to email,
                    "fullName" to fullName
                )
            )
        }
    }
}

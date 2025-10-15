package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.UserDAO
import ru.kingofraccoons.models.*
import ru.kingofraccoons.services.KeycloakService

fun Route.authRoutes(keycloakService: KeycloakService, userDAO: UserDAO) {
    
    post("/login") {
        val request = call.receiveOrBadRequest<LoginRequest>() ?: return@post

        if (request.email.isBlank() || request.password.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Email and password are required", 400)
            )
            return@post
        }

        val tokenResult = keycloakService.login(request.email, request.password)

        if (tokenResult.isFailure) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid credentials", 401)
            )
            return@post
        }

        val keycloakTokens = tokenResult.getOrThrow()

        var user = userDAO.findByEmail(request.email)
        if (user == null) {
            val keycloakUserResult = keycloakService.getUserByEmail(request.email)
            if (keycloakUserResult.isSuccess) {
                val keycloakUser = keycloakUserResult.getOrNull()
                if (keycloakUser != null) {
                    user = userDAO.createFromKeycloak(
                        email = request.email,
                        fullName = "${keycloakUser.firstName ?: ""} ${keycloakUser.lastName ?: ""}".trim(),
                        keycloakUserId = keycloakUser.id ?: ""
                    )
                }
            }
        }

        if (user == null) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Failed to get user information", 500)
            )
            return@post
        }

        call.respond(
            HttpStatusCode.OK,
            AuthResponse(
                id = user.id,
                email = user.email,
                fullName = user.fullName,
                accessToken = keycloakTokens.access_token,
                refreshToken = keycloakTokens.refresh_token ?: ""
            )
        )
    }
    
    post("/register") {
        val request = call.receiveOrBadRequest<RegisterRequest>() ?: return@post

        if (request.email.isBlank() || request.password.isBlank() || request.fullname.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Email, password and fullname are required", 400)
            )
            return@post
        }

        if (!isValidEmail(request.email)) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid email format", 400)
            )
            return@post
        }

        if (request.password.length < 6) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Password must be at least 6 characters", 400)
            )
            return@post
        }

        val existingUserResult = keycloakService.getUserByEmail(request.email)
        if (existingUserResult.isSuccess && existingUserResult.getOrNull() != null) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("User with this email already exists", 409)
            )
            return@post
        }

        val nameParts = request.fullname.trim().split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0) ?: request.fullname
        val lastName = nameParts.getOrNull(1)

        val registerResult = keycloakService.registerUser(
            username = request.email,
            email = request.email,
            password = request.password,
            firstName = firstName,
            lastName = lastName
        )

        if (registerResult.isFailure) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Failed to register user: ${registerResult.exceptionOrNull()?.message}", 400)
            )
            return@post
        }

        val keycloakUserId = registerResult.getOrThrow()

        val user = userDAO.createFromKeycloak(
            email = request.email,
            fullName = request.fullname,
            keycloakUserId = keycloakUserId
        )

        if (user == null) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Failed to create local user record", 500)
            )
            return@post
        }

        val tokenResult = keycloakService.login(request.email, request.password)

        if (tokenResult.isFailure) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("User created but failed to authenticate", 500)
            )
            return@post
        }

        val keycloakTokens = tokenResult.getOrThrow()

        call.respond(
            HttpStatusCode.Created,
            AuthResponse(
                id = user.id,
                email = user.email,
                fullName = user.fullName,
                accessToken = keycloakTokens.access_token,
                refreshToken = keycloakTokens.refresh_token ?: ""
            )
        )
    }
    
    // Refresh token endpoint
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
    
    authenticate("auth-jwt") {
        post("/loginOnToken") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: principal?.payload?.getClaim("preferred_username")?.asString()
            
            if (email == null) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid token", 401)
                )
            }
            
            val user = userDAO.findByEmail(email)
            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("User not found", 401)
                )
                return@post
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "id" to user.id,
                    "email" to user.email,
                    "fullName" to user.fullName
                )
            )
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return emailRegex.matches(email)
}

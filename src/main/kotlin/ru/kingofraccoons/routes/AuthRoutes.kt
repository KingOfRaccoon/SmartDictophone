package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.kingofraccoons.models.*
import ru.kingofraccoons.openapi.ParameterLocation
import ru.kingofraccoons.openapi.apiDoc
import ru.kingofraccoons.services.KeycloakService

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val tokenType: String = "Bearer"
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
    val message: String? = null
)

/**
 * Authentication routes
 * Регистрация, логин и обновление токенов через Keycloak API
 */
fun Route.authRoutes(keycloakService: KeycloakService) {
    apiDoc("POST", "/register") {
        summary = "Регистрация нового пользователя"
        description = "Создаёт нового пользователя в Keycloak и автоматически выполняет вход"
        tags = listOf("Authentication")

        requestBody(
            description = "Данные для регистрации",
            example = Json.encodeToString(
                RegisterRequest(
                    username = "john_doe",
                    email = "john@example.com",
                    password = "SecurePass123",
                    firstName = "John",
                    lastName = "Doe"
                )
            )
        )

        response(HttpStatusCode.Created, "Пользователь успешно создан и авторизован")
        response(HttpStatusCode.BadRequest, "Неверный формат данных или отсутствуют обязательные поля")
        response(HttpStatusCode.Conflict, "Пользователь с таким email или username уже существует")
    }
    
    /**
     * POST /register - регистрация нового пользователя
     */
    post("/register") {
        val request = call.receive<RegisterRequest>()
        
        // Валидация
        if (request.username.isBlank() || request.email.isBlank() || request.password.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Username, email and password are required", 400)
            )
            return@post
        }
        
        if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
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
        
        // Регистрация пользователя в Keycloak
        val result = keycloakService.registerUser(
            username = request.username,
            email = request.email,
            password = request.password,
            firstName = request.firstName,
            lastName = request.lastName
        )
        
        if (result.isFailure) {
            val error = result.exceptionOrNull()?.message ?: "Registration failed"
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(error, 409)
            )
            return@post
        }
        
        val userId = result.getOrThrow()
        
        // Автоматический логин после регистрации
        // Используем email для входа, так как Keycloak realm настроен на "Login with email"
        val loginResult = keycloakService.login(request.email, request.password)
        
        if (loginResult.isSuccess) {
            val tokens = loginResult.getOrThrow()
            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(
                    userId = userId,
                    accessToken = tokens.access_token,
                    refreshToken = tokens.refresh_token,
                    expiresIn = tokens.expires_in,
                    message = "User created and logged in successfully"
                )
            )
        } else {
            // Пользователь создан, но логин не удался
            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(
                    userId = userId,
                    message = "User created successfully. Please login with your email: ${request.email}"
                )
            )
        }
    }
    
    apiDoc("POST", "/login") {
        summary = "Вход в систему"
        description = "Аутентификация пользователя через email и пароль"
        tags = listOf("Authentication")

        requestBody(
            description = "Учётные данные пользователя",
            example = Json.encodeToString(
                LoginRequest(
                    email = "john@example.com",
                    password = "SecurePass123"
                )
            )
        )

        response(HttpStatusCode.OK, "Успешная аутентификация, возвращаются JWT токены")
        response(HttpStatusCode.BadRequest, "Не указан email или пароль")
        response(HttpStatusCode.Unauthorized, "Неверный email или пароль")
    }
    
    /**
     * POST /login - логин пользователя
     */
    post("/login") {
        val request = call.receive<LoginRequest>()
        
        if (request.email.isBlank() || request.password.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Email and password are required", 400)
            )
            return@post
        }
        
        val result = keycloakService.login(request.email, request.password)
        
        if (result.isFailure) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid email or password", 401)
            )
            return@post
        }
        
        val tokens = result.getOrThrow()
        
        call.respond(
            HttpStatusCode.OK,
            TokenResponse(
                accessToken = tokens.access_token,
                refreshToken = tokens.refresh_token,
                expiresIn = tokens.expires_in,
                tokenType = tokens.token_type
            )
        )
    }
    
    apiDoc("POST", "/refresh") {
        summary = "Обновление токена"
        description = "Получение нового access токена используя refresh токен"
        tags = listOf("Authentication")

        parameter("Authorization", "Bearer {refresh_token}", required = true, location = ParameterLocation.HEADER)

        response(HttpStatusCode.OK, "Новый access токен успешно получен")
        response(HttpStatusCode.BadRequest, "Refresh токен не указан")
        response(HttpStatusCode.Unauthorized, "Невалидный или истёкший refresh токен")
    }
    
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
        apiDoc("POST", "/loginOnToken") {
            summary = "Проверить токен и получить данные пользователя"
            description = "Проверяет валидность JWT токена и возвращает базовую информацию о пользователе из токена (Keycloak ID, email, имя)."
            tags = listOf("Authentication")
            parameter("Authorization", "Bearer {token}", required = true, type = "string", location = ParameterLocation.HEADER)
            response(HttpStatusCode.OK, "Токен валиден, информация о пользователе", "application/json") {
                """
                {
                  "keycloakUserId": "uuid",
                  "email": "user@example.com",
                  "fullName": "John Doe"
                }
                """.trimIndent()
            }
            response(HttpStatusCode.Unauthorized, "Недействительный или отсутствующий токен")
        }
        
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

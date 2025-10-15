package ru.kingofraccoons.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Serializable
data class KeycloakTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Int,
    val refresh_expires_in: Int? = null,
    val token_type: String
)

@Serializable
data class KeycloakUserRepresentation(
    val id: String? = null,
    val username: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val credentials: List<KeycloakCredential>? = null
)

@Serializable
data class KeycloakCredential(
    val type: String = "password",
    val value: String,
    val temporary: Boolean = false
)

@Serializable
data class KeycloakErrorResponse(
    val error: String? = null,
    val error_description: String? = null,
    val errorMessage: String? = null
)

class KeycloakService(config: Application) {
    private val keycloakConfig = config.environment.config.config("keycloak")
    
    private val serverUrl = keycloakConfig.property("serverUrl").getString()
    private val realm = keycloakConfig.property("realm").getString()
    private val clientId = keycloakConfig.property("clientId").getString()
    private val clientSecret = keycloakConfig.property("clientSecret").getString()
    private val adminUsername = keycloakConfig.property("adminUsername").getString()
    private val adminPassword = keycloakConfig.property("adminPassword").getString()
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private data class CachedToken(
        val value: String,
        val expiresAt: java.time.Instant
    )

    private var adminAccessToken: CachedToken? = null
    
    /**
     * Authenticate user with Keycloak and get access token
     */
    suspend fun login(username: String, password: String): Result<KeycloakTokenResponse> {
        return try {
            val response = httpClient.post("$serverUrl/realms/$realm/protocol/openid-connect/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("grant_type", "password")
                            append("username", username)
                            append("password", password)
                        }
                    )
                )
            }
            
            if (response.status.isSuccess()) {
                val tokenResponse = response.body<KeycloakTokenResponse>()
                logger.info { "User $username successfully authenticated with Keycloak" }
                Result.success(tokenResponse)
            } else {
                val errorResponse = try {
                    response.body<KeycloakErrorResponse>()
                } catch (e: Exception) {
                    KeycloakErrorResponse(error = "unknown", error_description = response.bodyAsText())
                }
                logger.warn { "Login failed for user $username: ${errorResponse.error_description}" }
                Result.failure(Exception(errorResponse.error_description ?: "Authentication failed"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during Keycloak login for user $username" }
            Result.failure(e)
        }
    }
    
    /**
     * Register a new user in Keycloak
     */
    suspend fun registerUser(
        username: String,
        email: String,
        password: String,
        firstName: String? = null,
        lastName: String? = null
    ): Result<String> {
        return try {
            // Get admin token
            val adminToken = getAdminToken().getOrElse {
                return Result.failure(Exception("Failed to get admin token: ${it.message}"))
            }
            
            val userRepresentation = KeycloakUserRepresentation(
                username = username,
                email = email,
                firstName = firstName,
                lastName = lastName,
                enabled = true,
                emailVerified = false,
                credentials = listOf(
                    KeycloakCredential(
                        type = "password",
                        value = password,
                        temporary = false
                    )
                )
            )
            
            val response = httpClient.post("$serverUrl/admin/realms/$realm/users") {
                contentType(ContentType.Application.Json)
                bearerAuth(adminToken)
                setBody(userRepresentation)
            }
            
            when (response.status) {
                HttpStatusCode.Created -> {
                    // Extract user ID from Location header
                    val location = response.headers["Location"]
                    val userId = location?.substringAfterLast("/") ?: ""
                    logger.info { "User $username successfully registered in Keycloak with ID: $userId" }
                    Result.success(userId)
                }
                HttpStatusCode.Conflict -> {
                    logger.warn { "User $username already exists in Keycloak" }
                    Result.failure(Exception("User already exists"))
                }
                else -> {
                    val errorText = response.bodyAsText()
                    logger.error { "Failed to register user $username: $errorText" }
                    Result.failure(Exception("Registration failed: $errorText"))
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during user registration in Keycloak" }
            Result.failure(e)
        }
    }
    
    /**
     * Get user info from Keycloak by user ID
     */
    suspend fun getUserById(userId: String): Result<KeycloakUserRepresentation> {
        return try {
            val adminToken = getAdminToken().getOrElse {
                return Result.failure(Exception("Failed to get admin token: ${it.message}"))
            }
            
            val response = httpClient.get("$serverUrl/admin/realms/$realm/users/$userId") {
                bearerAuth(adminToken)
            }
            
            if (response.status.isSuccess()) {
                val user = response.body<KeycloakUserRepresentation>()
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to get user info: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error getting user info from Keycloak" }
            Result.failure(e)
        }
    }
    
    /**
     * Search user by email
     */
    suspend fun getUserByEmail(email: String): Result<KeycloakUserRepresentation?> {
        return try {
            val adminToken = getAdminToken().getOrElse {
                return Result.failure(Exception("Failed to get admin token: ${it.message}"))
            }
            
            val response = httpClient.get("$serverUrl/admin/realms/$realm/users") {
                bearerAuth(adminToken)
                parameter("email", email)
                parameter("exact", true)
            }
            
            if (response.status.isSuccess()) {
                val users = response.body<List<KeycloakUserRepresentation>>()
                Result.success(users.firstOrNull())
            } else {
                Result.failure(Exception("Failed to search user: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error searching user in Keycloak" }
            Result.failure(e)
        }
    }
    
    /**
     * Refresh access token
     */
    suspend fun refreshToken(refreshToken: String): Result<KeycloakTokenResponse> {
        return try {
            val response = httpClient.post("$serverUrl/realms/$realm/protocol/openid-connect/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshToken)
                        }
                    )
                )
            }
            
            if (response.status.isSuccess()) {
                val tokenResponse = response.body<KeycloakTokenResponse>()
                Result.success(tokenResponse)
            } else {
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error refreshing token" }
            Result.failure(e)
        }
    }
    
    /**
     * Get admin access token
     */
    private suspend fun getAdminToken(): Result<String> {
        // Return cached token if still valid (with a short safety margin)
        adminAccessToken
            ?.takeIf { java.time.Instant.now().isBefore(it.expiresAt.minusSeconds(60)) }
            ?.let { return Result.success(it.value) }
        
        return try {
            val response = httpClient.post("$serverUrl/realms/master/protocol/openid-connect/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", "admin-cli")
                            append("grant_type", "password")
                            append("username", adminUsername)
                            append("password", adminPassword)
                        }
                    )
                )
            }
            
            if (response.status.isSuccess()) {
                val tokenResponse = response.body<KeycloakTokenResponse>()
                adminAccessToken = CachedToken(
                    value = tokenResponse.access_token,
                    expiresAt = java.time.Instant.now().plusSeconds(tokenResponse.expires_in.toLong())
                )
                logger.debug { "Admin token obtained successfully" }
                Result.success(tokenResponse.access_token)
            } else {
                logger.error { "Failed to get admin token: ${response.status}" }
                Result.failure(Exception("Failed to get admin token"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error getting admin token" }
            Result.failure(e)
        }
    }
    
    /**
     * Get public key for JWT verification from Keycloak
     */
    suspend fun getRealmPublicKey(): Result<String> {
        return try {
            val response = httpClient.get("$serverUrl/realms/$realm")
            
            if (response.status.isSuccess()) {
                val realmInfo = response.body<Map<String, Any>>()
                val publicKey = realmInfo["public_key"] as? String
                    ?: return Result.failure(Exception("Public key not found in realm info"))
                Result.success(publicKey)
            } else {
                Result.failure(Exception("Failed to get realm info"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error getting realm public key" }
            Result.failure(e)
        }
    }
    
    fun close() {
        httpClient.close()
    }
}

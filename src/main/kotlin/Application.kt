package ru.kingofraccoons

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import ru.kingofraccoons.dao.*
import ru.kingofraccoons.database.DatabaseFactory
import ru.kingofraccoons.models.ErrorResponse
import ru.kingofraccoons.routes.*
import ru.kingofraccoons.services.KeycloakService
import ru.kingofraccoons.services.PdfService
import ru.kingofraccoons.services.RabbitMQService
import ru.kingofraccoons.services.S3Service
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init(this)
    
    // Initialize services
    val keycloakService = KeycloakService(this)
    val s3Service = S3Service(this)
    val pdfService = PdfService()
    val rabbitMQService = RabbitMQService(this)
    
    // Initialize DAOs (без userDAO - используем только Keycloak)
    val folderDAO = FolderDAO()
    val recordDAO = RecordDAO()
    val transcriptionDAO = TranscriptionDAO()
    
    // API Key from config
    val apiKey = environment.config.config("api").property("key").getString()
    
    // Keycloak config
    val keycloakConfig = environment.config.config("keycloak")
    val keycloakServerUrl = keycloakConfig.property("serverUrl").getString()
    val keycloakPublicUrl = keycloakConfig.propertyOrNull("publicUrl")?.getString() ?: keycloakServerUrl
    val keycloakRealm = keycloakConfig.property("realm").getString()
    
    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-API-Key")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    
    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
    }
    
    install(PartialContent)
    
    // Get Keycloak public key for JWT verification
    logger.info { "Fetching Keycloak realm public key from $keycloakServerUrl..." }
    val realmPublicKeyResult = runBlocking {
        keycloakService.getRealmPublicKey()
    }
    
    val realmPublicKey = realmPublicKeyResult.getOrNull()
    
    if (realmPublicKey != null) {
        logger.info { "Successfully retrieved Keycloak public key (${realmPublicKey.take(50)}...)" }
    } else {
        logger.error { "Failed to retrieve Keycloak public key: ${realmPublicKeyResult.exceptionOrNull()?.message}" }
    }
    
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "smart-dictophone"
            
            // Configure JWT verification with Keycloak public key
            if (realmPublicKey != null) {
                logger.info { "Configuring JWT verifier with issuer: $keycloakPublicUrl/realms/$keycloakRealm" }
                verifier(
                    JWT
                        .require(Algorithm.RSA256(convertPublicKey(realmPublicKey), null))
                        .withIssuer("$keycloakPublicUrl/realms/$keycloakRealm")
                        .build()
                )
            } else {
                logger.warn { "Failed to get Keycloak public key, JWT verification may fail" }
            }
            
            validate { credential ->
                // Check if token has required claims (email or preferred_username)
                val email = credential.payload.getClaim("email")?.asString()
                    ?: credential.payload.getClaim("preferred_username")?.asString()
                
                if (email != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Token is not valid or has expired", 401)
                )
            }
        }
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error: ${cause.message}", 500)
            )
        }
        
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Endpoint not found", 404)
            )
        }
    }
    
    // Configure routing
    routing {
        // API Documentation - Swagger UI доступен по /swagger-ui
        swaggerUI(path = "swagger-ui", swaggerFile = "openapi/documentation.yaml")
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
        
        // Application routes
        authRoutes(keycloakService)
        userRoutes(recordDAO, folderDAO)
        recordRoutes(recordDAO, transcriptionDAO, folderDAO, s3Service, pdfService, rabbitMQService, apiKey)
        folderRoutes(folderDAO)
        
        get("/") {
            call.respondText("Smart Dictophone API v1.0 (Keycloak Integration)\n\nAPI Documentation: /swagger-ui", ContentType.Text.Plain)
        }
        
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }
    }
    
    // Shutdown hook
    monitor.subscribe(ApplicationStopped) {
        s3Service.close()
        keycloakService.close()
        rabbitMQService.close()
        logger.info { "Application stopped" }
    }
}

/**
 * Convert PEM public key string to RSAPublicKey
 */
private fun convertPublicKey(publicKeyPEM: String): RSAPublicKey {
    val publicKeyContent = publicKeyPEM
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\\s+".toRegex(), "")
    
    val keyBytes = Base64.getDecoder().decode(publicKeyContent)
    val keySpec = X509EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    
    return keyFactory.generatePublic(keySpec) as RSAPublicKey
}

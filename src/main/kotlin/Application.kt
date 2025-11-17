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
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import ru.kingofraccoons.dao.*
import ru.kingofraccoons.database.DatabaseFactory
import ru.kingofraccoons.models.ErrorResponse
import ru.kingofraccoons.openapi.OpenApiGenerator
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
        filter { call -> call.request.path() != "/health" }
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
                // Accept multiple issuers by not setting issuer constraint in verifier
                // Instead, we'll validate the issuer manually in the validate block
                logger.info { "Configuring JWT verifier without issuer constraint" }
                logger.info { "Will accept issuers:" }
                logger.info { "  - Internal: $keycloakServerUrl/realms/$keycloakRealm" }
                logger.info { "  - Public: $keycloakPublicUrl/realms/$keycloakRealm" }
                logger.info { "  - Localhost 8080: http://localhost:8080/realms/$keycloakRealm" }
                logger.info { "  - Localhost 8090: http://localhost:8090/realms/$keycloakRealm" }
                
                // Create verifier without issuer constraint
                verifier(
                    JWT
                        .require(Algorithm.RSA256(convertPublicKey(realmPublicKey), null))
                        // Don't set issuer here - we'll check it manually
                        .build()
                )
            } else {
                logger.warn { "Failed to get Keycloak public key, JWT verification may fail" }
            }
            
            validate { credential ->
                try {
                    // Check issuer manually to support multiple issuers
                    val issuer = credential.payload.issuer
                    val acceptedIssuers = listOf(
                        "$keycloakServerUrl/realms/$keycloakRealm",
                        "$keycloakPublicUrl/realms/$keycloakRealm",
                        "http://localhost:8080/realms/$keycloakRealm",
                        "http://localhost:8090/realms/$keycloakRealm"
                    )
                    
                    if (!acceptedIssuers.contains(issuer)) {
                        logger.warn { "Token issuer not accepted: $issuer. Expected one of: $acceptedIssuers" }
                        return@validate null
                    }
                    
                    // Check if token has required claims
                    val subject = credential.payload.subject
                    val email = credential.payload.getClaim("email")?.asString()
                        ?: credential.payload.getClaim("preferred_username")?.asString()
                    
                    logger.debug { "Validating token - Issuer: $issuer, Subject: $subject, Email: $email" }
                    
                    if (email != null && subject != null) {
                        logger.debug { "Token validated successfully for: $email" }
                        JWTPrincipal(credential.payload)
                    } else {
                        logger.warn { "Token validation failed - missing email or subject" }
                        null
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Token validation error: ${e.message}" }
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
        // Swagger UI поверх актуальной OpenAPI спецификации
        get("/swagger-ui") {
            call.respondText(
                swaggerUiHtmlTemplate(specUrl = "/openapi.json?raw=true"),
                ContentType.Text.Html
            )
        }
        
        // Endpoint для динамически генерируемой OpenAPI спецификации
        get("/openapi.json") {
            val spec = OpenApiGenerator.generateSpec(
                title = "Smart Dictophone API",
                version = "1.0.0",
                description = """
                    REST API для умного диктофона с автоматической транскрипцией и организацией записей.
                    
                    ## Аутентификация
                    API использует Keycloak для аутентификации.
                """.trimIndent(),
                servers = listOf(
                    OpenApiGenerator.Server("http://localhost:8888", "Локальная разработка"),
                    OpenApiGenerator.Server(
                        environment.config.propertyOrNull("application.publicBaseUrl")?.getString()
                            ?: "https://api.smartdictophone.com",
                        "Production"
                    )
                ),
                securitySchemes = mapOf(
                    "BearerAuth" to OpenApiGenerator.SecurityScheme(
                        type = "http",
                        scheme = "bearer",
                        bearerFormat = "JWT",
                        description = "JWT токен от Keycloak"
                    ),
                    "ApiKeyAuth" to OpenApiGenerator.SecurityScheme(
                        type = "apiKey",
                        `in` = "header",
                        name = "X-API-Key",
                        description = "API ключ для сервисных запросов"
                    )
                )
            )

            val acceptHeader = call.request.headers[HttpHeaders.Accept]
            val wantsRawJson = call.request.queryParameters["raw"].isTruthy()
            val wantsUi = call.request.queryParameters["ui"].isTruthy() ||
                (!wantsRawJson && acceptHeader.isHtmlPreferred() && !acceptHeader.isJsonPreferred())

            if (wantsUi) {
                call.respondText(
                    swaggerUiHtmlTemplate(specUrl = "/openapi.json?raw=true"),
                    ContentType.Text.Html
                )
            } else {
                call.respond(spec)
            }
        }
        
        // Application routes
        authRoutes(keycloakService)
        userRoutes(recordDAO, folderDAO, keycloakService)
        recordRoutes(recordDAO, transcriptionDAO, folderDAO, s3Service, pdfService, rabbitMQService, apiKey)
        folderRoutes(folderDAO, recordDAO, transcriptionDAO, s3Service)
        
        get("/") {
            call.respondText(
                "Smart Dictophone API v1.0 (Keycloak Integration)\n\n" +
                "API Documentation:\n" +
                "- Swagger UI: /swagger-ui\n" +
                "- OpenAPI JSON (generated): /openapi.json",
                ContentType.Text.Plain
            )
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

private fun String?.isTruthy(): Boolean {
    if (this == null) return false
    return when (lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }
}

private fun String?.isHtmlPreferred(): Boolean {
    return this?.split(',')
        ?.map { it.substringBefore(';').trim() }
        ?.any { it.equals(ContentType.Text.Html.toString(), ignoreCase = true) || it.equals("application/xhtml+xml", ignoreCase = true) }
        ?: false
}

private fun String?.isJsonPreferred(): Boolean {
    return this?.split(',')
        ?.map { it.substringBefore(';').trim() }
        ?.any { it.equals(ContentType.Application.Json.toString(), ignoreCase = true) || it == "application/json" }
        ?: false
}

private fun swaggerUiHtmlTemplate(specUrl: String): String {
    return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>Smart Dictophone API</title>
            <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
            <style>
                body { margin: 0; padding: 0; }
            </style>
        </head>
        <body>
            <div id="swagger-ui"></div>
            <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
            <script>
                window.onload = () => {
                    SwaggerUIBundle({
                        dom_id: '#swagger-ui',
                        url: '$specUrl',
                        layout: 'BaseLayout'
                    });
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}

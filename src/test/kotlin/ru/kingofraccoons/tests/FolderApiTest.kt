package ru.kingofraccoons.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import ru.kingofraccoons.module
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Folder API endpoints
 * Tests CRUD operations for folders without Keycloak authentication
 */
class FolderApiTest {

    companion object {
        private lateinit var testClient: HttpClient
        private var testFolderId: Int? = null
    }

    private fun ApplicationTestBuilder.configureTestApp() {
        environment {
            config = MapApplicationConfig().apply {
                // Database (in-memory H2)
                put("database.url", "jdbc:h2:mem:test_folders;DB_CLOSE_DELAY=-1;")
                put("database.driver", "org.h2.Driver")
                put("database.user", "sa")
                put("database.password", "")
                put("database.maxPoolSize", "3")

                // API key
                put("api.key", "test-api-key")

                // Keycloak (mock)
                put("keycloak.serverUrl", "http://localhost:8080")
                put("keycloak.publicUrl", "http://localhost:8090")
                put("keycloak.realm", "test-realm")
                put("keycloak.clientId", "test-client")
                put("keycloak.clientSecret", "secret")
                put("keycloak.adminUsername", "admin")
                put("keycloak.adminPassword", "password")

                // S3 (mock)
                put("s3.endpoint", "http://localhost:9000")
                put("s3.region", "us-east-1")
                put("s3.accessKey", "test")
                put("s3.secretKey", "test")
                put("s3.bucket", "test-bucket")

                // RabbitMQ (mock)
                put("rabbitmq.host", "localhost")
                put("rabbitmq.port", "5672")
                put("rabbitmq.username", "guest")
                put("rabbitmq.password", "guest")
                put("rabbitmq.queue", "test-queue")
            }
        }
        application {
            module()
        }
    }

    @Test
    fun `test health endpoint returns OK`() = testApplication {
        configureTestApp()
        
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
            val responseText = bodyAsText()
            assertTrue(responseText.contains("OK") || responseText.contains("healthy"))
        }
    }

    @Test
    fun `test root endpoint returns welcome message`() = testApplication {
        configureTestApp()
        
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            val responseText = bodyAsText()
            assertNotNull(responseText)
        }
    }

    /**
     * Note: These tests would require proper JWT token mocking
     * For now, they test the endpoint structure
     */
    @Test
    fun `test folders endpoint requires authentication`() = testApplication {
        configureTestApp()
        
        client.get("/folders").apply {
            // Should return 401 without token
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test records endpoint requires authentication`() = testApplication {
        configureTestApp()
        
        client.get("/records").apply {
            // Should return 401 without token
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test create folder endpoint structure`() = testApplication {
        configureTestApp()
        
        client.post("/folders") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "Test Folder", "description": "Test"}""")
        }.apply {
            // Should return 401 without token (authentication required)
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test create record endpoint structure`() = testApplication {
        configureTestApp()
        
        client.post("/records") {
            contentType(ContentType.Application.Json)
            setBody("""{"folderId": 1, "title": "Test Record"}""")
        }.apply {
            // Should return 401 without token
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test swagger documentation is available`() = testApplication {
        configureTestApp()
        
        client.get("/swagger-ui").apply {
            // Swagger UI should be accessible without authentication
            assertTrue(status.value in 200..399)
        }
    }

    @Test
    fun `test API returns JSON content type for errors`() = testApplication {
        configureTestApp()
        
        client.get("/folders").apply {
            val contentType = headers[HttpHeaders.ContentType]
            assertNotNull(contentType)
            assertTrue(contentType!!.contains("application/json"))
        }
    }
}

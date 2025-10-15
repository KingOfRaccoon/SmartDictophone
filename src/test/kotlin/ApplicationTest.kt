package ru.kingofraccoons

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                // Database (in-memory H2)
                put("database.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;")
                put("database.driver", "org.h2.Driver")
                put("database.user", "sa")
                put("database.password", "")
                put("database.maxPoolSize", "3")

                // API key used by internal endpoints
                put("api.key", "test-api-key")

                // Keycloak (dummy values for tests)
                put("keycloak.serverUrl", "http://localhost:8080")
                put("keycloak.realm", "test-realm")
                put("keycloak.clientId", "test-client")
                put("keycloak.clientSecret", "secret")
                put("keycloak.adminUsername", "admin")
                put("keycloak.adminPassword", "password")

                // S3 (dummy values to satisfy configuration)
                put("s3.endpoint", "http://localhost:9000")
                put("s3.region", "us-east-1")
                put("s3.accessKey", "test")
                put("s3.secretKey", "test")
                put("s3.bucket", "test-bucket")
            }
        }
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}

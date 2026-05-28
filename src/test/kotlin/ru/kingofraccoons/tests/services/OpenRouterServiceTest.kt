package ru.kingofraccoons.tests.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import ru.kingofraccoons.services.OpenRouterService

class OpenRouterServiceTest {
    @Test
    fun `summarize should return summary text and metadata`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"choices":[{"message":{"role":"assistant","content":"Summary result"}}],"model":"test-model","usage":{"prompt_tokens":100,"completion_tokens":50}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val service = OpenRouterService(
            apiKey = "test-key",
            model = "test-model",
            baseUrl = "https://openrouter.ai/api/v1",
            maxRetries = 1,
            retryDelayMs = 100,
            httpClient = mockClient
        )

        val result = service.summarize("Long transcript text")

        assertEquals("Summary result", result.text)
        assertEquals("test-model", result.modelUsed)
        assertEquals(100, result.promptTokens)
        assertEquals(50, result.completionTokens)
    }

    @Test
    fun `summarize should retry on 500 and eventually fail`() = runTest {
        var requestCount = 0
        val mockEngine = MockEngine { _ ->
            requestCount++
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        val service = OpenRouterService(
            apiKey = "test-key",
            model = "test-model",
            baseUrl = "https://openrouter.ai/api/v1",
            maxRetries = 2,
            retryDelayMs = 10,
            httpClient = mockClient
        )

        assertFailsWith<Exception> {
            service.summarize("Text")
        }
        assertEquals(2, requestCount)
    }
}

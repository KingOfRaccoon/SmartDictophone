package ru.kingofraccoons.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class OpenRouterService(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val maxRetries: Int = 3,
    private val retryDelayMs: Long = 5000,
    private val httpClient: HttpClient? = null
) {
    constructor(config: Application) : this(
        apiKey = config.environment.config.config("openrouter").property("apiKey").getString(),
        model = config.environment.config.config("openrouter").property("model").getString(),
        baseUrl = config.environment.config.config("openrouter").property("baseUrl").getString(),
        maxRetries = config.environment.config.config("openrouter").propertyOrNull("maxRetries")?.getString()?.toInt() ?: 3,
        retryDelayMs = config.environment.config.config("openrouter").propertyOrNull("retryDelayMs")?.getString()?.toLong() ?: 5000
    )

    private val client = httpClient ?: HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        defaultRequest {
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://smartdictophone.com")
            header("X-Title", "Smart Dictophone")
        }
    }

    suspend fun summarize(transcript: String): SummaryResult {
        val prompt = buildPrompt(transcript)
        val request = OpenRouterRequest(
            model = model,
            messages = listOf(
                Message("system", "You are a helpful assistant that summarizes transcripts concisely in Russian."),
                Message("user", prompt)
            )
        )

        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = client.post("$baseUrl/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                when {
                    response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500 -> {
                        logger.warn { "OpenRouter returned ${response.status}, retrying in ${retryDelayMs * (attempt + 1)}ms (attempt ${attempt + 1}/$maxRetries)" }
                        delay(retryDelayMs * (attempt + 1))
                        return@repeat
                    }
                    !response.status.isSuccess() -> {
                        throw Exception("OpenRouter API error: ${response.status}, body: ${response.body<String>()}")
                    }
                }

                val body = response.body<OpenRouterResponse>()
                val choice = body.choices.firstOrNull()
                    ?: throw Exception("No choices in OpenRouter response")

                return SummaryResult(
                    text = choice.message.content,
                    modelUsed = body.model ?: model,
                    promptTokens = body.usage?.promptTokens,
                    completionTokens = body.usage?.completionTokens
                )
            } catch (e: Exception) {
                lastException = e
                logger.warn { "OpenRouter attempt ${attempt + 1}/$maxRetries failed: ${e::class.simpleName}: ${e.message}" }
                if (attempt < maxRetries - 1) {
                    delay(retryDelayMs * (attempt + 1))
                }
            }
        }

        throw lastException ?: Exception("OpenRouter call failed after $maxRetries retries")
    }

    private fun buildPrompt(transcript: String): String {
        return """Проанализируй следующую транскрипцию аудиозаписи и создай краткое содержание (суммаризацию) на русском языке. Выдели основные темы, ключевые выводы и действия.

Транскрипция:
$transcript"""
    }
}

data class SummaryResult(
    val text: String,
    val modelUsed: String,
    val promptTokens: Int?,
    val completionTokens: Int?
)

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterResponse(
    val choices: List<Choice>,
    val model: String? = null,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: Message
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null
)

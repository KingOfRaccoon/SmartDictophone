# Transcription Summarization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automatic summarization of transcriptions via OpenRouter with processing statuses for better UX.

**Architecture:** After ML service saves transcription segments, the backend publishes a summary task to a new RabbitMQ queue. An in-process consumer in Ktor calls OpenRouter API, persists the result, and updates processing statuses. New tables `ProcessingStatuses` and `Summaries` track progress and store results.

**Tech Stack:** Kotlin, Ktor, Exposed ORM, PostgreSQL, RabbitMQ (`amqp-client`), OpenRouter API, kotlinx.serialization, Ktor HTTP Client (CIO engine)

---

## File Structure

### New files

| File | Responsibility |
|------|---------------|
| `src/main/kotlin/ru/kingofraccoons/models/ProcessingStatus.kt` | Exposed table `ProcessingStatuses` + entity `ProcessingStatus` + response DTO |
| `src/main/kotlin/ru/kingofraccoons/models/Summary.kt` | Exposed table `Summaries` + entity `Summary` + response DTO |
| `src/main/kotlin/ru/kingofraccoons/models/RecordWithSummaryResponse.kt` | Extended record DTO with embedded `summary` and `statuses` |
| `src/main/kotlin/ru/kingofraccoons/dao/ProcessingStatusDAO.kt` | CRUD for processing statuses |
| `src/main/kotlin/ru/kingofraccoons/dao/SummaryDAO.kt` | CRUD for summaries |
| `src/main/kotlin/ru/kingofraccoons/services/OpenRouterService.kt` | HTTP client for OpenRouter API with retry |
| `src/main/kotlin/ru/kingofraccoons/services/SummaryService.kt` | Business logic: fetch transcript, call OpenRouter, persist result |
| `src/test/kotlin/ru/kingofraccoons/tests/dao/ProcessingStatusDaoTest.kt` | DAO tests via Testcontainers |
| `src/test/kotlin/ru/kingofraccoons/tests/dao/SummaryDaoTest.kt` | DAO tests via Testcontainers |
| `src/test/kotlin/ru/kingofraccoons/tests/services/OpenRouterServiceTest.kt` | Service tests with Ktor `MockEngine` |

### Modified files

| File | Changes |
|------|---------|
| `src/main/kotlin/ru/kingofraccoons/models/Entities.kt` | Import new DTOs (or leave untouched if extracted to separate files) |
| `src/main/kotlin/ru/kingofraccoons/database/DatabaseFactory.kt` | Register `ProcessingStatuses` and `Summaries` in `createMissingTablesAndColumns` |
| `src/main/kotlin/ru/kingofraccoons/services/RabbitMQService.kt` | Add `summaryQueue`, `sendSummaryTask()`, `startSummaryConsumer()` |
| `src/main/kotlin/ru/kingofraccoons/routes/RecordRoutes.kt` | Add `GET /records/{id}/summary`, `GET /records/{id}/statuses`, modify `GET /records/{id}` and `POST /records/{id}/transcribe` |
| `src/main/kotlin/ru/kingofraccoons/Application.kt` | Instantiate new DAOs and services; launch summary consumer coroutine |
| `src/main/resources/application.yaml` | Add `openrouter.*` and `rabbitmq.summaryQueue` config |

---

## Prerequisites

Ensure these dependencies already exist in `build.gradle.kts` (they should from the existing codebase):

```kotlin
implementation("io.ktor:ktor-client-core")
implementation("io.ktor:ktor-client-cio")
implementation("io.ktor:ktor-client-content-negotiation")
implementation("com.rabbitmq:amqp-client:5.20.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
```

If any are missing, add them before proceeding.

---

### Task 1: Add ProcessingStatuses table, entity, and DTO

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/models/ProcessingStatus.kt`

- [ ] **Step 1: Write the file**

```kotlin
package ru.kingofraccoons.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object ProcessingStatuses : LongIdTable("processing_statuses") {
    val recordId = reference("record_id", Records)
    val stage = varchar("stage", 50)
    val status = varchar("status", 20)
    val errorMessage = text("error_message").nullable()
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    init {
        uniqueIndex("processing_statuses_record_stage_idx", recordId, stage)
    }
}

@Serializable
data class ProcessingStatus(
    val id: Long,
    val recordId: Long,
    val stage: String,
    val status: String,
    val errorMessage: String?,
    val updatedAt: String
)

@Serializable
data class ProcessingStatusResponse(
    val stage: String,
    val status: String,
    val errorMessage: String?,
    val updatedAt: String
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 2: Add Summaries table, entity, and DTO

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/models/Summary.kt`

- [ ] **Step 1: Write the file**

```kotlin
package ru.kingofraccoons.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Summaries : LongIdTable("summaries") {
    val recordId = reference("record_id", Records).uniqueIndex()
    val summaryText = text("summary_text")
    val modelUsed = varchar("model_used", 100)
    val promptTokens = integer("prompt_tokens").nullable()
    val completionTokens = integer("completion_tokens").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
}

@Serializable
data class Summary(
    val id: Long,
    val recordId: Long,
    val summaryText: String,
    val modelUsed: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val createdAt: String
)

@Serializable
data class SummaryResponse(
    val summaryText: String,
    val modelUsed: String,
    val createdAt: String
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 3: Add RecordWithSummaryResponse DTO

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/models/RecordWithSummaryResponse.kt`

- [ ] **Step 1: Write the file**

```kotlin
package ru.kingofraccoons.models

import kotlinx.serialization.Serializable

@Serializable
data class RecordWithSummaryResponse(
    val id: Long,
    val folderId: Long?,
    val title: String,
    val description: String?,
    val datetime: String,
    val latitude: Float?,
    val longitude: Float?,
    val duration: Int,
    val category: String,
    val audioUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val summary: SummaryResponse?,
    val statuses: List<ProcessingStatusResponse>
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 4: Register new tables in DatabaseFactory

**Files:**
- Modify: `src/main/kotlin/ru/kingofraccoons/database/DatabaseFactory.kt`

- [ ] **Step 1: Add imports**

```kotlin
import ru.kingofraccoons.models.ProcessingStatuses
import ru.kingofraccoons.models.Summaries
```

- [ ] **Step 2: Update createMissingTablesAndColumns call**

Replace:
```kotlin
SchemaUtils.createMissingTablesAndColumns(UserProfiles, Folders, Records, TranscriptionSegments, SharedRecords)
```

With:
```kotlin
SchemaUtils.createMissingTablesAndColumns(
    UserProfiles, Folders, Records, TranscriptionSegments,
    SharedRecords, ProcessingStatuses, Summaries
)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 5: Create ProcessingStatusDAO

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/dao/ProcessingStatusDAO.kt`

- [ ] **Step 1: Write the DAO**

```kotlin
package ru.kingofraccoons.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ru.kingofraccoons.database.dbQuery
import ru.kingofraccoons.models.ProcessingStatus
import ru.kingofraccoons.models.ProcessingStatuses
import java.time.LocalDateTime

class ProcessingStatusDAO {
    suspend fun createOrUpdate(
        recordId: Long,
        stage: String,
        status: String,
        errorMessage: String? = null
    ): ProcessingStatus? = dbQuery {
        ProcessingStatuses.upsert(ProcessingStatuses.recordId, ProcessingStatuses.stage) {
            it[ProcessingStatuses.recordId] = recordId
            it[ProcessingStatuses.stage] = stage
            it[ProcessingStatuses.status] = status
            it[ProcessingStatuses.errorMessage] = errorMessage
            it[updatedAt] = LocalDateTime.now()
        }
        findByRecordIdAndStage(recordId, stage)
    }

    suspend fun update(
        recordId: Long,
        stage: String,
        status: String,
        errorMessage: String? = null
    ): ProcessingStatus? = dbQuery {
        ProcessingStatuses.update({ (ProcessingStatuses.recordId eq recordId) and (ProcessingStatuses.stage eq stage) }) {
            it[ProcessingStatuses.status] = status
            it[ProcessingStatuses.errorMessage] = errorMessage
            it[updatedAt] = LocalDateTime.now()
        }
        findByRecordIdAndStage(recordId, stage)
    }

    suspend fun findByRecordId(recordId: Long): List<ProcessingStatus> = dbQuery {
        ProcessingStatuses.selectAll().where { ProcessingStatuses.recordId eq recordId }
            .orderBy(ProcessingStatuses.stage to SortOrder.ASC)
            .map(::resultRowToProcessingStatus)
    }

    private suspend fun findByRecordIdAndStage(recordId: Long, stage: String): ProcessingStatus? = dbQuery {
        ProcessingStatuses.selectAll()
            .where { (ProcessingStatuses.recordId eq recordId) and (ProcessingStatuses.stage eq stage) }
            .map(::resultRowToProcessingStatus)
            .singleOrNull()
    }

    private fun resultRowToProcessingStatus(row: ResultRow) = ProcessingStatus(
        id = row[ProcessingStatuses.id].value,
        recordId = row[ProcessingStatuses.recordId].value,
        stage = row[ProcessingStatuses.stage],
        status = row[ProcessingStatuses.status],
        errorMessage = row[ProcessingStatuses.errorMessage],
        updatedAt = row[ProcessingStatuses.updatedAt].toString()
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 6: Write ProcessingStatusDaoTest

**Files:**
- Create: `src/test/kotlin/ru/kingofraccoons/tests/dao/ProcessingStatusDaoTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.dao.ProcessingStatusDAO
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest

class ProcessingStatusDaoTest : BaseDaoTest() {
    private val dao = ProcessingStatusDAO()

    @Test
    fun `createOrUpdate should create new status`() = runTest {
        val record = createTestRecord()
        val status = dao.createOrUpdate(record.id, "summarization", "pending")

        assertNotNull(status)
        assertEquals("summarization", status!!.stage)
        assertEquals("pending", status.status)
    }

    @Test
    fun `createOrUpdate should update existing status`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "summarization", "pending")
        val updated = dao.createOrUpdate(record.id, "summarization", "completed")

        assertNotNull(updated)
        assertEquals("completed", updated!!.status)
    }

    @Test
    fun `findByRecordId should return all statuses for record`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "transcription", "completed")
        dao.createOrUpdate(record.id, "summarization", "pending")

        val statuses = dao.findByRecordId(record.id)
        assertEquals(2, statuses.size)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "ru.kingofraccoons.tests.dao.ProcessingStatusDaoTest"`
Expected: All 3 tests PASS

---

### Task 7: Create SummaryDAO

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/dao/SummaryDAO.kt`

- [ ] **Step 1: Write the DAO**

```kotlin
package ru.kingofraccoons.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ru.kingofraccoons.database.dbQuery
import ru.kingofraccoons.models.Summaries
import ru.kingofraccoons.models.Summary

class SummaryDAO {
    suspend fun createOrUpdate(
        recordId: Long,
        summaryText: String,
        modelUsed: String,
        promptTokens: Int?,
        completionTokens: Int?
    ): Summary? = dbQuery {
        Summaries.upsert(Summaries.recordId) {
            it[Summaries.recordId] = recordId
            it[Summaries.summaryText] = summaryText
            it[Summaries.modelUsed] = modelUsed
            it[Summaries.promptTokens] = promptTokens
            it[Summaries.completionTokens] = completionTokens
        }
        findByRecordId(recordId)
    }

    suspend fun findByRecordId(recordId: Long): Summary? = dbQuery {
        Summaries.selectAll().where { Summaries.recordId eq recordId }
            .map(::resultRowToSummary)
            .singleOrNull()
    }

    private fun resultRowToSummary(row: ResultRow) = Summary(
        id = row[Summaries.id].value,
        recordId = row[Summaries.recordId].value,
        summaryText = row[Summaries.summaryText],
        modelUsed = row[Summaries.modelUsed],
        promptTokens = row[Summaries.promptTokens],
        completionTokens = row[Summaries.completionTokens],
        createdAt = row[Summaries.createdAt].toString()
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 8: Write SummaryDaoTest

**Files:**
- Create: `src/test/kotlin/ru/kingofraccoons/tests/dao/SummaryDaoTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.dao.SummaryDAO
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest

class SummaryDaoTest : BaseDaoTest() {
    private val dao = SummaryDAO()

    @Test
    fun `createOrUpdate should create summary`() = runTest {
        val record = createTestRecord()
        val summary = dao.createOrUpdate(record.id, "Test summary", "test-model", 100, 50)

        assertNotNull(summary)
        assertEquals("Test summary", summary!!.summaryText)
        assertEquals("test-model", summary.modelUsed)
        assertEquals(100, summary.promptTokens)
        assertEquals(50, summary.completionTokens)
    }

    @Test
    fun `createOrUpdate should update existing summary`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "First", "model-1", 100, 50)
        val updated = dao.createOrUpdate(record.id, "Second", "model-2", 200, 100)

        assertNotNull(updated)
        assertEquals("Second", updated!!.summaryText)
        assertEquals("model-2", updated.modelUsed)
    }

    @Test
    fun `findByRecordId should return summary`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "Test", "model", null, null)

        val found = dao.findByRecordId(record.id)
        assertNotNull(found)
        assertEquals("Test", found!!.summaryText)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "ru.kingofraccoons.tests.dao.SummaryDaoTest"`
Expected: All 3 tests PASS

---

### Task 9: Create OpenRouterService

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/services/OpenRouterService.kt`

- [ ] **Step 1: Write the service**

```kotlin
package ru.kingofraccoons.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
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

    private val client = httpClient ?: HttpClient(CIO) {
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 10: Write OpenRouterServiceTest

**Files:**
- Create: `src/test/kotlin/ru/kingofraccoons/tests/services/OpenRouterServiceTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ru.kingofraccoons.tests.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.services.OpenRouterService

class OpenRouterServiceTest {
    @Test
    fun `summarize should return summary text and metadata`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"choices":[{"message":{"content":"Summary result"}}],"model":"test-model","usage":{"prompt_tokens":100,"completion_tokens":50}}""",
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

        assertThrows(Exception::class.java) {
            runTest { service.summarize("Text") }
        }
        assertEquals(2, requestCount)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "ru.kingofraccoons.tests.services.OpenRouterServiceTest"`
Expected: Both tests PASS

---

### Task 11: Add summary queue to RabbitMQService

**Files:**
- Modify: `src/main/kotlin/ru/kingofraccoons/services/RabbitMQService.kt`

- [ ] **Step 1: Add summary queue configuration and imports**

Add imports:
```kotlin
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
```

Add property:
```kotlin
private val summaryQueueName = rabbitConfig.propertyOrNull("summaryQueue")?.getString() ?: "summary-tasks"
```

- [ ] **Step 2: Add sendSummaryTask method**

Add inside the class:
```kotlin
fun sendSummaryTask(recordId: Long) {
    try {
        val message = recordId.toString()
        logger.info { "Sending summary task to RabbitMQ: $message" }
        channel?.basicPublish("", summaryQueueName, null, message.toByteArray(Charsets.UTF_8))
        logger.info { "Sent summary task for record ID: $recordId" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to send summary task for record ID: $recordId" }
        throw e
    }
}
```

- [ ] **Step 3: Add startSummaryConsumer method**

Add inside the class:
```kotlin
fun startSummaryConsumer(onMessage: suspend (Long) -> Unit) {
    thread(name = "rabbitmq-summary-consumer", isDaemon = true) {
        try {
            val consumerChannel = connection?.createChannel()
            consumerChannel?.queueDeclare(summaryQueueName, true, false, false, null)

            val consumer = object : DefaultConsumer(consumerChannel) {
                override fun handleDelivery(
                    tag: String,
                    envelope: Envelope,
                    properties: AMQP.BasicProperties,
                    body: ByteArray
                ) {
                    val recordId = String(body, Charsets.UTF_8).toLongOrNull()
                    if (recordId == null) {
                        consumerChannel?.basicAck(envelope.deliveryTag, false)
                        return
                    }

                    logger.info { "Received summary task for record ID: $recordId" }

                    runBlocking {
                        try {
                            onMessage(recordId)
                            consumerChannel?.basicAck(envelope.deliveryTag, false)
                            logger.info { "Summary task completed for record ID: $recordId" }
                        } catch (e: Exception) {
                            logger.error(e) { "Summary task failed for record ID: $recordId" }
                            consumerChannel?.basicNack(envelope.deliveryTag, false, false)
                        }
                    }
                }
            }

            consumerChannel?.basicConsume(summaryQueueName, false, consumer)
            logger.info { "Started summary consumer on queue: $summaryQueueName" }

            // Keep thread alive
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            logger.error(e) { "Summary consumer thread error" }
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 12: Create SummaryService

**Files:**
- Create: `src/main/kotlin/ru/kingofraccoons/services/SummaryService.kt`

- [ ] **Step 1: Write the service**

```kotlin
package ru.kingofraccoons.services

import mu.KotlinLogging
import ru.kingofraccoons.dao.ProcessingStatusDAO
import ru.kingofraccoons.dao.SummaryDAO
import ru.kingofraccoons.dao.TranscriptionDAO

private val logger = KotlinLogging.logger {}

class SummaryService(
    private val processingStatusDAO: ProcessingStatusDAO,
    private val summaryDAO: SummaryDAO,
    private val transcriptionDAO: TranscriptionDAO,
    private val openRouterService: OpenRouterService
) {
    suspend fun summarize(recordId: Long) {
        logger.info { "Starting summarization for record ID: $recordId" }

        processingStatusDAO.createOrUpdate(recordId, "summarization", "in_progress")

        try {
            val segments = transcriptionDAO.findByRecordId(recordId)
            val transcript = segments.joinToString(" ") { it.text.trim() }

            if (transcript.isBlank()) {
                logger.warn { "Empty transcript for record ID: $recordId" }
                processingStatusDAO.update(recordId, "summarization", "failed", "Empty transcription")
                return
            }

            val result = openRouterService.summarize(transcript)

            summaryDAO.createOrUpdate(
                recordId = recordId,
                summaryText = result.text,
                modelUsed = result.modelUsed,
                promptTokens = result.promptTokens,
                completionTokens = result.completionTokens
            )

            processingStatusDAO.update(recordId, "summarization", "completed")
            logger.info { "Summarization completed for record ID: $recordId" }
        } catch (e: Exception) {
            logger.error(e) { "Summarization failed for record ID: $recordId" }
            processingStatusDAO.update(recordId, "summarization", "failed", e.message)
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 13: Modify RecordRoutes for summarization

**Files:**
- Modify: `src/main/kotlin/ru/kingofraccoons/routes/RecordRoutes.kt`

- [ ] **Step 1: Update function signature**

Change:
```kotlin
fun Route.recordRoutes(
    recordDAO: RecordDAO,
    transcriptionDAO: TranscriptionDAO,
    folderDAO: FolderDAO,
    s3Service: S3Service,
    pdfService: PdfService,
    rabbitMQService: RabbitMQService,
    apiKey: String
)
```

To:
```kotlin
fun Route.recordRoutes(
    recordDAO: RecordDAO,
    transcriptionDAO: TranscriptionDAO,
    folderDAO: FolderDAO,
    s3Service: S3Service,
    pdfService: PdfService,
    rabbitMQService: RabbitMQService,
    processingStatusDAO: ProcessingStatusDAO,
    summaryDAO: SummaryDAO,
    apiKey: String
)
```

- [ ] **Step 2: Modify POST /records/{id}/transcribe to trigger summarization**

After the line:
```kotlin
recordDAO.updateDescription(recordId, fullText)
```

Add:
```kotlin
// Update transcription status and trigger summarization
processingStatusDAO.createOrUpdate(recordId, "transcription", "completed")
try {
    rabbitMQService.sendSummaryTask(recordId)
    processingStatusDAO.createOrUpdate(recordId, "summarization", "pending")
} catch (e: Exception) {
    logger.warn(e) { "Failed to enqueue summary task for record $recordId" }
    processingStatusDAO.createOrUpdate(recordId, "summarization", "failed", e.message)
}
```

- [ ] **Step 3: Modify GET /records/{id} to include summary and statuses**

Replace the body of `get("/records/{id}")` with:
```kotlin
val principal = call.principal<JWTPrincipal>()
    ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

val keycloakUserId = principal.payload.subject
val recordId = call.parameters["id"]?.toLongOrNull()
if (recordId == null) {
    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID", 400))
    return@get
}

val record = recordDAO.findById(recordId)
if (record == null) {
    call.respond(HttpStatusCode.NotFound, ErrorResponse("Record not found", 404))
    return@get
}

if (!record.belongsTo(keycloakUserId, folderDAO)) {
    call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't have access to this record", 403))
    return@get
}

val summary = summaryDAO.findByRecordId(recordId)
val statuses = processingStatusDAO.findByRecordId(recordId)

val response = RecordWithSummaryResponse(
    id = record.id,
    folderId = record.folderId,
    title = record.title,
    description = record.description,
    datetime = record.datetime,
    latitude = record.latitude,
    longitude = record.longitude,
    duration = record.duration,
    category = record.category,
    audioUrl = record.audioUrl,
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
    summary = summary?.let {
        SummaryResponse(
            summaryText = it.summaryText,
            modelUsed = it.modelUsed,
            createdAt = it.createdAt
        )
    },
    statuses = statuses.map {
        ProcessingStatusResponse(
            stage = it.stage,
            status = it.status,
            errorMessage = it.errorMessage,
            updatedAt = it.updatedAt
        )
    }
)

call.respond(HttpStatusCode.OK, response)
```

- [ ] **Step 4: Add GET /records/{id}/summary endpoint**

Add inside `authenticate("auth-jwt")` block:
```kotlin
apiDoc("GET", "/records/{id}/summary") {
    summary = "Получить суммаризацию записи"
    description = "Возвращает сгенерированную суммаризацию транскрипции"
    tags = listOf("Records")
    parameter("Authorization", "Bearer {token}", required = true, location = ParameterLocation.HEADER)
    parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
    response(HttpStatusCode.OK, "Суммаризация")
    response(HttpStatusCode.NotFound, "Суммаризация не готова")
    response(HttpStatusCode.Forbidden, "Нет доступа")
}

get("/records/{id}/summary") {
    val principal = call.principal<JWTPrincipal>()
        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

    val keycloakUserId = principal.payload.subject
    val recordId = call.parameters["id"]?.toLongOrNull()
    if (recordId == null) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID", 400))
        return@get
    }

    val record = recordDAO.findById(recordId)
    if (record == null) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Record not found", 404))
        return@get
    }

    if (!record.belongsTo(keycloakUserId, folderDAO)) {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't have access to this record", 403))
        return@get
    }

    val summary = summaryDAO.findByRecordId(recordId)
    if (summary == null) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Summary not available", 404))
        return@get
    }

    call.respond(HttpStatusCode.OK, SummaryResponse(
        summaryText = summary.summaryText,
        modelUsed = summary.modelUsed,
        createdAt = summary.createdAt
    ))
}
```

- [ ] **Step 5: Add GET /records/{id}/statuses endpoint**

Add inside `authenticate("auth-jwt")` block:
```kotlin
apiDoc("GET", "/records/{id}/statuses") {
    summary = "Получить статусы обработки записи"
    description = "Возвращает статусы всех этапов обработки (транскрипция, суммаризация)"
    tags = listOf("Records")
    parameter("Authorization", "Bearer {token}", required = true, location = ParameterLocation.HEADER)
    parameter("id", "ID записи", required = true, type = "integer", location = ParameterLocation.PATH)
    response(HttpStatusCode.OK, "Список статусов")
    response(HttpStatusCode.Forbidden, "Нет доступа")
}

get("/records/{id}/statuses") {
    val principal = call.principal<JWTPrincipal>()
        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token", 401))

    val keycloakUserId = principal.payload.subject
    val recordId = call.parameters["id"]?.toLongOrNull()
    if (recordId == null) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID", 400))
        return@get
    }

    val record = recordDAO.findById(recordId)
    if (record == null) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Record not found", 404))
        return@get
    }

    if (!record.belongsTo(keycloakUserId, folderDAO)) {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("You don't have access to this record", 403))
        return@get
    }

    val statuses = processingStatusDAO.findByRecordId(recordId)
    call.respond(HttpStatusCode.OK, statuses.map {
        ProcessingStatusResponse(
            stage = it.stage,
            status = it.status,
            errorMessage = it.errorMessage,
            updatedAt = it.updatedAt
        )
    })
}
```

- [ ] **Step 6: Add imports and verify compilation**

Ensure these imports are present at the top of `RecordRoutes.kt`:
```kotlin
import ru.kingofraccoons.dao.ProcessingStatusDAO
import ru.kingofraccoons.dao.SummaryDAO
import ru.kingofraccoons.models.*
```

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 14: Wire everything in Application.kt

**Files:**
- Modify: `src/main/kotlin/ru/kingofraccoons/Application.kt`

- [ ] **Step 1: Instantiate new DAOs and services**

After `val sharedRecordDAO = SharedRecordDAO()`, add:
```kotlin
val processingStatusDAO = ProcessingStatusDAO()
val summaryDAO = SummaryDAO()
val openRouterService = OpenRouterService(this)
val summaryService = SummaryService(processingStatusDAO, summaryDAO, transcriptionDAO, openRouterService)
```

- [ ] **Step 2: Pass new DAOs to recordRoutes**

Change:
```kotlin
recordRoutes(recordDAO, transcriptionDAO, folderDAO, s3Service, pdfService, rabbitMQService, apiKey)
```

To:
```kotlin
recordRoutes(recordDAO, transcriptionDAO, folderDAO, s3Service, pdfService, rabbitMQService, processingStatusDAO, summaryDAO, apiKey)
```

- [ ] **Step 3: Launch summary consumer coroutine**

After the routing block, add:
```kotlin
// Launch summary consumer
launch {
    rabbitMQService.startSummaryConsumer { recordId ->
        summaryService.summarize(recordId)
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 15: Add configuration to application.yaml

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Add openrouter and summaryQueue config**

Append to the end of the file:
```yaml
openrouter:
  apiKey: ${OPENROUTER_API_KEY:}
  model: ${OPENROUTER_MODEL:google/gemini-2.0-flash-exp:free}
  baseUrl: ${OPENROUTER_BASE_URL:https://openrouter.ai/api/v1}
  maxRetries: ${OPENROUTER_MAX_RETRIES:3}
  retryDelayMs: ${OPENROUTER_RETRY_DELAY_MS:5000}

rabbitmq:
  host: ${RABBITMQ_HOST:localhost}
  port: ${RABBITMQ_PORT:5672}
  username: ${RABBITMQ_USER:rmuser}
  password: ${RABBITMQ_PASSWORD:rmpassword}
  queue: ${RABBITMQ_QUEUE:audio-transcription}
  summaryQueue: ${RABBITMQ_SUMMARY_QUEUE:summary-tasks}
```

Note: This duplicates the existing `rabbitmq` block. Merge them so there is only one `rabbitmq:` section with both `queue` and `summaryQueue`.

- [ ] **Step 2: Verify application starts**

Run: `./gradlew run` or start via docker-compose
Expected: Application starts without config errors

---

### Task 16: Run all tests

**Files:**
- None (verification step)

- [ ] **Step 1: Run DAO tests**

Run: `./gradlew test --tests "ru.kingofraccoons.tests.dao.ProcessingStatusDaoTest" --tests "ru.kingofraccoons.tests.dao.SummaryDaoTest"`
Expected: All tests PASS

- [ ] **Step 2: Run service tests**

Run: `./gradlew test --tests "ru.kingofraccoons.tests.services.OpenRouterServiceTest"`
Expected: All tests PASS

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, no regressions

---

## Self-Review

### Spec Coverage Check

| Spec Requirement | Implementing Task |
|------------------|-------------------|
| ProcessingStatuses table | Task 1 |
| Summaries table | Task 2 |
| ProcessingStatusDAO | Task 5 |
| SummaryDAO | Task 7 |
| OpenRouterService with retry | Task 9 |
| RabbitMQ summary queue + consumer | Task 11 |
| SummaryService business logic | Task 12 |
| GET /records/{id} with summary + statuses | Task 13 |
| GET /records/{id}/summary | Task 13 |
| GET /records/{id}/statuses | Task 13 |
| POST /records/{id}/transcribe triggers summarization | Task 13 |
| Config via application.yaml | Task 15 |
| DAO tests | Tasks 6, 8 |
| OpenRouterService test | Task 10 |

No gaps identified.

### Placeholder Scan

No TBD, TODO, "implement later", or "add appropriate error handling" found. All code blocks are complete.

### Type Consistency

- `ProcessingStatusDAO.createOrUpdate` returns `ProcessingStatus?` consistently
- `SummaryDAO.createOrUpdate` returns `Summary?` consistently
- `OpenRouterService.summarize` returns `SummaryResult` consistently
- DTO names match between spec and plan: `SummaryResponse`, `ProcessingStatusResponse`

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-27-transcription-summarization.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

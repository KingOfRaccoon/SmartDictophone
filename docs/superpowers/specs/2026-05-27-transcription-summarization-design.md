# Design: Summarization of Transcriptions via OpenRouter

## Overview

Add automatic summarization of transcriptions using OpenRouter's free-tier models. Summarization triggers after transcription completes, with explicit processing statuses for better UX.

## Goals

- Automatically generate a concise summary after transcription finishes
- Expose processing statuses so the frontend can show progress indicators
- Store summary metadata (model used, token counts) for observability
- Allow configurable model selection without code changes
- Handle failures gracefully with clear status reporting

## Non-Goals

- Manual trigger for re-summarization (out of scope; can be added later)
- Multiple summaries per record (1:1 for now)
- Streaming / real-time summary generation
- Support for non-Russian transcripts (handled by model capability, not explicit logic)

## Data Model

### ProcessingStatuses

Tracks the status of each processing stage per record.

```kotlin
object ProcessingStatuses : LongIdTable("processing_statuses") {
    val recordId = reference("record_id", Records)
    val stage = varchar("stage", 50)      // "transcription", "summarization"
    val status = varchar("status", 20)    // "pending", "in_progress", "completed", "failed"
    val errorMessage = text("error_message").nullable()
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    init {
        uniqueIndex("processing_statuses_record_stage_idx", recordId, stage)
    }
}
```

### Summaries

Stores the generated summary and metadata.

```kotlin
object Summaries : LongIdTable("summaries") {
    val recordId = reference("record_id", Records).uniqueIndex()
    val summaryText = text("summary_text")
    val modelUsed = varchar("model_used", 100)
    val promptTokens = integer("prompt_tokens").nullable()
    val completionTokens = integer("completion_tokens").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
}
```

### DTOs

```kotlin
@Serializable
data class SummaryResponse(
    val summaryText: String,
    val modelUsed: String,
    val createdAt: String
)

@Serializable
data class ProcessingStatusResponse(
    val stage: String,
    val status: String,
    val errorMessage: String?,
    val updatedAt: String
)
```

## Architecture

### Components

| Component | Responsibility |
|-----------|----------------|
| `OpenRouterService` | HTTP client for OpenRouter API: builds request, parses response, handles retries |
| `SummaryService` | Business logic: fetches transcript, builds prompt, calls OpenRouter, persists result |
| `ProcessingStatusDAO` | CRUD for `ProcessingStatuses` |
| `SummaryDAO` | CRUD for `Summaries` |
| `RabbitMQService` | Publish summary tasks + run in-process consumer for `summary_queue` |

### Data Flow

```
[User creates record]
  → POST /records
  → audio → S3
  → transcription_task → RabbitMQ (audio-transcription queue)
  → ProcessingStatus(recordId, "transcription", "pending")

[ML service]
  → Consumes from audio-transcription queue
  → Transcribes audio
  → POST /records/{id}/transcribe
  → Saves segments + full text to description
  → ProcessingStatus(recordId, "transcription", "completed")
  → summary_task → RabbitMQ (summary queue)
  → ProcessingStatus(recordId, "summarization", "pending")

[Ktor consumer]
  → Consumes from summary queue
  → ProcessingStatus(recordId, "summarization", "in_progress")
  → SummaryService fetches transcript, calls OpenRouter
  → Saves result to Summaries
  → ProcessingStatus(recordId, "summarization", "completed")

[User views record]
  → GET /records/{id}
  → Returns record with embedded summary + statuses
```

### Prompt

```text
Проанализируй следующую транскрипцию аудиозаписи и создай краткое содержание (суммаризацию) на русском языке. Выдели основные темы, ключевые выводы и действия.

Транскрипция:
{transcript}
```

## API Changes

### New Endpoints

#### `GET /records/{id}/summary`

Returns the generated summary for a record.

- **200 OK** — `SummaryResponse`
- **404 Not Found** — summary not ready or record does not exist
- **403 Forbidden** — no access to the record

#### `GET /records/{id}/statuses`

Returns processing statuses for a record.

- **200 OK** — `List<ProcessingStatusResponse>`
- **403 Forbidden** — no access to the record

### Modified Endpoints

#### `GET /records/{id}`

Response now includes embedded fields:

```json
{
  "id": 1,
  "title": "Meeting",
  ...,
  "summary": {
    "summaryText": "...",
    "modelUsed": "google/gemini-2.0-flash-exp:free",
    "createdAt": "2026-05-27T10:00:00"
  },
  "statuses": [
    { "stage": "transcription", "status": "completed", ... },
    { "stage": "summarization", "status": "in_progress", ... }
  ]
}
```

If summary is not yet ready, `summary` is `null`.

#### `POST /records/{id}/transcribe` (ML endpoint)

After successfully saving transcription segments:

1. `ProcessingStatusDAO.update(recordId, "transcription", "completed")`
2. `RabbitMQService.sendSummaryTask(recordId)`
3. `ProcessingStatusDAO.create(recordId, "summarization", "pending")`

## Configuration

New section in `application.yaml`:

```yaml
openrouter:
  apiKey: ${OPENROUTER_API_KEY:}
  model: ${OPENROUTER_MODEL:google/gemini-2.0-flash-exp:free}
  baseUrl: ${OPENROUTER_BASE_URL:https://openrouter.ai/api/v1}
  maxRetries: ${OPENROUTER_MAX_RETRIES:3}
  retryDelayMs: ${OPENROUTER_RETRY_DELAY_MS:5000}

rabbitmq:
  # ...existing config
  summaryQueue: ${RABBITMQ_SUMMARY_QUEUE:summary-tasks}
```

## Error Handling & Retry

### OpenRouter Call Retry

```kotlin
suspend fun <T> withRetry(
    maxRetries: Int,
    delayMs: Long,
    block: suspend () -> T
): T
```

Retries on network errors and 5xx responses. Fails fast on 4xx (except 429, which retries).

### RabbitMQ Consumer Ack Policy

| Scenario | Action | Status |
|----------|--------|--------|
| Success | `basicAck` | `completed` |
| Empty transcript | `basicAck` | `failed` + message |
| OpenRouter fails after max retries | `basicAck` | `failed` + message |
| Unexpected exception | `basicNack(requeue=false)` | `failed` + message |

## Implementation Notes

### Consumer Lifecycle

The RabbitMQ consumer runs inside a Ktor-launched coroutine:

```kotlin
// In Application.module()
launch {
    rabbitMQService.startSummaryConsumer { recordId ->
        summaryService.summarize(recordId)
    }
}
```

The consumer loops on `channel.basicConsume`, acknowledges messages explicitly, and survives under normal load because the work is IO-bound (HTTP call to OpenRouter).

### Database Migrations

New tables (`ProcessingStatuses`, `Summaries`) are created automatically via `SchemaUtils.createMissingTablesAndColumns` in `DatabaseFactory.init`. For production, a SQL migration should be prepared separately.

### OpenRouter Request Format

```json
{
  "model": "google/gemini-2.0-flash-exp:free",
  "messages": [
    { "role": "system", "content": "You are a helpful assistant that summarizes transcripts concisely in Russian." },
    { "role": "user", "content": "<prompt with transcript>" }
  ]
}
```

## Testing Strategy

### DAO Tests (Testcontainers)

- `ProcessingStatusDaoTest` — create, update, findByRecordId, unique constraint on (recordId, stage)
- `SummaryDaoTest` — create, findByRecordId, idempotency (second create updates existing)

### Service Tests (Mock HTTP)

- `OpenRouterServiceTest` — using Ktor `MockEngine`: verify request JSON shape, parse response, retry on 5xx, fail on 4xx
- `SummaryServiceTest` — mock `OpenRouterService`: verify end-to-end flow from transcript text to persisted summary

### Integration Test

1. Create record via API
2. Save transcription via ML endpoint
3. Verify `ProcessingStatus` shows `summarization/pending`
4. Trigger `SummaryService.summarize(recordId)`
5. Verify `GET /records/{id}` returns non-null `summary` and `completed` status

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| OpenRouter free tier rate limits or downtime | Configurable model + retry with delay; statuses show `failed` clearly |
| Long transcript exceeds model context window | Truncate transcript to fit within model limits (e.g., 100k tokens for Gemini Flash) |
| Consumer crash loses in-flight message | `autoAck=false` + explicit `basicAck` only after DB commit |
| Summary generation blocks API | Runs in separate coroutine; only DB write happens on IO dispatcher |

## Future Extensions

- Re-summarize endpoint (e.g., with custom prompt)
- Multiple summary styles (brief, detailed, bullet points)
- Translation of summary to other languages
- Sentiment analysis as another processing stage

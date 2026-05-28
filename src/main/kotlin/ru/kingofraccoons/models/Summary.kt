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

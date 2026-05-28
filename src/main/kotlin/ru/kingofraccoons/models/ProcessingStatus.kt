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

package ru.kingofraccoons.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// Database Tables
// Users table removed - using Keycloak user IDs directly

object Folders : LongIdTable("folders") {
    val keycloakUserId = varchar("keycloak_user_id", 255).index() // Keycloak user ID
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val isDefault = bool("is_default").default(false) // Дефолтные папки
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
}

enum class RecordCategory {
    Work, Study, Personal
}

object Records : LongIdTable("records") {
    val folderId = reference("folder_id", Folders).nullable()
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val datetime = datetime("datetime")
    val latitude = float("latitude").nullable()
    val longitude = float("longitude").nullable()
    val duration = integer("duration") // seconds
    val category = enumerationByName<RecordCategory>("category", 20)
    val audioUrl = varchar("audio_url", 512)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
}

object TranscriptionSegments : LongIdTable("transcription_segments") {
    val recordId = reference("record_id", Records)
    val start = float("start")
    val end = float("end")
    val text = text("text")
}

// DTOs for API
// User model removed - using Keycloak user info directly

@Serializable
data class Folder(
    val id: Long,
    val keycloakUserId: String,
    val name: String,
    val description: String?,
    val isDefault: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class Record(
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
    val updatedAt: String
)

@Serializable
data class TranscriptionSegment(
    val id: Long,
    val recordId: Long,
    val start: Float,
    val end: Float,
    val text: String
)

// Request/Response DTOs
// Auth endpoints removed - using Keycloak directly

@Serializable
data class UserInfo(
    val keycloakUserId: String,
    val username: String,  // Оригинальный username пользователя
    val email: String?,
    val fullName: String?,
    val countRecords: Int,
    val countMinutes: Int
)

@Serializable
data class CreateFolderRequest(
    val name: String,
    val description: String?
)

@Serializable
data class UpdateFolderRequest(
    val name: String,
    val description: String?
)

@Serializable
data class TranscribeRequest(
    val segments: List<TranscriptionSegmentInput>
)

@Serializable
data class TranscriptionSegmentInput(
    val start: Float,
    val end: Float,
    val text: String
)

@Serializable
data class PaginatedResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int
)

@Serializable
data class ErrorResponse(
    val message: String,
    val status: Int
)

package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import mu.KotlinLogging
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.dao.TranscriptionDAO
import ru.kingofraccoons.models.ErrorResponse
import ru.kingofraccoons.models.PaginatedResponse
import ru.kingofraccoons.models.Record
import ru.kingofraccoons.models.RecordCategory
import ru.kingofraccoons.models.TranscribeRequest
import ru.kingofraccoons.services.PdfService
import ru.kingofraccoons.services.S3Service
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

fun Route.recordRoutes(
    recordDAO: RecordDAO,
    transcriptionDAO: TranscriptionDAO,
    folderDAO: FolderDAO,
    s3Service: S3Service,
    pdfService: PdfService,
    apiKey: String
) {
    authenticate("auth-jwt") {
        get("/records") {
            val userId = call.requireUserId() ?: return@get
            
            val search = call.request.queryParameters["search"]
            val folderId = call.request.queryParameters["folderId"]?.toLongOrNull()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            
            val (records, totalElements) = recordDAO.search(userId, search, folderId, page, size)
            val totalPages = ((totalElements + size - 1) / size).toInt()
            
            call.respond(
                HttpStatusCode.OK,
                PaginatedResponse(
                    content = records,
                    totalElements = totalElements,
                    totalPages = totalPages
                )
            )
        }
        
        post("/records") {
            val userId = call.requireUserId() ?: return@post
            
            val multipart = call.receiveMultipart()
            var datetime: LocalDateTime? = null
            var place: String? = null
            var recordBytes: ByteArray? = null
            var recordFileName: String? = null
            var name: String? = null
            var folderId: Long? = null
            var category: RecordCategory? = null
            var latitude: Float? = null
            var longitude: Float? = null
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        when (part.name) {
                            "datetime" -> datetime = runCatching {
                                LocalDateTime.parse(part.value, DateTimeFormatter.ISO_DATE_TIME)
                            }.getOrNull()
                            "place" -> place = part.value
                            "name" -> name = part.value
                            "folderId" -> folderId = part.value.toLongOrNull()
                            "category" -> category = RecordCategory.entries
                                .firstOrNull { it.name.equals(part.value, ignoreCase = true) }
                        }
                    }
                    is PartData.FileItem -> {
                        if (part.name == "recordFile") {
                            val channel = part.provider()
                            @Suppress("DEPRECATION") // Ktor 3 still exposes only the deprecated helper for full channel reads.
                            val bytes = channel.readRemaining().readBytes()
                            recordBytes = bytes
                            recordFileName = part.originalFileName ?: "recording.m4a"
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }
            
            // Validation
            if (datetime == null || name.isNullOrBlank() || category == null || recordBytes == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Required fields: datetime, name, category, recordFile", 400)
                )
                return@post
            }

            val resolvedFolderId = folderId
            if (resolvedFolderId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Folder ID is required", HttpStatusCode.BadRequest.value)
                )
                return@post
            }

            val folder = folderDAO.findById(resolvedFolderId)
            if (folder == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Folder not found", HttpStatusCode.NotFound.value)
                )
                return@post
            }

            if (folder.userId != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("You don't have access to this folder", HttpStatusCode.Forbidden.value)
                )
                return@post
            }
            
            // Parse location if provided
            place?.let {
                val coordinates = it.split(",")
                if (coordinates.size == 2) {
                    latitude = coordinates[0].trim().toFloatOrNull()
                    longitude = coordinates[1].trim().toFloatOrNull()
                }
            }
            
            // Upload file to S3
            try {
                val bytes = recordBytes!!
                val fileName = recordFileName ?: "recording.m4a"

                val audioUrl = s3Service.uploadFile(
                    ByteArrayInputStream(bytes),
                    fileName,
                    "audio/m4a"
                )
                
                // Create record
                val record = recordDAO.create(
                    folderId = resolvedFolderId,
                    title = name!!,
                    description = null,
                    datetime = datetime!!,
                    latitude = latitude,
                    longitude = longitude,
                    duration = 0, // Will be updated after processing
                    category = category!!,
                    audioUrl = audioUrl
                )
                
                if (record == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Failed to create record", 400)
                    )
                    return@post
                }

                // TODO: здесь идет коннект к RabbitMQ  и он отправляет в очередь `audio-transcription` в формате {'id': id_record}
                
                call.respond(HttpStatusCode.Created, record)
            } catch (e: Exception) {
                logger.error(e) { "Failed to upload audio file" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Failed to upload audio file: ${e.message}", 400)
                )
            }
        }
        
        get("/records/{id}/audio") {
            val userId = call.requireUserId() ?: return@get
            
            val recordId = call.parameters["id"]?.toLongOrNull()
            if (recordId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid record ID", 400)
                )
                return@get
            }
            
            val record = recordDAO.findById(recordId)
            if (record == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Record not found", 404)
                )
                return@get
            }

            if (!recordBelongsToUser(record, userId, folderDAO)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("You don't have access to this record", HttpStatusCode.Forbidden.value)
                )
                return@get
            }
            
            // Download from S3
            val audioData = s3Service.downloadFile(record.audioUrl)
            if (audioData == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Audio file not found", 404)
                )
                return@get
            }
            
            call.respondBytes(
                audioData,
                ContentType.Audio.MP4,
                HttpStatusCode.OK
            )
        }
        
        get("/records/{id}/pdf") {
            val userId = call.requireUserId() ?: return@get
            
            val recordId = call.parameters["id"]?.toLongOrNull()
            if (recordId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid record ID", 400)
                )
                return@get
            }
            
            val record = recordDAO.findById(recordId)
            if (record == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Record not found", 404)
                )
                return@get
            }

            if (!recordBelongsToUser(record, userId, folderDAO)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("You don't have access to this record", HttpStatusCode.Forbidden.value)
                )
                return@get
            }
            
            val segments = transcriptionDAO.findByRecordId(recordId)
            if (segments.isEmpty()) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("No transcription available", 404)
                )
                return@get
            }
            
            try {
                val pdfData = pdfService.generateTranscriptionPdf(
                    record.title,
                    record.datetime,
                    segments
                )
                
                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "${record.title}.pdf"
                    ).toString()
                )
                call.respondBytes(
                    pdfData,
                    ContentType.Application.Pdf,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate PDF" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to generate PDF", 500)
                )
            }
        }
    }
    
    // API Key protected endpoint
    post("/records/{id}/transcribe") {
        val providedApiKey = call.request.headers["X-API-Key"]
        
        if (providedApiKey != apiKey) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("Invalid API key", 401)
            )
            return@post
        }

        val recordId = call.parameters["id"]?.toLongOrNull()
        if (recordId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid record ID", 400)
            )
            return@post
        }

        val record = recordDAO.findById(recordId)
        if (record == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Record not found", 404)
            )
            return@post
        }

        val request = call.receiveOrBadRequest<TranscribeRequest>() ?: return@post
        val segments = request.segments

        if (segments.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Segments are required", 400)
            )
            return@post
        }

        // Save transcription segments
        transcriptionDAO.createBatch(recordId, segments)

        call.respond(HttpStatusCode.OK, mapOf("message" to "Transcription saved successfully"))
    }
}

/**
 * Verifies that the record resides in a folder owned by the current user.
 * Records without a folder are treated as inaccessible for safety reasons.
 */
private suspend fun recordBelongsToUser(
    record: Record,
    userId: Long,
    folderDAO: FolderDAO
): Boolean {
    val folderId = record.folderId ?: return false
    val folder = folderDAO.findById(folderId) ?: return false
    return folder.userId == userId
}

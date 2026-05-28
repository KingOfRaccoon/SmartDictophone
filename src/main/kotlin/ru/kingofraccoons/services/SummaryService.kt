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

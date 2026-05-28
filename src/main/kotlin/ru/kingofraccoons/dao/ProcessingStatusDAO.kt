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
        ProcessingStatuses.selectAll()
            .where { (ProcessingStatuses.recordId eq recordId) and (ProcessingStatuses.stage eq stage) }
            .map(::resultRowToProcessingStatus)
            .singleOrNull()
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

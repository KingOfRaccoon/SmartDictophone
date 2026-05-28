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
        Summaries.selectAll().where { Summaries.recordId eq recordId }
            .map(::resultRowToSummary)
            .singleOrNull()
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

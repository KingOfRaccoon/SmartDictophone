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

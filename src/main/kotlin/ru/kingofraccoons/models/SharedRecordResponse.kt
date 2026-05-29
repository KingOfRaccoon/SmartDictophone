package ru.kingofraccoons.models

import kotlinx.serialization.Serializable

@Serializable
data class SharedRecordResponse(
    val record: Record,
    val role: String,
    val sharedByUserId: String,
    val sharedAt: String
)

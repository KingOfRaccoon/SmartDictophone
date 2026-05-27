package ru.kingofraccoons.tests.infrastructure

import ru.kingofraccoons.models.RecordCategory
import ru.kingofraccoons.models.TranscriptionSegmentInput
import java.time.LocalDateTime

object TestData {
    const val USER_ID_1 = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    const val USER_ID_2 = "b2c3d4e5-f6a7-8901-bcde-f12345678901"
    const val USER_ID_3 = "c3d4e5f6-a7b8-9012-cdef-123456789012"

    const val FOLDER_NAME = "Test Folder"
    const val FOLDER_DESCRIPTION = "Test folder description"

    const val RECORD_TITLE = "Test Recording"
    const val RECORD_DESCRIPTION = "A test recording description"
    const val RECORD_DURATION = 120
    const val RECORD_AUDIO_URL = "s3://test-bucket/1.m4a"
    val RECORD_DATETIME = LocalDateTime.of(2025, 1, 15, 10, 30, 0)
    val RECORD_CATEGORY = RecordCategory.Work
    const val RECORD_LATITUDE = 55.7558f
    const val RECORD_LONGITUDE = 37.6173f

    fun segmentInputs(count: Int): List<TranscriptionSegmentInput> =
        (1..count).map { i ->
            TranscriptionSegmentInput(
                start = (i - 1) * 5.0f,
                end = i * 5.0f,
                text = "Segment $i text content"
            )
        }
}

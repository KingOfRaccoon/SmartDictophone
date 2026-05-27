package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import ru.kingofraccoons.tests.infrastructure.TestData

class TranscriptionDaoTest : BaseDaoTest() {

    private suspend fun createTestRecord() =
        folderDAO.create("user-1", "Folder", null)!!.let { folder ->
            recordDAO.create(
                folder.id, "Record", null,
                java.time.LocalDateTime.of(2025, 1, 15, 10, 0),
                null, null, 60,
                ru.kingofraccoons.models.RecordCategory.Work,
                "s3://test/1.m4a"
            )!!
        }

    @Test
    fun `createBatch - creates multiple segments`() = runBlocking {
        val record = createTestRecord()
        val segments = TestData.segmentInputs(3)

        val created = transcriptionDAO.createBatch(record.id, segments)

        assertEquals(3, created.size)
        created.forEach { seg ->
            assertEquals(record.id, seg.recordId)
        }
        assertEquals("Segment 1 text content", created[0].text)
        assertEquals(0.0f, created[0].start)
        assertEquals(5.0f, created[0].end)
    }

    @Test
    fun `createBatch - handles empty input`() = runBlocking {
        val record = createTestRecord()

        val created = transcriptionDAO.createBatch(record.id, emptyList())

        assertTrue(created.isEmpty())
    }

    @Test
    fun `findByRecordId - returns segments ordered by start`() = runBlocking {
        val record = createTestRecord()
        transcriptionDAO.createBatch(record.id, TestData.segmentInputs(3))

        val segments = transcriptionDAO.findByRecordId(record.id)

        assertEquals(3, segments.size)
        for (i in 0 until segments.size - 1) {
            assertTrue(segments[i].start <= segments[i + 1].start)
        }
    }

    @Test
    fun `findByRecordId - returns empty for record with no segments`() = runBlocking {
        val record = createTestRecord()

        assertTrue(transcriptionDAO.findByRecordId(record.id).isEmpty())
    }

    @Test
    fun `deleteByRecordId - deletes all segments and returns count`() = runBlocking {
        val record = createTestRecord()
        transcriptionDAO.createBatch(record.id, TestData.segmentInputs(3))

        val deleted = transcriptionDAO.deleteByRecordId(record.id)

        assertEquals(3, deleted)
        assertTrue(transcriptionDAO.findByRecordId(record.id).isEmpty())
    }

    @Test
    fun `deleteByRecordId - returns 0 for record with no segments`() = runBlocking {
        val record = createTestRecord()

        assertEquals(0, transcriptionDAO.deleteByRecordId(record.id))
    }
}

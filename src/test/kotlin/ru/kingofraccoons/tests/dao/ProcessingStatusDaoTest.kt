package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.dao.ProcessingStatusDAO
import ru.kingofraccoons.models.RecordCategory
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import java.time.LocalDateTime

class ProcessingStatusDaoTest : BaseDaoTest() {
    private val dao = ProcessingStatusDAO()

    private suspend fun createTestRecord(): ru.kingofraccoons.models.Record {
        val folder = folderDAO.create("test-user", "Test Folder", null)
            ?: throw IllegalStateException("Failed to create folder")
        return recordDAO.create(
            folderId = folder.id,
            title = "Test Record",
            description = null,
            datetime = LocalDateTime.now(),
            latitude = null,
            longitude = null,
            duration = 60,
            category = RecordCategory.Work,
            audioUrl = "http://test/audio.m4a"
        ) ?: throw IllegalStateException("Failed to create record")
    }

    @Test
    fun `createOrUpdate should create new status`() = runTest {
        val record = createTestRecord()
        val status = dao.createOrUpdate(record.id, "summarization", "pending")

        assertNotNull(status)
        assertEquals("summarization", status!!.stage)
        assertEquals("pending", status.status)
    }

    @Test
    fun `createOrUpdate should update existing status`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "summarization", "pending")
        val updated = dao.createOrUpdate(record.id, "summarization", "completed")

        assertNotNull(updated)
        assertEquals("completed", updated!!.status)
    }

    @Test
    fun `findByRecordId should return all statuses for record`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "transcription", "completed")
        dao.createOrUpdate(record.id, "summarization", "pending")

        val statuses = dao.findByRecordId(record.id)
        assertEquals(2, statuses.size)
    }
}

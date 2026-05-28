package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.dao.SummaryDAO
import ru.kingofraccoons.models.RecordCategory
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import java.time.LocalDateTime

class SummaryDaoTest : BaseDaoTest() {
    private val dao = SummaryDAO()

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
    fun `createOrUpdate should create summary`() = runTest {
        val record = createTestRecord()
        val summary = dao.createOrUpdate(record.id, "Test summary", "test-model", 100, 50)

        assertNotNull(summary)
        assertEquals("Test summary", summary!!.summaryText)
        assertEquals("test-model", summary.modelUsed)
        assertEquals(100, summary.promptTokens)
        assertEquals(50, summary.completionTokens)
    }

    @Test
    fun `createOrUpdate should update existing summary`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "First", "model-1", 100, 50)
        val updated = dao.createOrUpdate(record.id, "Second", "model-2", 200, 100)

        assertNotNull(updated)
        assertEquals("Second", updated!!.summaryText)
        assertEquals("model-2", updated.modelUsed)
    }

    @Test
    fun `findByRecordId should return summary`() = runTest {
        val record = createTestRecord()
        dao.createOrUpdate(record.id, "Test", "model", null, null)

        val found = dao.findByRecordId(record.id)
        assertNotNull(found)
        assertEquals("Test", found!!.summaryText)
    }
}

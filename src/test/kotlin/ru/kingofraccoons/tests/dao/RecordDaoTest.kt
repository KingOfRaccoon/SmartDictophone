package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.models.RecordCategory
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import ru.kingofraccoons.tests.infrastructure.TestData
import java.time.LocalDateTime

class RecordDaoTest : BaseDaoTest() {

    private suspend fun createTestFolder(
        userId: String = TestData.USER_ID_1,
        name: String = TestData.FOLDER_NAME
    ) = folderDAO.create(userId, name, TestData.FOLDER_DESCRIPTION)!!

    private suspend fun createTestRecord(
        folderId: Long? = null,
        title: String = TestData.RECORD_TITLE,
        category: RecordCategory = TestData.RECORD_CATEGORY
    ) = recordDAO.create(
        folderId = folderId,
        title = title,
        description = TestData.RECORD_DESCRIPTION,
        datetime = TestData.RECORD_DATETIME,
        latitude = TestData.RECORD_LATITUDE,
        longitude = TestData.RECORD_LONGITUDE,
        duration = TestData.RECORD_DURATION,
        category = category,
        audioUrl = TestData.RECORD_AUDIO_URL
    )!!

    @Test
    fun `create - creates record with all fields`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        assertTrue(record.id > 0)
        assertEquals(folder.id, record.folderId)
        assertEquals(TestData.RECORD_TITLE, record.title)
        assertEquals(TestData.RECORD_DESCRIPTION, record.description)
        assertEquals(TestData.RECORD_DURATION, record.duration)
        assertEquals(RecordCategory.Work.name, record.category)
        assertEquals(TestData.RECORD_AUDIO_URL, record.audioUrl)
        assertEquals(TestData.RECORD_LATITUDE, record.latitude)
        assertEquals(TestData.RECORD_LONGITUDE, record.longitude)
    }

    @Test
    fun `create - creates record with null folderId`() = runBlocking {
        val record = createTestRecord(folderId = null)

        assertNull(record.folderId)
    }

    @Test
    fun `findById - returns record by id`() = runBlocking {
        val folder = createTestFolder()
        val created = createTestRecord(folder.id)

        val found = recordDAO.findById(created.id)

        assertNotNull(found)
        assertEquals(created.title, found!!.title)
    }

    @Test
    fun `findById - returns null for non-existent id`() = runBlocking {
        assertNull(recordDAO.findById(99999L))
    }

    @Test
    fun `findByFolderId - returns records in specified folder`() = runBlocking {
        val folder1 = createTestFolder(name = "Folder 1")
        val folder2 = createTestFolder(name = "Folder 2")
        createTestRecord(folder1.id, title = "Rec 1")
        createTestRecord(folder1.id, title = "Rec 2")
        createTestRecord(folder2.id, title = "Rec 3")

        val records = recordDAO.findByFolderId(folder1.id)

        assertEquals(2, records.size)
        assertTrue(records.all { it.folderId == folder1.id })
    }

    @Test
    fun `findByFolderId - returns empty list for folder with no records`() = runBlocking {
        val folder = createTestFolder()

        assertTrue(recordDAO.findByFolderId(folder.id).isEmpty())
    }

    @Test
    fun `search - returns all user records when no filters`() = runBlocking {
        val folder = createTestFolder()
        createTestRecord(folder.id, title = "Rec 1")
        createTestRecord(folder.id, title = "Rec 2")
        createTestRecord(folder.id, title = "Rec 3")

        val (records, total) = recordDAO.search(TestData.USER_ID_1, null, null, null, 0, 10)

        assertEquals(3, records.size)
        assertEquals(3L, total)
    }

    @Test
    fun `search - filters by search text in title`() = runBlocking {
        val folder = createTestFolder()
        createTestRecord(folder.id, title = "Meeting Notes")
        createTestRecord(folder.id, title = "Shopping List")

        val (records, _) = recordDAO.search(TestData.USER_ID_1, "Meeting", null, null, 0, 10)

        assertEquals(1, records.size)
        assertEquals("Meeting Notes", records[0].title)
    }

    @Test
    fun `search - filters by search text in description`() = runBlocking {
        val folder = createTestFolder()
        recordDAO.create(folder.id, "Title A", "Project alpha discussion", TestData.RECORD_DATETIME, null, null, 60, RecordCategory.Work, "s3://a")!!
        recordDAO.create(folder.id, "Title B", "Project beta discussion", TestData.RECORD_DATETIME, null, null, 60, RecordCategory.Work, "s3://b")!!

        val (records, _) = recordDAO.search(TestData.USER_ID_1, "alpha", null, null, 0, 10)

        assertEquals(1, records.size)
        assertEquals("Title A", records[0].title)
    }

    @Test
    fun `search - filters by folderId`() = runBlocking {
        val folder1 = createTestFolder(name = "F1")
        val folder2 = createTestFolder(name = "F2")
        createTestRecord(folder1.id, title = "In F1")
        createTestRecord(folder2.id, title = "In F2")

        val (records, _) = recordDAO.search(TestData.USER_ID_1, null, folder1.id, null, 0, 10)

        assertEquals(1, records.size)
        assertEquals("In F1", records[0].title)
    }

    @Test
    fun `search - filters by category`() = runBlocking {
        val folder = createTestFolder()
        createTestRecord(folder.id, title = "Work Rec", category = RecordCategory.Work)
        createTestRecord(folder.id, title = "Personal Rec", category = RecordCategory.Personal)

        val (records, _) = recordDAO.search(TestData.USER_ID_1, null, null, RecordCategory.Work, 0, 10)

        assertEquals(1, records.size)
        assertEquals("Work Rec", records[0].title)
    }

    @Test
    fun `search - combines multiple filters`() = runBlocking {
        val folder = createTestFolder()
        createTestRecord(folder.id, title = "Work Meeting", category = RecordCategory.Work)
        createTestRecord(folder.id, title = "Study Meeting", category = RecordCategory.Study)

        val (records, _) = recordDAO.search(TestData.USER_ID_1, "Meeting", folder.id, RecordCategory.Work, 0, 10)

        assertEquals(1, records.size)
        assertEquals("Work Meeting", records[0].title)
    }

    @Test
    fun `search - paginates correctly`() = runBlocking {
        val folder = createTestFolder()
        repeat(5) { i ->
            createTestRecord(folder.id, title = "Rec $i")
        }

        val (page0, total0) = recordDAO.search(TestData.USER_ID_1, null, null, null, 0, 2)
        val (page1, _) = recordDAO.search(TestData.USER_ID_1, null, null, null, 1, 2)
        val (page2, _) = recordDAO.search(TestData.USER_ID_1, null, null, null, 2, 2)

        assertEquals(2, page0.size)
        assertEquals(5L, total0)
        assertEquals(2, page1.size)
        assertEquals(1, page2.size)
    }

    @Test
    fun `search - returns empty for page beyond results`() = runBlocking {
        val folder = createTestFolder()
        createTestRecord(folder.id)

        val (records, total) = recordDAO.search(TestData.USER_ID_1, null, null, null, 5, 10)

        assertTrue(records.isEmpty())
        assertEquals(1L, total)
    }

    @Test
    fun `search - returns empty for different user`() = runBlocking {
        val folder = createTestFolder()
        createTestRecord(folder.id)

        val (records, _) = recordDAO.search(TestData.USER_ID_2, null, null, null, 0, 10)

        assertTrue(records.isEmpty())
    }

    @Test
    fun `search - orders by datetime descending`() = runBlocking {
        val folder = createTestFolder()
        val older = recordDAO.create(folder.id, "Older", null, LocalDateTime.of(2025, 1, 1, 10, 0), null, null, 60, RecordCategory.Work, "s3://o")!!
        val newer = recordDAO.create(folder.id, "Newer", null, LocalDateTime.of(2025, 6, 1, 10, 0), null, null, 60, RecordCategory.Work, "s3://n")!!

        val (records, _) = recordDAO.search(TestData.USER_ID_1, null, null, null, 0, 10)

        assertEquals(newer.id, records[0].id)
        assertEquals(older.id, records[1].id)
    }

    @Test
    fun `updatePartial - updates only title`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        recordDAO.updatePartial(record.id, title = "New Title", description = null, category = null)
        val found = recordDAO.findById(record.id)!!

        assertEquals("New Title", found.title)
        assertEquals(TestData.RECORD_DESCRIPTION, found.description)
        assertEquals(RecordCategory.Work.name, found.category)
    }

    @Test
    fun `updatePartial - updates only description`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        recordDAO.updatePartial(record.id, title = null, description = "New Desc", category = null)
        val found = recordDAO.findById(record.id)!!

        assertEquals(TestData.RECORD_TITLE, found.title)
        assertEquals("New Desc", found.description)
    }

    @Test
    fun `updatePartial - updates only category`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        recordDAO.updatePartial(record.id, title = null, description = null, category = RecordCategory.Personal)
        val found = recordDAO.findById(record.id)!!

        assertEquals(RecordCategory.Personal.name, found.category)
        assertEquals(TestData.RECORD_TITLE, found.title)
    }

    @Test
    fun `updatePartial - updates multiple fields at once`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        recordDAO.updatePartial(record.id, title = "T", description = "D", category = RecordCategory.Study)
        val found = recordDAO.findById(record.id)!!

        assertEquals("T", found.title)
        assertEquals("D", found.description)
        assertEquals(RecordCategory.Study.name, found.category)
    }

    @Test
    fun `updatePartial - returns record unchanged when all params null`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        val updated = recordDAO.updatePartial(record.id, null, null, null)

        assertNotNull(updated)
        assertEquals(record.title, updated!!.title)
        assertEquals(record.description, updated.description)
    }

    @Test
    fun `updatePartial - returns null for non-existent id`() = runBlocking {
        assertNull(recordDAO.updatePartial(99999L, "Title", null, null))
    }

    @Test
    fun `countByKeycloakUserId - counts all user records across folders`() = runBlocking {
        val f1 = createTestFolder(name = "F1")
        val f2 = createTestFolder(name = "F2")
        createTestRecord(f1.id)
        createTestRecord(f1.id)
        createTestRecord(f2.id)

        assertEquals(3L, recordDAO.countByKeycloakUserId(TestData.USER_ID_1))
    }

    @Test
    fun `countByKeycloakUserId - returns 0 for user with no records`() = runBlocking {
        assertEquals(0L, recordDAO.countByKeycloakUserId(TestData.USER_ID_1))
    }

    @Test
    fun `sumDurationByKeycloakUserId - sums duration of all user records`() = runBlocking {
        val folder = createTestFolder()
        recordDAO.create(folder.id, "R1", null, TestData.RECORD_DATETIME, null, null, 120, RecordCategory.Work, "s3://1")!!
        recordDAO.create(folder.id, "R2", null, TestData.RECORD_DATETIME, null, null, 180, RecordCategory.Work, "s3://2")!!

        assertEquals(300L, recordDAO.sumDurationByKeycloakUserId(TestData.USER_ID_1))
    }

    @Test
    fun `sumDurationByKeycloakUserId - returns 0 for user with no records`() = runBlocking {
        assertEquals(0L, recordDAO.sumDurationByKeycloakUserId(TestData.USER_ID_1))
    }

    @Test
    fun `update - updates all fields`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        recordDAO.update(
            record.id, "Updated", "New Desc",
            LocalDateTime.of(2025, 6, 1, 12, 0),
            40.0f, -74.0f, 300, RecordCategory.Personal, "s3://updated"
        )
        val found = recordDAO.findById(record.id)!!

        assertEquals("Updated", found.title)
        assertEquals("New Desc", found.description)
        assertEquals(300, found.duration)
        assertEquals(RecordCategory.Personal.name, found.category)
        assertEquals(40.0f, found.latitude)
        assertEquals(-74.0f, found.longitude)
    }

    @Test
    fun `update - returns null for non-existent id`() = runBlocking {
        assertNull(recordDAO.update(99999L, "T", null, TestData.RECORD_DATETIME, null, null, 60, RecordCategory.Work, "s3://x"))
    }

    @Test
    fun `updateDescription - updates only description`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        recordDAO.updateDescription(record.id, "Transcribed text here")
        val found = recordDAO.findById(record.id)!!

        assertEquals("Transcribed text here", found.description)
        assertEquals(TestData.RECORD_TITLE, found.title)
    }

    @Test
    fun `delete - deletes record and returns true`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        assertTrue(recordDAO.delete(record.id))
        assertNull(recordDAO.findById(record.id))
    }

    @Test
    fun `delete - returns false for non-existent id`() = runBlocking {
        assertFalse(recordDAO.delete(99999L))
    }
}

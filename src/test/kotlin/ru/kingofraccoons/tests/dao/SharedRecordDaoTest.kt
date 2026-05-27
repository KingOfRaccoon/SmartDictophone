package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import ru.kingofraccoons.tests.infrastructure.TestData

class SharedRecordDaoTest : BaseDaoTest() {

    private suspend fun createTestFolder(userId: String = TestData.USER_ID_1) =
        folderDAO.create(userId, "Folder", null)!!

    private suspend fun createTestRecord(folderId: Long) =
        recordDAO.create(
            folderId, "Record", null,
            java.time.LocalDateTime.of(2025, 1, 15, 10, 0),
            null, null, 60,
            ru.kingofraccoons.models.RecordCategory.Work,
            "s3://test/1.m4a"
        )!!

    @Test
    fun `share - creates new shared record`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        val shared = sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")

        assertTrue(shared.id > 0)
        assertEquals(record.id, shared.recordId)
        assertEquals(TestData.USER_ID_1, shared.sharedByUserId)
        assertEquals(TestData.USER_ID_2, shared.sharedWithUserId)
        assertEquals("viewer", shared.role)
    }

    @Test
    fun `share - upserts when sharing again with same user`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")
        val updated = sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "editor")

        assertEquals("editor", updated.role)
        val all = sharedRecordDAO.findByRecordId(record.id)
        assertEquals(1, all.size)
    }

    @Test
    fun `share - allows sharing with multiple users`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")
        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_3, "editor")

        val all = sharedRecordDAO.findByRecordId(record.id)
        assertEquals(2, all.size)
    }

    @Test
    fun `findByRecordId - returns all shares for a record`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)
        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")

        val shares = sharedRecordDAO.findByRecordId(record.id)

        assertEquals(1, shares.size)
        assertEquals(TestData.USER_ID_2, shares[0].sharedWithUserId)
    }

    @Test
    fun `findByRecordId - returns empty for unshared record`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        assertTrue(sharedRecordDAO.findByRecordId(record.id).isEmpty())
    }

    @Test
    fun `findBySharedWithUserId - returns records shared with user`() = runBlocking {
        val f1 = createTestFolder()
        val r1 = createTestRecord(f1.id)
        val f2 = createTestFolder()
        val r2 = createTestRecord(f2.id)

        sharedRecordDAO.share(r1.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")
        sharedRecordDAO.share(r2.id, TestData.USER_ID_1, TestData.USER_ID_2, "editor")

        val shares = sharedRecordDAO.findBySharedWithUserId(TestData.USER_ID_2)
        assertEquals(2, shares.size)
    }

    @Test
    fun `findBySharedWithUserId - returns empty for user with no shares`() = runBlocking {
        assertTrue(sharedRecordDAO.findBySharedWithUserId(TestData.USER_ID_1).isEmpty())
    }

    @Test
    fun `delete - removes specific share and returns true`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)
        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")

        assertTrue(sharedRecordDAO.delete(record.id, TestData.USER_ID_2))
        assertTrue(sharedRecordDAO.findByRecordId(record.id).isEmpty())
    }

    @Test
    fun `delete - returns false when no matching share`() = runBlocking {
        assertFalse(sharedRecordDAO.delete(99999L, TestData.USER_ID_2))
    }

    @Test
    fun `deleteByRecordId - removes all shares for record`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)
        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")
        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_3, "editor")

        val deleted = sharedRecordDAO.deleteByRecordId(record.id)

        assertEquals(2, deleted)
        assertTrue(sharedRecordDAO.findByRecordId(record.id).isEmpty())
    }

    @Test
    fun `deleteByRecordId - returns 0 for record with no shares`() = runBlocking {
        assertEquals(0, sharedRecordDAO.deleteByRecordId(99999L))
    }

    @Test
    fun `hasAccess - returns true when user has share`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)
        sharedRecordDAO.share(record.id, TestData.USER_ID_1, TestData.USER_ID_2, "viewer")

        assertTrue(sharedRecordDAO.hasAccess(record.id, TestData.USER_ID_2))
    }

    @Test
    fun `hasAccess - returns false when user has no share`() = runBlocking {
        val folder = createTestFolder()
        val record = createTestRecord(folder.id)

        assertFalse(sharedRecordDAO.hasAccess(record.id, TestData.USER_ID_2))
    }

    @Test
    fun `hasAccess - returns false for non-existent record`() = runBlocking {
        assertFalse(sharedRecordDAO.hasAccess(99999L, TestData.USER_ID_1))
    }
}

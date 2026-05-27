package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import ru.kingofraccoons.tests.infrastructure.TestData

class FolderDaoTest : BaseDaoTest() {

    @Test
    fun `create - creates folder and returns it with generated id`() = runBlocking {
        val folder = folderDAO.create(TestData.USER_ID_1, "Documents", "My docs", false)

        assertNotNull(folder)
        assertTrue(folder!!.id > 0)
        assertEquals(TestData.USER_ID_1, folder.keycloakUserId)
        assertEquals("Documents", folder.name)
        assertEquals("My docs", folder.description)
        assertFalse(folder.isDefault)
    }

    @Test
    fun `create - creates default folder`() = runBlocking {
        val folder = folderDAO.create(TestData.USER_ID_1, "Work", null, true)

        assertTrue(folder!!.isDefault)
    }

    @Test
    fun `createDefaultFolders - creates three default folders`() = runBlocking {
        val folders = folderDAO.createDefaultFolders(TestData.USER_ID_1)

        assertEquals(3, folders.size)
        assertTrue(folders.all { it.isDefault })
        val names = folders.map { it.name }.toSet()
        assertTrue(names.contains("Работа"))
        assertTrue(names.contains("Учёба"))
        assertTrue(names.contains("Личное"))
    }

    @Test
    fun `createDefaultFolders - can be called twice producing six folders`() = runBlocking {
        folderDAO.createDefaultFolders(TestData.USER_ID_1)
        folderDAO.createDefaultFolders(TestData.USER_ID_1)

        val all = folderDAO.findByKeycloakUserId(TestData.USER_ID_1)
        assertEquals(6, all.size)
    }

    @Test
    fun `findByKeycloakUserId - returns only folders for specified user`() = runBlocking {
        folderDAO.create(TestData.USER_ID_1, "User1 Folder", null)
        folderDAO.create(TestData.USER_ID_2, "User2 Folder", null)

        val folders = folderDAO.findByKeycloakUserId(TestData.USER_ID_1)

        assertEquals(1, folders.size)
        assertEquals("User1 Folder", folders[0].name)
    }

    @Test
    fun `findByKeycloakUserId - returns empty list for unknown user`() = runBlocking {
        val folders = folderDAO.findByKeycloakUserId("nonexistent")

        assertTrue(folders.isEmpty())
    }

    @Test
    fun `hasDefaultFolders - returns true when three default folders exist`() = runBlocking {
        folderDAO.createDefaultFolders(TestData.USER_ID_1)

        assertTrue(folderDAO.hasDefaultFolders(TestData.USER_ID_1))
    }

    @Test
    fun `hasDefaultFolders - returns false when fewer than three defaults`() = runBlocking {
        folderDAO.create(TestData.USER_ID_1, "F1", null, true)
        folderDAO.create(TestData.USER_ID_1, "F2", null, true)

        assertFalse(folderDAO.hasDefaultFolders(TestData.USER_ID_1))
    }

    @Test
    fun `hasDefaultFolders - returns false for user with no folders`() = runBlocking {
        assertFalse(folderDAO.hasDefaultFolders(TestData.USER_ID_1))
    }

    @Test
    fun `findById - returns folder by id`() = runBlocking {
        val created = folderDAO.create(TestData.USER_ID_1, "Target", "desc", false)!!

        val found = folderDAO.findById(created.id)

        assertNotNull(found)
        assertEquals("Target", found!!.name)
        assertEquals("desc", found.description)
    }

    @Test
    fun `findById - returns null for non-existent id`() = runBlocking {
        val found = folderDAO.findById(99999L)

        assertNull(found)
    }

    @Test
    fun `update - updates name and description`() = runBlocking {
        val created = folderDAO.create(TestData.USER_ID_1, "Old Name", "Old Desc", false)!!

        folderDAO.update(created.id, "New Name", "New Desc")
        val found = folderDAO.findById(created.id)!!

        assertEquals("New Name", found.name)
        assertEquals("New Desc", found.description)
    }

    @Test
    fun `update - sets description to null`() = runBlocking {
        val created = folderDAO.create(TestData.USER_ID_1, "Name", "desc", false)!!

        folderDAO.update(created.id, "Name", null)
        val found = folderDAO.findById(created.id)!!

        assertNull(found.description)
    }

    @Test
    fun `update - returns null for non-existent id`() = runBlocking {
        val updated = folderDAO.update(99999L, "Name", "Desc")

        assertNull(updated)
    }

    @Test
    fun `delete - deletes existing folder and returns true`() = runBlocking {
        val created = folderDAO.create(TestData.USER_ID_1, "ToDelete", null, false)!!

        assertTrue(folderDAO.delete(created.id))
        assertNull(folderDAO.findById(created.id))
    }

    @Test
    fun `delete - returns false for non-existent id`() = runBlocking {
        assertFalse(folderDAO.delete(99999L))
    }
}

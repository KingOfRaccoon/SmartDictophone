package ru.kingofraccoons.tests.dao

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.kingofraccoons.tests.infrastructure.BaseDaoTest
import ru.kingofraccoons.tests.infrastructure.TestData

class UserProfileDaoTest : BaseDaoTest() {

    @Test
    fun `create - creates profile with all fields`() = runBlocking {
        val profile = userProfileDAO.create(
            TestData.USER_ID_1,
            telegram = "@user",
            avatarUrl = "http://avatar.url/img.jpg",
            emailForTranscripts = "user@test.com"
        )

        assertNotNull(profile)
        assertTrue(profile.id > 0)
        assertEquals(TestData.USER_ID_1, profile.keycloakUserId)
        assertEquals("@user", profile.telegram)
        assertEquals("http://avatar.url/img.jpg", profile.avatarUrl)
        assertEquals("user@test.com", profile.emailForTranscripts)
    }

    @Test
    fun `create - creates profile with minimal fields`() = runBlocking {
        val profile = userProfileDAO.create(TestData.USER_ID_1)

        assertNotNull(profile)
        assertEquals(TestData.USER_ID_1, profile.keycloakUserId)
        assertNull(profile.telegram)
        assertNull(profile.avatarUrl)
        assertNull(profile.emailForTranscripts)
    }

    @Test
    fun `findByKeycloakUserId - returns profile for existing user`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1, telegram = "@test")

        val found = userProfileDAO.findByKeycloakUserId(TestData.USER_ID_1)

        assertNotNull(found)
        assertEquals("@test", found!!.telegram)
    }

    @Test
    fun `findByKeycloakUserId - returns null for non-existent user`() = runBlocking {
        val found = userProfileDAO.findByKeycloakUserId("nonexistent-id")

        assertNull(found)
    }

    @Test
    fun `findOrCreate - creates profile when not exists`() = runBlocking {
        val profile = userProfileDAO.findOrCreate(TestData.USER_ID_1)

        assertNotNull(profile)
        assertEquals(TestData.USER_ID_1, profile.keycloakUserId)
        assertNull(profile.telegram)
    }

    @Test
    fun `findOrCreate - returns existing profile without overwriting`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1, telegram = "@original")

        val profile = userProfileDAO.findOrCreate(TestData.USER_ID_1)

        assertEquals("@original", profile.telegram)
    }

    @Test
    fun `update - updates telegram`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1, telegram = "@old")

        userProfileDAO.update(TestData.USER_ID_1, telegram = "@new")
        val found = userProfileDAO.findByKeycloakUserId(TestData.USER_ID_1)!!

        assertEquals("@new", found.telegram)
    }

    @Test
    fun `update - updates avatarUrl`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1)

        userProfileDAO.update(TestData.USER_ID_1, avatarUrl = "new_avatar.png")
        val found = userProfileDAO.findByKeycloakUserId(TestData.USER_ID_1)!!

        assertEquals("new_avatar.png", found.avatarUrl)
    }

    @Test
    fun `update - updates emailForTranscripts`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1)

        userProfileDAO.update(TestData.USER_ID_1, emailForTranscripts = "new@test.com")
        val found = userProfileDAO.findByKeycloakUserId(TestData.USER_ID_1)!!

        assertEquals("new@test.com", found.emailForTranscripts)
    }

    @Test
    fun `update - updates multiple fields at once`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1)

        userProfileDAO.update(
            TestData.USER_ID_1,
            telegram = "@multi",
            avatarUrl = "multi.png",
            emailForTranscripts = "multi@test.com"
        )
        val found = userProfileDAO.findByKeycloakUserId(TestData.USER_ID_1)!!

        assertEquals("@multi", found.telegram)
        assertEquals("multi.png", found.avatarUrl)
        assertEquals("multi@test.com", found.emailForTranscripts)
    }

    @Test
    fun `update - returns existing profile when all params null`() = runBlocking {
        val original = userProfileDAO.create(TestData.USER_ID_1, telegram = "@keep")

        val updated = userProfileDAO.update(TestData.USER_ID_1)

        assertNotNull(updated)
        assertEquals("@keep", updated!!.telegram)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun `update - returns null for non-existent user`() = runBlocking {
        val updated = userProfileDAO.update("nonexistent", telegram = "@test")

        assertNull(updated)
    }

    @Test
    fun `updateAvatarUrl - updates only avatar`() = runBlocking {
        userProfileDAO.create(TestData.USER_ID_1, telegram = "@keep", emailForTranscripts = "keep@test.com")

        userProfileDAO.updateAvatarUrl(TestData.USER_ID_1, "new_avatar.jpg")
        val found = userProfileDAO.findByKeycloakUserId(TestData.USER_ID_1)!!

        assertEquals("new_avatar.jpg", found.avatarUrl)
        assertEquals("@keep", found.telegram)
        assertEquals("keep@test.com", found.emailForTranscripts)
    }

    @Test
    fun `updateAvatarUrl - returns null for non-existent user`() = runBlocking {
        val updated = userProfileDAO.updateAvatarUrl("nonexistent", "avatar.jpg")

        assertNull(updated)
    }
}

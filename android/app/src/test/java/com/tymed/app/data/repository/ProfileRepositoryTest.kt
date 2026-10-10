package com.tymed.app.data.repository

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        repository = ProfileRepository(newInMemoryDatabase().profileDao())
    }

    /** A real (if empty) file, so deletion is actually observable — [ProfilePhotoStore.delete] is
     * a plain `File.delete()`. */
    private fun fakePhotoFile(): File = File.createTempFile("profile-photo", ".jpg").apply { deleteOnExit() }

    @Test
    fun `updateProfile deletes the old photo file when replaced by a new one`() = runTest {
        val oldPhoto = fakePhotoFile()
        val newPhoto = fakePhotoFile()
        repository.updateProfile(TEST_PROFILE_ID, "Abhinav", "#E07A5F", oldPhoto.path)
        assertTrue(oldPhoto.exists())

        repository.updateProfile(TEST_PROFILE_ID, "Abhinav", "#E07A5F", newPhoto.path)

        assertFalse("the replaced photo should be deleted", oldPhoto.exists())
        assertTrue("the new photo should be untouched", newPhoto.exists())
        assertEquals(newPhoto.path, repository.getProfile(TEST_PROFILE_ID)?.photoPath)
    }

    @Test
    fun `updateProfile deletes the old photo file when cleared`() = runTest {
        val oldPhoto = fakePhotoFile()
        repository.updateProfile(TEST_PROFILE_ID, "Abhinav", "#E07A5F", oldPhoto.path)

        repository.updateProfile(TEST_PROFILE_ID, "Abhinav", "#E07A5F", null)

        assertFalse(oldPhoto.exists())
        assertNull(repository.getProfile(TEST_PROFILE_ID)?.photoPath)
    }

    @Test
    fun `updateProfile does not touch the file when the photo is unchanged`() = runTest {
        val photo = fakePhotoFile()
        repository.updateProfile(TEST_PROFILE_ID, "Abhinav", "#E07A5F", photo.path)

        repository.updateProfile(TEST_PROFILE_ID, "Abhinav (renamed)", "#E07A5F", photo.path)

        assertTrue(photo.exists())
        assertEquals("Abhinav (renamed)", repository.getProfile(TEST_PROFILE_ID)?.name)
    }

    @Test
    fun `deleteProfile deletes its photo file too`() = runTest {
        val photo = fakePhotoFile()
        val secondId = repository.createProfile("Mom", "#3D405B", photo.path)

        repository.deleteProfile(secondId)

        assertFalse(photo.exists())
    }

    @Test
    fun `deleteProfile refuses to delete the last remaining profile`() = runTest {
        val deleted = repository.deleteProfile(TEST_PROFILE_ID)

        assertFalse(deleted)
        assertEquals(1, repository.listProfiles().size)
    }
}

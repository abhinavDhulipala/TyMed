package com.tymed.app.data.repository

import com.tymed.app.data.FakeAlarmScheduler
import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.entity.RecurrenceType
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
    private lateinit var db: TymedDatabase
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        alarmScheduler = FakeAlarmScheduler()
        repository = ProfileRepository(db.profileDao(), db.scheduleDao(), alarmScheduler)
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

    /** The FK cascade removes the schedules from Room, but their armed alarms re-arm themselves
     * from intent extras and would keep ringing daily — only this profile's must be cancelled. */
    @Test
    fun `deleteProfile cancels the alarms for every one of its schedules, and only its own`() = runTest {
        val medications = MedicationRepository(db.medicationDao())
        val schedules = ScheduleRepository(db.scheduleDao())
        val daily = RecurrenceInput(RecurrenceType.DAILY, null, null, null)
        fun med(name: String) = MedicationInput(name, null, null, null, null, null)

        val momId = repository.createProfile("Mom", "#3D405B")
        val momMedA = medications.createMedication(momId, med("Aspirin"))
        val momMedB = medications.createMedication(momId, med("Metformin"))
        val momSchedules = listOf(
            schedules.createSchedule(momMedA, "08:00", daily),
            schedules.createSchedule(momMedA, "20:00", daily),
            schedules.createSchedule(momMedB, "12:00", daily),
        )
        val myMed = medications.createMedication(TEST_PROFILE_ID, med("Vitamin D"))
        val mySchedule = schedules.createSchedule(myMed, "09:00", daily)

        assertTrue(repository.deleteProfile(momId))

        assertEquals(momSchedules.toSet(), alarmScheduler.cancelled.toSet())
        assertFalse(alarmScheduler.cancelled.contains(mySchedule))
        assertEquals(listOf(momId), alarmScheduler.clearedFollowUpMinutes)
        assertTrue("the cascade should still remove the schedules themselves", schedules.listSchedulesForProfile(momId).isEmpty())
    }

    @Test
    fun `deleteProfile refuses to delete the last remaining profile`() = runTest {
        val deleted = repository.deleteProfile(TEST_PROFILE_ID)

        assertFalse(deleted)
        assertEquals(1, repository.listProfiles().size)
        assertTrue("a refused delete must leave alarms alone", alarmScheduler.cancelled.isEmpty())
    }
}

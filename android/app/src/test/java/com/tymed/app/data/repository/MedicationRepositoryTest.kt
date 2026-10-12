package com.tymed.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MedicationRepositoryTest {
    private lateinit var repository: MedicationRepository

    @Before
    fun setUp() {
        repository = MedicationRepository(newInMemoryDatabase().medicationDao())
    }

    private fun input(name: String, dosage: String? = null, form: String? = null) =
        MedicationInput(name, dosage, form, null, null, null)

    @Test
    fun `findDuplicateMedication flags an exact name+dosage+form match`() = runTest {
        repository.createMedication(TEST_PROFILE_ID, input("Aspirin", "81mg", "tablet"))

        val duplicate = repository.findDuplicateMedication(TEST_PROFILE_ID, "aspirin", "81mg", "tablet")

        assertEquals("Aspirin", duplicate.exact?.name)
        assertEquals(emptyList<Any>(), duplicate.partial)
    }

    @Test
    fun `findDuplicateMedication treats a different dosage as only a partial match`() = runTest {
        repository.createMedication(TEST_PROFILE_ID, input("Aspirin", "81mg", "tablet"))

        val duplicate = repository.findDuplicateMedication(TEST_PROFILE_ID, "aspirin", "325mg", "tablet")

        assertNull(duplicate.exact)
        assertEquals(1, duplicate.partial.size)
        assertEquals("Aspirin", duplicate.partial[0].name)
    }

    @Test
    fun `findDuplicateMedication excludes the medication being edited`() = runTest {
        val id = repository.createMedication(TEST_PROFILE_ID, input("Aspirin", "81mg", "tablet"))

        val duplicate = repository.findDuplicateMedication(TEST_PROFILE_ID, "Aspirin", "81mg", "tablet", excludeId = id)

        assertNull(duplicate.exact)
        assertEquals(0, duplicate.partial.size)
    }

    @Test
    fun `pill count decrement never goes below zero or past null`() = runTest {
        val untracked = repository.createMedication(TEST_PROFILE_ID, input("Vitamin D"))
        repository.decrementPillCount(untracked)
        assertNull(repository.getMedication(untracked)?.pillsRemaining)

        val id = repository.createMedication(TEST_PROFILE_ID, input("Aspirin").copy(pillsRemaining = 0))
        repository.decrementPillCount(id)
        assertEquals(0, repository.getMedication(id)?.pillsRemaining)
    }

    @Test
    fun `pill count increment and decrement are symmetric`() = runTest {
        val id = repository.createMedication(TEST_PROFILE_ID, input("Aspirin").copy(pillsRemaining = 10))
        repository.decrementPillCount(id)
        repository.incrementPillCount(id)
        assertEquals(10, repository.getMedication(id)?.pillsRemaining)
    }
}

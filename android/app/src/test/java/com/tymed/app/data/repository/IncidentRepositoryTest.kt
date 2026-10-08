package com.tymed.app.data.repository

import com.tymed.app.data.entity.durationSeconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class IncidentRepositoryTest {
    private lateinit var repository: IncidentRepository

    @Before
    fun setUp() {
        repository = IncidentRepository(newInMemoryDatabase().incidentDao())
    }

    private fun input(
        type: String = "Seizure",
        startedAt: String = Instant.now().minusSeconds(90).toString(),
        endedAt: String = Instant.now().toString(),
        severity: String? = null,
        notes: String? = null,
    ) = IncidentInput(type, startedAt, endedAt, severity, notes)

    @Test
    fun `createIncident then getIncident round-trips every field`() = runTest {
        val id = repository.createIncident(TEST_PROFILE_ID, input(severity = "mild", notes = "Happened in the yard"))

        val saved = repository.getIncident(id)

        assertEquals("Seizure", saved?.type)
        assertEquals("mild", saved?.severity)
        assertEquals("Happened in the yard", saved?.notes)
        assertNotNull(saved?.endedAt)
    }

    @Test
    fun `updateIncident overwrites the existing row`() = runTest {
        val id = repository.createIncident(TEST_PROFILE_ID, input())

        repository.updateIncident(id, input(type = "Vomiting", severity = "severe"))

        val saved = repository.getIncident(id)
        assertEquals("Vomiting", saved?.type)
        assertEquals("severe", saved?.severity)
    }

    @Test
    fun `deleteIncident removes the row`() = runTest {
        val id = repository.createIncident(TEST_PROFILE_ID, input())

        repository.deleteIncident(id)

        assertNull(repository.getIncident(id))
    }

    @Test
    fun `recentIncidents filters by type and orders most recent first`() = runTest {
        repository.createIncident(TEST_PROFILE_ID, input(type = "Seizure", startedAt = Instant.now().minusSeconds(200).toString()))
        repository.createIncident(TEST_PROFILE_ID, input(type = "Vomiting", startedAt = Instant.now().minusSeconds(100).toString()))
        val newestSeizure = repository.createIncident(TEST_PROFILE_ID, input(type = "Seizure", startedAt = Instant.now().toString()))

        val seizures = repository.recentIncidents(TEST_PROFILE_ID, type = "Seizure")

        assertEquals(2, seizures.size)
        assertEquals(newestSeizure, seizures.first().id)
    }

    @Test
    fun `durationSeconds measures from startedAt to endedAt`() = runTest {
        val id = repository.createIncident(
            TEST_PROFILE_ID,
            input(startedAt = Instant.parse("2026-01-01T00:00:00Z").toString(), endedAt = Instant.parse("2026-01-01T00:01:30Z").toString()),
        )

        val saved = requireNotNull(repository.getIncident(id))

        assertEquals(90L, saved.durationSeconds())
    }
}

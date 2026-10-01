package com.tymed.app.ai

import com.tymed.app.data.FakeAlarmScheduler
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.repository.DoseActions
import com.tymed.app.data.repository.IncidentRepository
import com.tymed.app.data.repository.IntakeLogRepository
import com.tymed.app.data.repository.MedicationInput
import com.tymed.app.data.repository.MedicationRepository
import com.tymed.app.data.repository.ScheduleRepository
import com.tymed.app.data.repository.ScheduleSyncRepository
import com.tymed.app.data.repository.newInMemoryDatabase
import com.tymed.app.util.todayDateString
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolsTest {
    private lateinit var medications: MedicationRepository
    private lateinit var logs: IntakeLogRepository
    private lateinit var incidents: IncidentRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var tools: Tools

    @Before
    fun setUp() {
        val db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        val schedules = ScheduleRepository(db.scheduleDao())
        logs = IntakeLogRepository(db.intakeLogDao(), db.scheduleDao())
        incidents = IncidentRepository(db.incidentDao())
        alarmScheduler = FakeAlarmScheduler()
        val doseActions = DoseActions(logs, medications, schedules, alarmScheduler)
        val sync = ScheduleSyncRepository(schedules, alarmScheduler)
        tools = Tools(medications, schedules, logs, sync, alarmScheduler, doseActions, incidents)
    }

    @Test
    fun `add_medication returns needs_confirmation before writing anything`() = runTest {
        val result = tools.addMedication("Vitamin D", "1000 IU", listOf("08:00"), null, confirmed = false)

        assertTrue(result is AddMedicationResult.NeedsConfirmation)
        assertTrue(medications.listMedications().isEmpty())
    }

    @Test
    fun `add_medication writes once confirmed`() = runTest {
        tools.addMedication("Vitamin D", "1000 IU", listOf("08:00"), null, confirmed = true)

        val saved = medications.listMedications().single()
        assertEquals("Vitamin D", saved.name)
        assertEquals(1, alarmScheduler.scheduled.size)
    }

    @Test
    fun `add_medication for an existing exact match with no new schedule reports exists`() = runTest {
        tools.addMedication("Aspirin", null, null, null, confirmed = true)

        val result = tools.addMedication("Aspirin", null, null, null, confirmed = false)

        assertEquals(AddMedicationResult.Exists("Aspirin"), result)
    }

    @Test
    fun `add_medication for an existing medication with a new schedule delegates to update`() = runTest {
        tools.addMedication("Aspirin", null, listOf("08:00"), null, confirmed = true)

        val result = tools.addMedication("Aspirin", null, listOf("08:00", "20:00"), null, confirmed = true)

        assertTrue(result is AddMedicationResult.ExistingUpdated)
        assertTrue((result as AddMedicationResult.ExistingUpdated).update is UpdateScheduleResult.Updated)
    }

    @Test
    fun `update_medication_schedule reports not_found for an untracked name`() = runTest {
        val result = tools.updateMedicationSchedule("Nonexistent", null, listOf("08:00"), null, confirmed = true)
        assertTrue(result is UpdateScheduleResult.NotFound)
    }

    @Test
    fun `update_medication_schedule disambiguates by dosage`() = runTest {
        medications.createMedication(MedicationInput("Aspirin", "81mg", null, null, null, null))
        medications.createMedication(MedicationInput("Aspirin", "325mg", null, null, null, null))

        val ambiguous = tools.updateMedicationSchedule("Aspirin", null, listOf("08:00"), null, confirmed = false)
        assertTrue(ambiguous is UpdateScheduleResult.Ambiguous)

        val resolved = tools.updateMedicationSchedule("Aspirin", "81mg", listOf("09:00"), null, confirmed = false)
        assertTrue(resolved is UpdateScheduleResult.NeedsConfirmation)
    }

    @Test
    fun `get_todays_doses reflects the current status`() = runTest {
        tools.addMedication("Aspirin", null, listOf("08:00"), null, confirmed = true)

        val doses = tools.getTodaysDoses()

        assertEquals(1, doses.size)
        assertEquals(DoseStatus.PENDING, doses[0].status)
    }

    @Test
    fun `mark_dose_taken requires confirmation even for an unambiguous match`() = runTest {
        tools.addMedication("Aspirin", null, listOf("08:00"), null, confirmed = true)

        val pending = tools.markDoseTaken("Aspirin", null, confirmed = false)
        assertTrue(pending is MarkDoseTakenResult.NeedsConfirmation)
        assertEquals(DoseStatus.PENDING, logs.getDosesForDate(todayDateString()).first().log.status)

        val done = tools.markDoseTaken("Aspirin", null, confirmed = true)
        assertTrue(done is MarkDoseTakenResult.MarkedTaken)
        assertEquals(DoseStatus.TAKEN, logs.getDosesForDate(todayDateString()).first().log.status)
    }

    @Test
    fun `mark_dose_taken reports not_found when nothing pending matches`() = runTest {
        val result = tools.markDoseTaken("Nonexistent", null, confirmed = true)
        assertTrue(result is MarkDoseTakenResult.NotFound)
    }

    @Test
    fun `log_incident returns needs_confirmation before writing anything`() = runTest {
        val result = tools.logIncident("Seizure", null, 45, null, null, confirmed = false)

        assertTrue(result is LogIncidentResult.NeedsConfirmation)
        assertTrue(incidents.listIncidents().isEmpty())
    }

    @Test
    fun `log_incident writes once confirmed, with the computed end time and duration`() = runTest {
        val result = tools.logIncident("Seizure", null, 45, "mild", "In the yard", confirmed = true)

        assertTrue(result is LogIncidentResult.Logged)
        val saved = incidents.listIncidents().single()
        assertEquals("Seizure", saved.type)
        assertEquals("mild", saved.severity)
        assertEquals("In the yard", saved.notes)
        assertTrue(saved.endedAt != null)
    }

    @Test
    fun `log_incident with no durationSeconds logs it as still ongoing`() = runTest {
        tools.logIncident("Seizure", null, null, null, null, confirmed = true)

        val saved = incidents.listIncidents().single()
        assertEquals(null, saved.endedAt)
    }

    @Test
    fun `log_incident rejects a blank type`() = runTest {
        val result = tools.runTool("log_incident", mapOf("type" to "", "confirmed" to true))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `get_recent_incidents returns the most recently logged incidents first`() = runTest {
        tools.logIncident("Seizure", 10, 30, null, null, confirmed = true)
        tools.logIncident("Vomiting", 5, null, null, null, confirmed = true)

        val recent = tools.getRecentIncidents(null, null)

        assertEquals(2, recent.size)
        assertEquals("Vomiting", recent.first().type)
    }

    @Test
    fun `get_recent_incidents filters by type`() = runTest {
        tools.logIncident("Seizure", null, 30, null, null, confirmed = true)
        tools.logIncident("Vomiting", null, null, null, null, confirmed = true)

        val recent = tools.getRecentIncidents("Seizure", null)

        assertEquals(1, recent.size)
        assertEquals("Seizure", recent.first().type)
    }

    @Test
    fun `runTool surfaces a bad tool name as an error instead of throwing`() = runTest {
        val result = tools.runTool("not_a_real_tool", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `runTool surfaces a validation failure as an error`() = runTest {
        val result = tools.runTool("add_medication", mapOf("name" to ""))
        assertTrue(result is ToolResult.Error)
    }
}

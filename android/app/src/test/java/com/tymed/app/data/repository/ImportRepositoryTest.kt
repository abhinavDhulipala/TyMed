package com.tymed.app.data.repository

import com.tymed.app.data.FakeAlarmScheduler
import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.entity.Schedule
import com.tymed.app.export.JsonExporter
import com.tymed.app.importer.ImportParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportRepositoryTest {
    private lateinit var db: TymedDatabase
    private lateinit var medications: MedicationRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var importer: ImportRepository

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        schedules = ScheduleRepository(db.scheduleDao())
        alarmScheduler = FakeAlarmScheduler()
        importer = ImportRepository(db, medications, schedules, alarmScheduler)
    }

    // Deliberately high source ids, so a test would fail if any were written through unmapped.
    private val file = ImportData(
        medications = listOf(Medication(id = 100, name = "Keppra", dosage = "500mg", form = "tablet", pillsRemaining = 20, createdAt = "2026-01-01T00:00:00Z")),
        schedules = listOf(Schedule(id = 200, medicationId = 100, timeOfDay = "08:00")),
        intakeLogs = listOf(
            IntakeLog(id = 300, medicationId = 100, scheduleId = 200, scheduledDate = "2026-02-01", scheduledTime = "08:00", status = DoseStatus.TAKEN, takenAt = "2026-02-01T08:03:00Z"),
            IntakeLog(id = 301, medicationId = 100, scheduleId = 200, scheduledDate = "2026-02-02", scheduledTime = "08:00", status = DoseStatus.SKIPPED),
        ),
        incidents = listOf(Incident(id = 400, type = "Seizure", startedAt = "2026-02-03T10:00:00Z", endedAt = "2026-02-03T10:01:00Z", createdAt = "2026-02-03T10:02:00Z")),
    )

    @Test
    fun `imports into an empty database, linking rows by their new ids`() = runTest {
        val result = importer.importData(file)

        assertEquals(ImportResult(medicationsAdded = 1, schedulesAdded = 1, dosesAdded = 2, incidentsAdded = 1), result)
        val med = medications.listMedications().single()
        assertEquals(20, med.pillsRemaining)
        assertEquals("2026-01-01T00:00:00Z", med.createdAt)
        val sched = schedules.listAllSchedules().single()
        assertEquals(med.id, sched.medicationId)
        val taken = db.intakeLogDao().findBySchedule(sched.id, "2026-02-01")
        assertEquals(DoseStatus.TAKEN, taken?.status)
        assertEquals("2026-02-01T08:03:00Z", taken?.takenAt)
        assertEquals(1, db.incidentDao().getAll().size)
    }

    @Test
    fun `arms reminders for the schedules it adds`() = runTest {
        importer.importData(file)

        val rearmed = alarmScheduler.rearmed.single()
        assertEquals(listOf("Keppra"), rearmed.map { it.medicationName })
    }

    @Test
    fun `re-importing the same file changes nothing and arms nothing new`() = runTest {
        importer.importData(file)
        alarmScheduler.rearmed.clear()

        val result = importer.importData(file)

        assertEquals(ImportResult(skipped = 5), result)
        assertEquals(1, medications.listMedications().size)
        assertEquals(2, db.intakeLogDao().getAll().size)
        assertEquals(1, db.incidentDao().getAll().size)
        assertTrue(alarmScheduler.rearmed.isEmpty())
    }

    @Test
    fun `merges into a matching existing medication and schedule without overwriting them`() = runTest {
        val existingMed = medications.createMedication(MedicationInput("keppra ", "500MG", "Tablet", null, 3, null))
        val existingSched = schedules.createSchedule(existingMed, "08:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))

        val result = importer.importData(file)

        assertEquals(0, result.medicationsAdded)
        assertEquals(0, result.schedulesAdded)
        assertEquals(2, result.dosesAdded)
        // The device's own pill count is kept, not replaced by the file's.
        assertEquals(3, medications.getMedication(existingMed)?.pillsRemaining)
        assertEquals(DoseStatus.TAKEN, db.intakeLogDao().findBySchedule(existingSched, "2026-02-01")?.status)
    }

    @Test
    fun `a recorded outcome fills in an existing pending dose but never overrides a resolved one`() = runTest {
        val med = medications.createMedication(MedicationInput("Keppra", "500mg", "tablet", null, null, null))
        val sched = schedules.createSchedule(med, "08:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))
        val logs = db.intakeLogDao()
        logs.insert(IntakeLog(medicationId = med, scheduleId = sched, scheduledDate = "2026-02-01", scheduledTime = "08:00"))
        logs.insert(IntakeLog(medicationId = med, scheduleId = sched, scheduledDate = "2026-02-02", scheduledTime = "08:00", status = DoseStatus.TAKEN, takenAt = "2026-02-02T08:00:00Z"))

        val result = importer.importData(file)

        assertEquals(1, result.dosesUpdated)
        assertEquals(DoseStatus.TAKEN, logs.findBySchedule(sched, "2026-02-01")?.status)
        assertEquals("2026-02-01T08:03:00Z", logs.findBySchedule(sched, "2026-02-01")?.takenAt)
        assertEquals(DoseStatus.TAKEN, logs.findBySchedule(sched, "2026-02-02")?.status)
    }

    @Test
    fun `skips rows that reference a medication or schedule missing from the file`() = runTest {
        val orphans = file.copy(
            schedules = file.schedules + Schedule(id = 201, medicationId = 999, timeOfDay = "09:00"),
            intakeLogs = file.intakeLogs + IntakeLog(medicationId = 100, scheduleId = 999, scheduledDate = "2026-02-05", scheduledTime = "09:00"),
        )

        val result = importer.importData(orphans)

        assertEquals(2, result.skipped)
        assertEquals(1, schedules.listAllSchedules().size)
        assertEquals(2, db.intakeLogDao().getAll().size)
    }

    @Test
    fun `an export from one device imports cleanly into another`() = runTest {
        importer.importData(file)
        val exported = JsonExporter.render(
            ExportSnapshot(
                medications.listMedications(),
                schedules.listAllSchedules(),
                db.intakeLogDao().getAll(),
                db.incidentDao().getAll(),
                ExportSettings(followUpMinutes = 10, use24HourFormat = false, aiAssistantEnabled = false),
            ),
        )
        val otherDb = newInMemoryDatabase()
        val otherImporter = ImportRepository(otherDb, MedicationRepository(otherDb.medicationDao()), ScheduleRepository(otherDb.scheduleDao()), FakeAlarmScheduler())

        val result = otherImporter.importData(ImportParser.parse(exported.toByteArray()))

        assertEquals(ImportResult(medicationsAdded = 1, schedulesAdded = 1, dosesAdded = 2, incidentsAdded = 1), result)
    }
}

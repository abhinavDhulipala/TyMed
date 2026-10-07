package com.tymed.app.data.repository

import androidx.room.withTransaction
import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.Schedule

/** Everything [com.tymed.app.importer.ImportParser] recovered from an export file — the import
 * side's counterpart to [ExportSnapshot], minus settings (those are per-device preferences, not
 * history, so an import leaves them alone). Every id here is the *source* database's id: only
 * meaningful for linking rows to each other within the file, never written to this database. */
data class ImportData(
    val medications: List<Medication>,
    val schedules: List<Schedule>,
    val intakeLogs: List<IntakeLog>,
    val incidents: List<Incident>,
)

data class ImportResult(
    val medicationsAdded: Int = 0,
    val schedulesAdded: Int = 0,
    val dosesAdded: Int = 0,
    /** Existing pending doses that the file had recorded as taken/skipped. */
    val dosesUpdated: Int = 0,
    val incidentsAdded: Int = 0,
    /** Rows already on this device, or that referenced a row the file didn't contain. */
    val skipped: Int = 0,
)

/** Merges an [ImportData] into the database rather than replacing what's there, so importing is
 * never destructive and re-importing the same file is a no-op. Rows are matched against existing
 * data by their natural keys (not ids, which are meaningless across devices):
 * - medications by name + dosage + form (same rule as the duplicate check on the edit screen)
 * - schedules by medication + time of day
 * - doses by schedule + date, the same pair the unique index on intake_logs enforces
 * - incidents by type + start + end
 * A matched medication or schedule is reused as-is (not overwritten), and its source id is
 * remapped onto the existing row so the file's doses still attach to it. */
class ImportRepository(
    private val database: TymedDatabase,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
) {
    suspend fun importData(data: ImportData): ImportResult {
        val result = database.withTransaction { merge(data) }
        // After the transaction commits, so an alarm is never armed for a schedule that got
        // rolled back. Re-arming is idempotent, so re-arming every enabled schedule (rather than
        // tracking just the new ones) is safe and covers them all.
        if (result.schedulesAdded > 0) {
            alarmScheduler.rearmAllScheduleAlarms(scheduleRepository.listAllEnabledSchedulesWithMedication())
        }
        return result
    }

    private suspend fun merge(data: ImportData): ImportResult {
        val medicationDao = database.medicationDao()
        val scheduleDao = database.scheduleDao()
        val logDao = database.intakeLogDao()
        val incidentDao = database.incidentDao()
        var result = ImportResult()

        val medicationIds = mutableMapOf<Long, Long>()
        for (med in data.medications) {
            val existing = medicationRepository.findDuplicateMedication(med.name, med.dosage, med.form).exact
            medicationIds[med.id] = if (existing != null) {
                result = result.copy(skipped = result.skipped + 1)
                existing.id
            } else {
                result = result.copy(medicationsAdded = result.medicationsAdded + 1)
                medicationDao.insert(med.copy(id = 0))
            }
        }

        val scheduleIds = mutableMapOf<Long, Long>()
        for (sched in data.schedules) {
            val medicationId = medicationIds[sched.medicationId]
            if (medicationId == null) {
                result = result.copy(skipped = result.skipped + 1)
                continue
            }
            val existing = scheduleDao.listForMedication(medicationId).find { it.timeOfDay == sched.timeOfDay }
            scheduleIds[sched.id] = if (existing != null) {
                result = result.copy(skipped = result.skipped + 1)
                existing.id
            } else {
                result = result.copy(schedulesAdded = result.schedulesAdded + 1)
                scheduleDao.insert(sched.copy(id = 0, medicationId = medicationId, notificationIds = null))
            }
        }

        for (log in data.intakeLogs) {
            val medicationId = medicationIds[log.medicationId]
            val scheduleId = log.scheduleId?.let { scheduleIds[it] }
            if (medicationId == null || (log.scheduleId != null && scheduleId == null)) {
                result = result.copy(skipped = result.skipped + 1)
                continue
            }
            val existing = if (scheduleId != null) {
                logDao.findBySchedule(scheduleId, log.scheduledDate)
            } else {
                logDao.findUnscheduled(medicationId, log.scheduledDate, log.scheduledTime)
            }
            when {
                existing == null -> {
                    logDao.insert(log.copy(id = 0, medicationId = medicationId, scheduleId = scheduleId))
                    result = result.copy(dosesAdded = result.dosesAdded + 1)
                }
                // e.g. the app already lazily created today's row as pending before the import,
                // but the file knows it was actually taken — the recorded outcome wins.
                existing.status == DoseStatus.PENDING && log.status != DoseStatus.PENDING -> {
                    logDao.setStatus(existing.id, log.status, log.takenAt)
                    result = result.copy(dosesUpdated = result.dosesUpdated + 1)
                }
                else -> result = result.copy(skipped = result.skipped + 1)
            }
        }

        for (incident in data.incidents) {
            if (incidentDao.findMatching(incident.type, incident.startedAt, incident.endedAt) != null) {
                result = result.copy(skipped = result.skipped + 1)
            } else {
                incidentDao.insert(incident.copy(id = 0))
                result = result.copy(incidentsAdded = result.incidentsAdded + 1)
            }
        }

        return result
    }
}

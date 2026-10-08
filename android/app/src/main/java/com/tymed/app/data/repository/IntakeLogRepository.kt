package com.tymed.app.data.repository

import com.tymed.app.data.RecurrenceRule
import com.tymed.app.data.dao.DailyAdherenceRow
import com.tymed.app.data.dao.DoseWithMedication
import com.tymed.app.data.dao.IntakeLogDao
import com.tymed.app.data.dao.ScheduleDao
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.util.todayDateString
import java.time.Instant

data class DailyAdherence(val taken: Int, val resolved: Int)

class IntakeLogRepository(
    private val logDao: IntakeLogDao,
    private val scheduleDao: ScheduleDao,
) {
    /** Lazily creates a date's pending log rows for every enabled schedule (belonging to
     * [profileId]) that recurs on it and doesn't have a row yet. Works for any date — past,
     * today, or future — so a day's doses exist as soon as it's viewed, not just once it becomes
     * "today". */
    suspend fun ensureLogsForDate(profileId: Long, dateStr: String) {
        val schedules = scheduleDao.listAllEnabledForProfile(profileId)
        for (schedule in schedules) {
            val rule = RecurrenceRule(
                recurrenceType = schedule.recurrenceType,
                daysOfWeek = decodeDaysOfWeek(schedule.daysOfWeek),
                startDate = schedule.startDate,
                endDate = schedule.endDate,
            )
            if (!RecurrenceRule.isActiveOn(rule, dateStr)) continue

            val existing = logDao.findBySchedule(schedule.id, dateStr)
            if (existing == null) {
                logDao.insert(
                    IntakeLog(
                        medicationId = schedule.medicationId,
                        scheduleId = schedule.id,
                        scheduledDate = dateStr,
                        scheduledTime = schedule.timeOfDay,
                        status = DoseStatus.PENDING,
                    ),
                )
            }
        }
    }

    /** All doses scheduled for one specific date — including today, present and future times
     * alike. The Today screen is just this called with today's date: a day is a day, today
     * isn't special. */
    suspend fun getDosesForDate(profileId: Long, dateStr: String): List<DoseWithMedication> {
        ensureLogsForDate(profileId, dateStr)
        return logDao.getDosesForDate(profileId, dateStr)
    }

    suspend fun getLog(logId: Long): IntakeLog? = logDao.getById(logId)

    suspend fun getDoseById(logId: Long): DoseWithMedication? = logDao.getDoseById(logId)

    suspend fun setLogStatus(logId: Long, status: String) {
        logDao.setStatus(logId, status, if (status == DoseStatus.TAKEN) Instant.now().toString() else null)
    }

    /** Corrects the recorded taken time for a dose already marked taken. */
    suspend fun setLogTakenAt(logId: Long, takenAtIso: String) = logDao.setTakenAt(logId, takenAtIso)

    /** Resolves today's log row for a given schedule, creating it if needed (used by the alarm's
     * Taken button, which only knows the scheduleId, not which profile it belongs to). Inserts
     * the row directly rather than going through [ensureLogsForDate]'s whole-profile sweep —
     * the alarm having fired at all is itself proof this dose is due today, so this must not
     * leave Taken with nothing to mark. */
    suspend fun findOrCreateTodayLogForSchedule(scheduleId: Long): IntakeLog {
        val today = todayDateString()
        val existing = logDao.findBySchedule(scheduleId, today)
        if (existing != null) return existing

        val schedule = scheduleDao.getById(scheduleId) ?: error("No schedule found with id $scheduleId")
        logDao.insert(
            IntakeLog(
                medicationId = schedule.medicationId,
                scheduleId = scheduleId,
                scheduledDate = today,
                scheduledTime = schedule.timeOfDay,
                status = DoseStatus.PENDING,
            ),
        )
        // Re-query by (scheduleId, today) rather than trust the insert's returned row id: a
        // concurrent caller may have already inserted the same row, in which case this insert
        // was silently ignored (see IntakeLogDao.insert) and its "id" isn't a real row.
        return logDao.findBySchedule(scheduleId, today)
            ?: error("Failed to create or find today's log for schedule $scheduleId")
    }

    /** Per-day taken/resolved counts between two dates (inclusive), for the adherence calendar. */
    suspend fun getDailyAdherence(profileId: Long, startDate: String, endDate: String): Map<String, DailyAdherence> =
        logDao.getDailyAdherence(profileId, startDate, endDate).associate { row: DailyAdherenceRow ->
            row.scheduledDate to DailyAdherence(row.taken, row.resolved)
        }

    suspend fun getTodayStatusForSchedule(scheduleId: Long): String? = logDao.getStatus(scheduleId, todayDateString())

    /** Every intake log belonging to [profileId], for a single-profile data export. */
    suspend fun listLogsForProfile(profileId: Long): List<IntakeLog> = logDao.getAllForProfile(profileId)

    /** Every intake log across every profile, for a full "all profiles" data export. */
    suspend fun listAllLogs(): List<IntakeLog> = logDao.getAll()
}

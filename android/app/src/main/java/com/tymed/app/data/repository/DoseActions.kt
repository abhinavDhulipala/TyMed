package com.tymed.app.data.repository

import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.util.todayDateString

/** Marks a dose taken/skipped/pending, keeping the medication's pill count in sync — the single
 * mutation entrypoint used by the UI, the alarm's Taken button, and the AI assistant's tools, so
 * pill counts and alarm state can never drift out of sync with a dose's recorded status. */
class DoseActions(
    private val intakeLogRepository: IntakeLogRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
) {
    suspend fun markDose(logId: Long, status: String) {
        val log = intakeLogRepository.getLog(logId) ?: return

        if (log.status == DoseStatus.TAKEN && status != DoseStatus.TAKEN) {
            medicationRepository.incrementPillCount(log.medicationId)
        } else if (log.status != DoseStatus.TAKEN && status == DoseStatus.TAKEN) {
            medicationRepository.decrementPillCount(log.medicationId)
        }

        intakeLogRepository.setLogStatus(logId, status)

        // Resolving today's dose ahead of its alarm time must stop that alarm from still
        // ringing. The alarm chain fires natively with no visibility into intake_logs, so it
        // has to be told explicitly (skipTodaysDoseReminder re-arms for tomorrow instead of
        // cancelling outright, so the recurring chain isn't broken).
        if ((status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED) &&
            log.scheduleId != null &&
            log.scheduledDate == todayDateString()
        ) {
            val schedule = scheduleRepository.getScheduleWithMedication(log.scheduleId)
            if (schedule != null) {
                alarmScheduler.skipTodaysDoseReminder(schedule)
            }
        }
    }
}

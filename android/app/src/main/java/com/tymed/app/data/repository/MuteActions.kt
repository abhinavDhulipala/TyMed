package com.tymed.app.data.repository

import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.ReminderMute
import com.tymed.app.data.entity.DoseStatus

/** Mutes/unmutes a medication's alarms, keeping what's armed in step with what's stored — alarms
 * re-arm themselves without consulting Room, so a stored mute has no effect until they're
 * re-armed. */
class MuteActions(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeLogRepository: IntakeLogRepository,
    private val alarmScheduler: AlarmScheduler,
) {
    /** [days] null mutes until [unmute] is called. */
    suspend fun mute(medicationId: Long, days: Int?) {
        require(days == null || days > 0) { "days must be positive" }
        medicationRepository.setMutedUntil(medicationId, days?.let { ReminderMute.untilAfterDays(it) } ?: ReminderMute.INDEFINITE)
        alarmScheduler.rearmAllScheduleAlarms(scheduleRepository.listEnabledSchedulesWithMedication(medicationId))
    }

    suspend fun unmute(medicationId: Long) {
        medicationRepository.setMutedUntil(medicationId, null)
        // A dose already resolved ahead of its time today must not start ringing again.
        val (resolved, unresolved) = scheduleRepository.listEnabledSchedulesWithMedication(medicationId).partition {
            val status = intakeLogRepository.getTodayStatusForSchedule(it.schedule.id)
            status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED
        }
        alarmScheduler.rearmAllScheduleAlarms(unresolved)
        resolved.forEach { alarmScheduler.skipTodaysDoseReminder(it) }
    }
}

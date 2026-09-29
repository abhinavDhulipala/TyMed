package com.tymed.app.data.repository

import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.DoseReminderParams
import com.tymed.app.data.RecurrenceRule
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.entity.Schedule
import com.tymed.app.util.todayDateString

/** Brings a medication's schedules in line with the given time slots + repeat pattern, and
 * re-arms their reminders. Shared by the edit screen and the AI assistant's
 * update_medication_schedule tool so both edit schedules the exact same way. */
class ScheduleSyncRepository(
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
) {
    suspend fun syncMedicationSchedules(
        medicationId: Long,
        medicationName: String,
        medicationDosage: String?,
        times: List<String>,
        // startDate is derived per schedule below (the monthly anchor), not chosen by the caller.
        recurrenceType: String,
        daysOfWeek: List<Int>?,
        endDate: String?,
    ) {
        val uniqueTimes = times.distinct()
        val nextTimeSet = uniqueTimes.toSet()

        // Diff against what's already there instead of replacing everything on every save: a
        // schedule for a time slot that still exists keeps its id, which keeps its intake_logs
        // history (including any already-taken doses) and lets the alarm be updated in place
        // (idempotent re-arm) rather than cancelled and re-created. Only truly removed time
        // slots get deleted/cancelled.
        val existingSchedules = scheduleRepository.listSchedulesForMedication(medicationId)
        val existingByTime = existingSchedules.associateBy { it.timeOfDay }

        val removed = existingSchedules.filter { it.timeOfDay !in nextTimeSet }
        for (schedule in removed) {
            alarmScheduler.cancelDoseReminders(schedule.id)
            scheduleRepository.deleteSchedule(schedule.id)
        }

        for (time in uniqueTimes) {
            val existing = existingByTime[time]
            val startDate = resolveStartDate(existing, recurrenceType)
            val nextRule = RecurrenceInput(recurrenceType, daysOfWeek, startDate, endDate)

            val scheduleId: Long
            if (existing != null) {
                scheduleId = existing.id
                val existingRule = RecurrenceRule(
                    existing.recurrenceType,
                    decodeDaysOfWeek(existing.daysOfWeek),
                    existing.startDate,
                    existing.endDate,
                )
                val candidateRule = RecurrenceRule(nextRule.recurrenceType, nextRule.daysOfWeek, nextRule.startDate, nextRule.endDate)
                if (!RecurrenceRule.equals(existingRule, candidateRule)) {
                    scheduleRepository.updateScheduleRecurrence(existing.id, nextRule)
                }
            } else {
                scheduleId = scheduleRepository.createSchedule(medicationId, time, nextRule)
            }

            // Re-arming is idempotent (same schedule id => same alarm request code => the
            // platform updates the existing alarm in place), so it's safe to do
            // unconditionally — covers the medication name/dosage having changed even when the
            // time didn't.
            alarmScheduler.scheduleDoseReminders(
                DoseReminderParams(
                    scheduleId = scheduleId,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    dosage = medicationDosage,
                    timeOfDay = time,
                    recurrenceType = nextRule.recurrenceType,
                    daysOfWeek = nextRule.daysOfWeek,
                    startDate = nextRule.startDate,
                    endDate = nextRule.endDate,
                ),
            )
        }
    }

    /** The monthly day-of-month anchor: preserve an existing monthly schedule's own anchor (so
     * editing something unrelated doesn't shift when it fires), otherwise anchor to today —
     * either a brand new schedule, or one just switched to monthly from another recurrence
     * type. */
    private fun resolveStartDate(existing: Schedule?, recurrenceType: String): String? {
        if (recurrenceType != RecurrenceType.MONTHLY) return null
        if (existing != null && existing.recurrenceType == RecurrenceType.MONTHLY && existing.startDate != null) {
            return existing.startDate
        }
        return todayDateString()
    }
}

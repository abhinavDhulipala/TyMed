package com.tymed.app.data

import com.tymed.app.data.dao.ScheduleWithMedication

/** Records calls instead of touching AlarmManager — used by repository/business-logic tests that
 * don't care about the alarm subsystem itself (that's covered separately). */
class FakeAlarmScheduler : AlarmScheduler {
    val scheduled = mutableListOf<DoseReminderParams>()
    val cancelled = mutableListOf<Long>()
    var stopRingingCalls = 0
    val skipped = mutableListOf<ScheduleWithMedication>()
    val rearmed = mutableListOf<List<ScheduleWithMedication>>()
    val followUpMinutes = mutableListOf<Int>()

    override fun scheduleDoseReminders(params: DoseReminderParams) {
        scheduled += params
    }

    override fun cancelDoseReminders(scheduleId: Long) {
        cancelled += scheduleId
    }

    override fun stopRinging() {
        stopRingingCalls++
    }

    override fun skipTodaysDoseReminder(schedule: ScheduleWithMedication) {
        skipped += schedule
    }

    override fun rearmAllScheduleAlarms(schedules: List<ScheduleWithMedication>) {
        rearmed += schedules
    }

    override fun setFollowUpMinutes(profileId: Long, minutes: Int) {
        followUpMinutes += minutes
    }
}

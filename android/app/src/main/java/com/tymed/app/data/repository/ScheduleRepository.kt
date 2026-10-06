package com.tymed.app.data.repository

import com.tymed.app.data.dao.ScheduleDao
import com.tymed.app.data.dao.ScheduleWithMedication
import com.tymed.app.data.entity.Schedule
import org.json.JSONArray

data class RecurrenceInput(
    val recurrenceType: String,
    /** Required (non-null) when recurrenceType == WEEKLY; ignored otherwise. */
    val daysOfWeek: List<Int>?,
    /** Required when recurrenceType == MONTHLY (the day-of-month anchor). */
    val startDate: String?,
    val endDate: String?,
)

fun encodeDaysOfWeek(daysOfWeek: List<Int>?): String? =
    if (daysOfWeek.isNullOrEmpty()) null else JSONArray(daysOfWeek).toString()

fun decodeDaysOfWeek(json: String?): List<Int>? {
    if (json.isNullOrBlank()) return null
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getInt(it) }
    } catch (error: Exception) {
        null
    }
}

class ScheduleRepository(private val dao: ScheduleDao) {
    suspend fun listSchedulesForMedication(medicationId: Long): List<Schedule> = dao.listForMedication(medicationId)

    suspend fun getSchedule(id: Long): Schedule? = dao.getById(id)

    suspend fun getScheduleWithMedication(id: Long): ScheduleWithMedication? = dao.getWithMedication(id)

    /** Every enabled schedule across all medications, for re-arming native alarms on app start. */
    suspend fun listAllEnabledSchedulesWithMedication(): List<ScheduleWithMedication> = dao.listAllEnabledWithMedication()

    /** Every schedule regardless of medication or enabled state, for a full data export. */
    suspend fun listAllSchedules(): List<Schedule> = dao.getAll()

    suspend fun createSchedule(medicationId: Long, timeOfDay: String, recurrence: RecurrenceInput): Long =
        dao.insert(
            Schedule(
                medicationId = medicationId,
                timeOfDay = timeOfDay,
                enabled = 1,
                daysOfWeek = encodeDaysOfWeek(recurrence.daysOfWeek),
                recurrenceType = recurrence.recurrenceType,
                startDate = recurrence.startDate,
                endDate = recurrence.endDate,
            ),
        )

    /** Updates a schedule's recurrence in place, keeping its id (and therefore its intake_logs
     * history) stable — used when editing a medication so unchanged time slots aren't touched. */
    suspend fun updateScheduleRecurrence(id: Long, recurrence: RecurrenceInput) {
        dao.updateRecurrence(
            id = id,
            daysOfWeek = encodeDaysOfWeek(recurrence.daysOfWeek),
            recurrenceType = recurrence.recurrenceType,
            startDate = recurrence.startDate,
            endDate = recurrence.endDate,
        )
    }

    suspend fun deleteSchedule(id: Long) = dao.deleteById(id)
}

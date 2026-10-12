package com.tymed.app.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.tymed.app.data.entity.Schedule

data class ScheduleWithMedication(
    @Embedded
    val schedule: Schedule,
    val medicationName: String,
    val dosage: String?,
    val profileId: Long,
)

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Query(
        "UPDATE schedules SET days_of_week = :daysOfWeek, recurrence_type = :recurrenceType, " +
            "start_date = :startDate, end_date = :endDate WHERE id = :id",
    )
    suspend fun updateRecurrence(id: Long, daysOfWeek: String?, recurrenceType: String, startDate: String?, endDate: String?)

    @Query("SELECT * FROM schedules WHERE medication_id = :medicationId ORDER BY time_of_day")
    suspend fun listForMedication(medicationId: Long): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): Schedule?

    /** Every enabled schedule across every profile — this one deliberately stays unfiltered:
     * alarms must keep ringing for every profile's medications regardless of which profile is
     * active in the UI (it's a shared device), so [listAllEnabledWithMedication] (the version
     * that feeds the alarm rearm backstop) is also unfiltered. */
    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun listAllEnabled(): List<Schedule>

    @Query("SELECT schedules.* FROM schedules JOIN medications ON medications.id = schedules.medication_id WHERE schedules.enabled = 1 AND medications.profile_id = :profileId")
    suspend fun listAllEnabledForProfile(profileId: Long): List<Schedule>

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<Schedule>

    @Query("SELECT schedules.* FROM schedules JOIN medications ON medications.id = schedules.medication_id WHERE medications.profile_id = :profileId")
    suspend fun getAllForProfile(profileId: Long): List<Schedule>

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT schedules.*, medications.name AS medicationName, medications.dosage AS dosage, medications.profile_id AS profileId " +
            "FROM schedules JOIN medications ON medications.id = schedules.medication_id " +
            "WHERE schedules.id = :id",
    )
    suspend fun getWithMedication(id: Long): ScheduleWithMedication?

    /** Unfiltered across every profile — see [listAllEnabled] above for why. */
    @Query(
        "SELECT schedules.*, medications.name AS medicationName, medications.dosage AS dosage, medications.profile_id AS profileId " +
            "FROM schedules JOIN medications ON medications.id = schedules.medication_id " +
            "WHERE schedules.enabled = 1",
    )
    suspend fun listAllEnabledWithMedication(): List<ScheduleWithMedication>
}

package com.tymed.app.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tymed.app.data.entity.IntakeLog

data class DoseWithMedication(
    @Embedded
    val log: IntakeLog,
    val medicationName: String,
    val dosage: String?,
)

data class DailyAdherenceRow(
    val scheduledDate: String,
    val taken: Int,
    val resolved: Int,
)

@Dao
interface IntakeLogDao {
    // IGNORE, not the default ABORT: ensureLogsForDate's check-then-insert isn't atomic across
    // two suspend calls, so a losing racer here is expected and should be a silent no-op rather
    // than a constraint-violation crash (the unique index on (schedule_id, scheduled_date) is
    // what actually prevents the duplicate row; see IntakeLog.kt).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: IntakeLog): Long

    @Query("SELECT * FROM intake_logs WHERE id = :id")
    suspend fun getById(id: Long): IntakeLog?

    @Query("SELECT * FROM intake_logs WHERE schedule_id = :scheduleId AND scheduled_date = :date")
    suspend fun findBySchedule(scheduleId: Long, date: String): IntakeLog?

    // The unique (schedule_id, scheduled_date) index doesn't cover rows with no schedule (SQLite
    // treats NULLs as distinct), so those are matched on medication + date + time instead.
    @Query(
        "SELECT * FROM intake_logs WHERE schedule_id IS NULL AND medication_id = :medicationId " +
            "AND scheduled_date = :date AND scheduled_time = :time LIMIT 1",
    )
    suspend fun findUnscheduled(medicationId: Long, date: String, time: String): IntakeLog?

    @Query("UPDATE intake_logs SET status = :status, taken_at = :takenAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, takenAt: String?)

    @Query("UPDATE intake_logs SET taken_at = :takenAt WHERE id = :id")
    suspend fun setTakenAt(id: Long, takenAt: String)

    @Query(
        "SELECT intake_logs.*, medications.name AS medicationName, medications.dosage AS dosage " +
            "FROM intake_logs JOIN medications ON medications.id = intake_logs.medication_id " +
            "WHERE intake_logs.scheduled_date = :date AND medications.profile_id = :profileId " +
            "ORDER BY intake_logs.scheduled_time",
    )
    suspend fun getDosesForDate(profileId: Long, date: String): List<DoseWithMedication>

    @Query(
        "SELECT intake_logs.*, medications.name AS medicationName, medications.dosage AS dosage " +
            "FROM intake_logs JOIN medications ON medications.id = intake_logs.medication_id " +
            "WHERE intake_logs.id = :id",
    )
    suspend fun getDoseById(id: Long): DoseWithMedication?

    @Query(
        "SELECT intake_logs.scheduled_date AS scheduledDate, " +
            "SUM(CASE WHEN intake_logs.status = 'taken' THEN 1 ELSE 0 END) AS taken, " +
            "SUM(CASE WHEN intake_logs.status IN ('taken', 'skipped') THEN 1 ELSE 0 END) AS resolved " +
            "FROM intake_logs JOIN medications ON medications.id = intake_logs.medication_id " +
            "WHERE medications.profile_id = :profileId AND intake_logs.scheduled_date BETWEEN :start AND :end " +
            "GROUP BY intake_logs.scheduled_date",
    )
    suspend fun getDailyAdherence(profileId: Long, start: String, end: String): List<DailyAdherenceRow>

    @Query("SELECT status FROM intake_logs WHERE schedule_id = :scheduleId AND scheduled_date = :date")
    suspend fun getStatus(scheduleId: Long, date: String): String?

    @Query(
        "SELECT intake_logs.* FROM intake_logs JOIN medications ON medications.id = intake_logs.medication_id " +
            "WHERE medications.profile_id = :profileId ORDER BY intake_logs.scheduled_date, intake_logs.scheduled_time",
    )
    suspend fun getAllForProfile(profileId: Long): List<IntakeLog>

    @Query("SELECT * FROM intake_logs ORDER BY scheduled_date, scheduled_time")
    suspend fun getAll(): List<IntakeLog>
}

package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object DoseStatus {
    const val PENDING = "pending"
    const val TAKEN = "taken"
    const val SKIPPED = "skipped"
}

@Entity(
    tableName = "intake_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Unique (not just an index): ensureLogsForDate does a check-then-insert per
        // schedule+date that isn't atomic across two Room suspend calls, so two near-
        // simultaneous refreshes (e.g. a ViewModel's init{} and a LifecycleResumeEffect firing
        // moments apart) can both see "no row yet" and both try to insert. This constraint plus
        // IntakeLogDao.insert's OnConflictStrategy.IGNORE makes the second one a safe no-op
        // instead of a duplicate pending dose. Doesn't apply to the legacy on-disk schema (this
        // table is fully rebuilt in MIGRATION_4_5 anyway), so adding uniqueness here is safe.
        Index(value = ["schedule_id", "scheduled_date"], name = "idx_logs_schedule_date", unique = true),
        Index(value = ["scheduled_date"], name = "idx_logs_date"),
    ],
)
data class IntakeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "schedule_id")
    val scheduleId: Long?,
    @ColumnInfo(name = "scheduled_date")
    val scheduledDate: String,
    @ColumnInfo(name = "scheduled_time")
    val scheduledTime: String,
    @ColumnInfo(name = "status", defaultValue = "'pending'")
    val status: String = DoseStatus.PENDING,
    @ColumnInfo(name = "taken_at")
    val takenAt: String? = null,
)

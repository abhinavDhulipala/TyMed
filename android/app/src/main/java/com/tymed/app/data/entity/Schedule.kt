package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** recurrence_type values. Kept as raw strings (not a Kotlin enum) so they match the TEXT
 * column's on-disk values exactly — see [com.tymed.app.data.RecurrenceRule]. */
object RecurrenceType {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
}

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["medication_id"], name = "idx_schedules_medication"),
    ],
)
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "time_of_day")
    val timeOfDay: String,
    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Int = 1,
    // Legacy iOS-only column (JSON string[] of expo-notifications ids). Never populated or read
    // on Android; kept only so the on-disk schema matches exactly for existing installs.
    @ColumnInfo(name = "notification_ids")
    val notificationIds: String? = null,
    // Nullable JSON array of 0=Sun..6=Sat, only meaningful when recurrenceType == WEEKLY.
    @ColumnInfo(name = "days_of_week")
    val daysOfWeek: String? = null,
    @ColumnInfo(name = "recurrence_type", defaultValue = "'daily'")
    val recurrenceType: String = RecurrenceType.DAILY,
    // Lower bound for all recurrence types; for MONTHLY this also anchors the day-of-month.
    @ColumnInfo(name = "start_date")
    val startDate: String? = null,
    @ColumnInfo(name = "end_date")
    val endDate: String? = null,
)

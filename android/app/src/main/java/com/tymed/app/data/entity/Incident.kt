package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Duration
import java.time.Instant

/** A single occurrence of something notable that happened — a seizure, a vomiting episode,
 * a missed-dose reaction, or anything else with a start time and an end time. [type] is free
 * text rather than a fixed enum so the same table covers whatever the user wants to track, while
 * still working great out of the box for seizure logging. Always logged after the fact (both
 * [startedAt] and [endedAt] required) rather than with a live "still ongoing" timer — someone
 * logging a seizure is almost always doing so once it's over, not mid-event. */
@Entity(
    tableName = "incidents",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profile_id"], name = "idx_incidents_profile")],
)
data class Incident(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "started_at")
    val startedAt: String,
    @ColumnInfo(name = "ended_at")
    val endedAt: String,
    @ColumnInfo(name = "severity")
    val severity: String? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
)

/** Free-text convention, same as [DoseStatus]/[RecurrenceType] — not a hard restriction on
 * [Incident.severity], just the values the UI offers as quick picks. */
object IncidentSeverity {
    const val MILD = "mild"
    const val MODERATE = "moderate"
    const val SEVERE = "severe"
}

/** Derived rather than stored, so it can never go stale relative to [Incident.startedAt]/
 * [Incident.endedAt]. */
fun Incident.durationSeconds(): Long =
    Duration.between(Instant.parse(startedAt), Instant.parse(endedAt)).seconds

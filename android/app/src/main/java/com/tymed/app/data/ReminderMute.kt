package com.tymed.app.data

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Interprets [com.tymed.app.data.entity.Medication.mutedUntil]. Deliberately separate from
 * [RecurrenceRule]: a muted medication's doses are still due (they show up and can be marked as
 * usual, e.g. when someone else is giving them) — only its alarms stay quiet. */
object ReminderMute {
    /** Stored like any other end instant, just one that never arrives. */
    const val INDEFINITE = "9999-12-31T23:59:59Z"

    /** An instant rather than a calendar date, so N days always silences exactly N of each daily
     * dose — with a date, "1 day" set after the evening dose would silence nothing. */
    fun untilAfterDays(days: Int, now: ZonedDateTime = ZonedDateTime.now()): String =
        now.plusDays(days.toLong()).toInstant().toString()

    /** When the mute ends, or null if it's not (or no longer) muted. An unreadable value counts
     * as not muted: ringing when it shouldn't is a nuisance, staying silent is a missed dose. */
    fun activeUntil(mutedUntil: String?, now: Instant = Instant.now()): Instant? {
        if (mutedUntil == null) return null
        val until = try {
            Instant.parse(mutedUntil)
        } catch (error: DateTimeParseException) {
            return null
        }
        return until.takeIf { it > now }
    }

    fun isIndefinite(mutedUntil: String?): Boolean = mutedUntil == INDEFINITE
}

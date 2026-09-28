package expo.modules.tymedalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Calendar
import org.json.JSONArray

/**
 * A reboot (or the user force-stopping the app) wipes every [android.app.AlarmManager] entry
 * the app had armed — including AlarmReceiver's self-perpetuating daily chains — with nothing
 * left to fire and re-arm itself. Previously the only recovery was opening the app, so an
 * alarm due before the next app open silently never rang. This re-arms every enabled
 * schedule's next occurrence directly from the app's SQLite database (see
 * src/db/schedules.ts's listAllEnabledSchedulesWithMedication, which this query mirrors) —
 * there's no JS runtime available this early to call back into the existing rearm path.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
      return
    }

    try {
      rearmFromDatabase(context)
    } catch (error: Exception) {
      // Best-effort recovery — a failure here shouldn't crash the boot sequence. Schedules
      // still self-heal the next time the app is opened (rearmAllScheduleAlarms).
    }
  }

  private fun rearmFromDatabase(context: Context) {
    val dbFile = File(context.filesDir, "SQLite/tymed.db")
    if (!dbFile.exists()) return

    val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    try {
      val cursor = db.rawQuery(
        """
        SELECT schedules.id AS schedule_id,
               schedules.medication_id AS medication_id,
               schedules.time_of_day AS time_of_day,
               schedules.days_of_week AS days_of_week,
               schedules.recurrence_type AS recurrence_type,
               schedules.start_date AS start_date,
               schedules.end_date AS end_date,
               medications.name AS medication_name,
               medications.dosage AS dosage
        FROM schedules
        JOIN medications ON medications.id = schedules.medication_id
        WHERE schedules.enabled = 1
        """.trimIndent(),
        null
      )
      cursor.use {
        val today = AlarmReceiver.todayDateString()
        while (it.moveToNext()) {
          val endDate = it.getString(it.getColumnIndexOrThrow("end_date")) ?: ""
          // Mirrors armAndroidDailyAlarm's own guard: a schedule already past its end date
          // wouldn't have been armed in the first place.
          if (endDate.isNotBlank() && endDate < today) continue

          val scheduleId = it.getInt(it.getColumnIndexOrThrow("schedule_id"))
          val timeOfDay = it.getString(it.getColumnIndexOrThrow("time_of_day")) ?: continue
          val parts = timeOfDay.split(":")
          if (parts.size != 2) continue
          val hour = parts[0].toIntOrNull() ?: continue
          val minute = parts[1].toIntOrNull() ?: continue

          armAlarm(
            context,
            AlarmSchedule(
              triggerAtMillis = nextOccurrenceMillis(hour, minute),
              requestCode = scheduleId,
              scheduleId = scheduleId,
              medicationId = it.getInt(it.getColumnIndexOrThrow("medication_id")),
              medicationName = it.getString(it.getColumnIndexOrThrow("medication_name")) ?: "your medication",
              dosage = it.getString(it.getColumnIndexOrThrow("dosage")),
              isPrimary = true,
              hour = hour,
              minute = minute,
              recurrenceType = it.getString(it.getColumnIndexOrThrow("recurrence_type")) ?: "daily",
              daysOfWeek = decodeDaysOfWeek(it.getString(it.getColumnIndexOrThrow("days_of_week"))),
              startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
              endDate = endDate
            )
          )
        }
      }
    } finally {
      db.close()
    }
  }

  /** schedules.days_of_week is stored as a JSON array (e.g. "[0,2,4]"); the native side wants
   * the same comma-separated form src/notifications/scheduler.ts's encodeDaysOfWeek produces. */
  private fun decodeDaysOfWeek(json: String?): String {
    if (json.isNullOrBlank()) return ""
    return try {
      val array = JSONArray(json)
      (0 until array.length()).joinToString(",") { i -> array.getInt(i).toString() }
    } catch (error: Exception) {
      ""
    }
  }

  private fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
    val next = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    if (next.timeInMillis <= System.currentTimeMillis()) {
      next.add(Calendar.DAY_OF_YEAR, 1)
    }
    return next.timeInMillis
  }
}

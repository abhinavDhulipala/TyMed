package expo.modules.tymedalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

const val PREFS_NAME = "tymed_alarm_prefs"
const val PREF_FOLLOW_UP_MINUTES = "follow_up_minutes"

class AlarmRequest : Record {
  @Field val triggerAtMillis: Double = 0.0
  @Field val requestCode: Int = 0
  @Field val scheduleId: Int = 0
  @Field val medicationId: Int = 0
  @Field val medicationName: String = ""
  @Field val dosage: String? = null
  @Field val isPrimary: Boolean = false
  @Field val hour: Int = 0
  @Field val minute: Int = 0
  @Field val recurrenceType: String = "daily"
  @Field val daysOfWeek: String = ""
  @Field val startDate: String = ""
  @Field val endDate: String = ""
}

class TymedAlarmModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("React context is not available")

  override fun definition() = ModuleDefinition {
    Name("TymedAlarm")

    Function("scheduleAlarm") { request: AlarmRequest ->
      scheduleAlarm(request)
    }

    Function("cancelAlarm") { requestCode: Int ->
      cancelAlarm(requestCode)
    }

    Function("setFollowUpMinutes") { minutes: Int ->
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(PREF_FOLLOW_UP_MINUTES, minutes)
        .apply()
    }
  }

  private fun scheduleAlarm(request: AlarmRequest) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, request.requestCode)
      putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, request.scheduleId)
      putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, request.medicationId)
      putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, request.medicationName)
      putExtra(AlarmReceiver.EXTRA_DOSAGE, request.dosage)
      putExtra(AlarmReceiver.EXTRA_IS_PRIMARY, request.isPrimary)
      putExtra(AlarmReceiver.EXTRA_HOUR, request.hour)
      putExtra(AlarmReceiver.EXTRA_MINUTE, request.minute)
      putExtra(AlarmReceiver.EXTRA_RECURRENCE_TYPE, request.recurrenceType)
      putExtra(AlarmReceiver.EXTRA_DAYS_OF_WEEK, request.daysOfWeek)
      putExtra(AlarmReceiver.EXTRA_START_DATE, request.startDate)
      putExtra(AlarmReceiver.EXTRA_END_DATE, request.endDate)
    }
    val operation = PendingIntent.getBroadcast(
      context,
      request.requestCode,
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    val showPendingIntent = PendingIntent.getActivity(
      context,
      request.requestCode,
      showIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // setAlarmClock is the one exact-alarm API that needs no special permission — it's
    // meant precisely for user-visible alarm-clock behavior like this.
    alarmManager.setAlarmClock(
      AlarmManager.AlarmClockInfo(request.triggerAtMillis.toLong(), showPendingIntent),
      operation
    )
  }

  private fun cancelAlarm(requestCode: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val fireIntent = Intent(context, AlarmReceiver::class.java)
    val operation = PendingIntent.getBroadcast(
      context,
      requestCode,
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(operation)
    operation.cancel()
  }
}

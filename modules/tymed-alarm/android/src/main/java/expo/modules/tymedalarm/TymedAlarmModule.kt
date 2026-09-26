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

    Function("stopRinging") {
      stopRinging()
    }

    Function("setFollowUpMinutes") { minutes: Int ->
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(PREF_FOLLOW_UP_MINUTES, minutes)
        .apply()
    }
  }

  private fun scheduleAlarm(request: AlarmRequest) {
    armAlarm(
      context,
      AlarmSchedule(
        triggerAtMillis = request.triggerAtMillis.toLong(),
        requestCode = request.requestCode,
        scheduleId = request.scheduleId,
        medicationId = request.medicationId,
        medicationName = request.medicationName,
        dosage = request.dosage,
        isPrimary = request.isPrimary,
        hour = request.hour,
        minute = request.minute,
        recurrenceType = request.recurrenceType,
        daysOfWeek = request.daysOfWeek,
        startDate = request.startDate,
        endDate = request.endDate
      )
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

  // Cancelling a pending AlarmManager entry (above) only stops *future* fires — if an alarm
  // is already ringing (foreground service + looping MediaPlayer), it keeps going until told
  // to stop explicitly. Mirrors AlarmActivity's own Taken/Snooze path, so "mark as taken" from
  // the in-app UI silences an alarm that's currently ringing too. Only one alarm can ever be
  // ringing at a time (single service/notification), so no requestCode is needed here.
  private fun stopRinging() {
    val stopIntent = Intent(context, AlarmRingService::class.java).apply {
      action = AlarmRingService.ACTION_STOP
    }
    context.startService(stopIntent)
  }
}

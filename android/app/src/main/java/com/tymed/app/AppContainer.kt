package com.tymed.app

import android.content.Context
import com.tymed.app.ai.AiOrchestrator
import com.tymed.app.ai.GeminiNanoClient
import com.tymed.app.ai.Tools
import com.tymed.app.alarm.AndroidAlarmScheduler
import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.repository.DoseActions
import com.tymed.app.data.repository.ExportRepository
import com.tymed.app.data.repository.IncidentRepository
import com.tymed.app.data.repository.IntakeLogRepository
import com.tymed.app.data.repository.MedicationRepository
import com.tymed.app.data.repository.ScheduleRepository
import com.tymed.app.data.repository.ScheduleSyncRepository
import com.tymed.app.data.repository.SettingsRepository

/** Simple hand-rolled DI container (no Hilt/Dagger needed for an app this size) — one instance
 * built once in [TymedApplication] and shared by the alarm subsystem, the AI assistant, and
 * every Compose ViewModel. */
class AppContainer(context: Context) {
    val database: TymedDatabase = TymedDatabase.getInstance(context)
    val alarmScheduler: AlarmScheduler = AndroidAlarmScheduler(context)

    val medicationRepository = MedicationRepository(database.medicationDao())
    val scheduleRepository = ScheduleRepository(database.scheduleDao())
    val intakeLogRepository = IntakeLogRepository(database.intakeLogDao(), database.scheduleDao())
    val settingsRepository = SettingsRepository(database.appSettingDao())
    val incidentRepository = IncidentRepository(database.incidentDao())
    val exportRepository = ExportRepository(
        medicationRepository,
        scheduleRepository,
        intakeLogRepository,
        incidentRepository,
        settingsRepository,
    )

    val doseActions = DoseActions(intakeLogRepository, medicationRepository, scheduleRepository, alarmScheduler)
    val scheduleSyncRepository = ScheduleSyncRepository(scheduleRepository, alarmScheduler)

    // Retained here (not per-ViewModel) so the prepared Gemini Nano model survives navigating
    // away from and back to the Assistant tab, matching the old app-process-lifetime module.
    val geminiNanoClient = GeminiNanoClient()
    private val tools = Tools(
        medicationRepository,
        scheduleRepository,
        intakeLogRepository,
        scheduleSyncRepository,
        alarmScheduler,
        doseActions,
        incidentRepository,
    )
    val aiOrchestrator = AiOrchestrator(medicationRepository, tools, geminiNanoClient)
}

package com.tymed.app.data.repository

import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.Schedule

data class ExportSettings(
    val followUpMinutes: Int,
    val use24HourFormat: Boolean,
    val aiAssistantEnabled: Boolean,
)

/** A full point-in-time copy of everything stored in the app — handed to
 * [com.tymed.app.export.ExportFormat] renderers so each format (JSON/CSV/PDF) works from the
 * same snapshot instead of re-querying the database per format. */
data class ExportSnapshot(
    val medications: List<Medication>,
    val schedules: List<Schedule>,
    val intakeLogs: List<IntakeLog>,
    val incidents: List<Incident>,
    val settings: ExportSettings,
)

class ExportRepository(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeLogRepository: IntakeLogRepository,
    private val incidentRepository: IncidentRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun loadSnapshot(): ExportSnapshot = ExportSnapshot(
        medications = medicationRepository.listMedications(),
        schedules = scheduleRepository.listAllSchedules(),
        intakeLogs = intakeLogRepository.listAllLogs(),
        incidents = incidentRepository.listIncidents(),
        settings = ExportSettings(
            followUpMinutes = settingsRepository.getFollowUpMinutes(),
            use24HourFormat = settingsRepository.getUse24HourFormat(),
            aiAssistantEnabled = settingsRepository.getAiAssistantEnabled(),
        ),
    )
}

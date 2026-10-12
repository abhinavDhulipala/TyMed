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

/** One profile's full point-in-time data. */
data class ProfileExport(
    val profileName: String,
    val medications: List<Medication>,
    val schedules: List<Schedule>,
    val intakeLogs: List<IntakeLog>,
    val incidents: List<Incident>,
    val settings: ExportSettings,
)

/** Handed to [com.tymed.app.export.ExportFormat] renderers so each format (JSON/CSV/PDF) works
 * from the same snapshot instead of re-querying the database per format. Always a list of
 * per-profile sections — exporting just the active profile is the one-element case, exporting
 * every profile on the device is the many-element case, so renderers don't need to fork on scope. */
data class ExportSnapshot(val profiles: List<ProfileExport>)

class ExportRepository(
    private val profileRepository: ProfileRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeLogRepository: IntakeLogRepository,
    private val incidentRepository: IncidentRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Snapshots just [profileId]. */
    suspend fun loadSnapshot(profileId: Long): ExportSnapshot {
        val profile = profileRepository.getProfile(profileId) ?: return ExportSnapshot(emptyList())
        return ExportSnapshot(listOf(loadProfileExport(profile.id, profile.name)))
    }

    /** Snapshots every profile on the device, one section each. */
    suspend fun loadSnapshotForAllProfiles(): ExportSnapshot {
        val profiles = profileRepository.listProfiles()
        return ExportSnapshot(profiles.map { loadProfileExport(it.id, it.name) })
    }

    private suspend fun loadProfileExport(profileId: Long, profileName: String): ProfileExport =
        ProfileExport(
            profileName = profileName,
            medications = medicationRepository.listMedications(profileId),
            schedules = scheduleRepository.listSchedulesForProfile(profileId),
            intakeLogs = intakeLogRepository.listLogsForProfile(profileId),
            incidents = incidentRepository.listIncidents(profileId),
            settings = ExportSettings(
                followUpMinutes = settingsRepository.getFollowUpMinutes(profileId),
                use24HourFormat = settingsRepository.getUse24HourFormat(profileId),
                aiAssistantEnabled = settingsRepository.getAiAssistantEnabled(profileId),
            ),
        )
}

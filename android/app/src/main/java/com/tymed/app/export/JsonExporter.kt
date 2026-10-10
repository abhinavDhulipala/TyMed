package com.tymed.app.export

import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.Schedule
import com.tymed.app.data.repository.ExportSnapshot
import com.tymed.app.data.repository.ProfileExport
import com.tymed.app.data.repository.decodeDaysOfWeek
import org.json.JSONArray
import org.json.JSONObject

/** Renders an [ExportSnapshot] as a single nested JSON document, one object per profile — the
 * most complete and most machine-readable of the three export formats, at the cost of needing a
 * text editor to read. */
object JsonExporter {
    fun render(snapshot: ExportSnapshot): String {
        val root = JSONObject()
        root.put("profiles", snapshot.profiles.toJsonArray(::profileToJson))
        return root.toString(2)
    }

    private fun profileToJson(profile: ProfileExport) = JSONObject().apply {
        put("profileName", profile.profileName)
        put("medications", profile.medications.toJsonArray(::medicationToJson))
        put("schedules", profile.schedules.toJsonArray(::scheduleToJson))
        put("intakeLogs", profile.intakeLogs.toJsonArray(::intakeLogToJson))
        put("incidents", profile.incidents.toJsonArray(::incidentToJson))
        put(
            "settings",
            JSONObject().apply {
                put("followUpMinutes", profile.settings.followUpMinutes)
                put("use24HourFormat", profile.settings.use24HourFormat)
                put("aiAssistantEnabled", profile.settings.aiAssistantEnabled)
            },
        )
    }

    private fun medicationToJson(medication: Medication) = JSONObject().apply {
        put("id", medication.id)
        put("name", medication.name)
        put("dosage", medication.dosage)
        put("form", medication.form)
        put("notes", medication.notes)
        put("pillsRemaining", medication.pillsRemaining)
        put("refillThreshold", medication.refillThreshold)
        put("createdAt", medication.createdAt)
    }

    private fun scheduleToJson(schedule: Schedule) = JSONObject().apply {
        put("id", schedule.id)
        put("medicationId", schedule.medicationId)
        put("timeOfDay", schedule.timeOfDay)
        put("enabled", schedule.enabled == 1)
        put("daysOfWeek", decodeDaysOfWeek(schedule.daysOfWeek)?.let { JSONArray(it) })
        put("recurrenceType", schedule.recurrenceType)
        put("startDate", schedule.startDate)
        put("endDate", schedule.endDate)
    }

    private fun intakeLogToJson(log: IntakeLog) = JSONObject().apply {
        put("id", log.id)
        put("medicationId", log.medicationId)
        put("scheduleId", log.scheduleId)
        put("scheduledDate", log.scheduledDate)
        put("scheduledTime", log.scheduledTime)
        put("status", log.status)
        put("takenAt", log.takenAt)
    }

    private fun incidentToJson(incident: Incident) = JSONObject().apply {
        put("id", incident.id)
        put("type", incident.type)
        put("startedAt", incident.startedAt)
        put("endedAt", incident.endedAt)
        put("severity", incident.severity)
        put("notes", incident.notes)
        put("createdAt", incident.createdAt)
    }

    private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(toJson(it)) }
        return array
    }
}

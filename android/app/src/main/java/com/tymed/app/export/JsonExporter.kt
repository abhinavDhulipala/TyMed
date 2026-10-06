package com.tymed.app.export

import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.Schedule
import com.tymed.app.data.repository.ExportSnapshot
import com.tymed.app.data.repository.decodeDaysOfWeek
import org.json.JSONArray
import org.json.JSONObject

/** Renders an [ExportSnapshot] as a single nested JSON document — the most complete and most
 * machine-readable of the three export formats, at the cost of needing a text editor to read. */
object JsonExporter {
    fun render(snapshot: ExportSnapshot): String {
        val root = JSONObject()
        root.put("medications", snapshot.medications.toJsonArray(::medicationToJson))
        root.put("schedules", snapshot.schedules.toJsonArray(::scheduleToJson))
        root.put("intakeLogs", snapshot.intakeLogs.toJsonArray(::intakeLogToJson))
        root.put("incidents", snapshot.incidents.toJsonArray(::incidentToJson))
        root.put(
            "settings",
            JSONObject().apply {
                put("followUpMinutes", snapshot.settings.followUpMinutes)
                put("use24HourFormat", snapshot.settings.use24HourFormat)
                put("aiAssistantEnabled", snapshot.settings.aiAssistantEnabled)
            },
        )
        return root.toString(2)
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

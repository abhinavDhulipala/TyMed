package com.tymed.app.export

import com.tymed.app.data.repository.ExportSnapshot
import com.tymed.app.data.repository.decodeDaysOfWeek
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Renders an [ExportSnapshot] as a zip of one CSV per table — the shape a spreadsheet app
 * expects, as opposed to [JsonExporter]'s single nested document. */
object CsvExporter {
    fun render(snapshot: ExportSnapshot): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeCsv(
                "medications.csv",
                listOf("id", "name", "dosage", "form", "notes", "pillsRemaining", "refillThreshold", "createdAt"),
                snapshot.medications.map { med ->
                    listOf(med.id, med.name, med.dosage, med.form, med.notes, med.pillsRemaining, med.refillThreshold, med.createdAt)
                },
            )
            zip.writeCsv(
                "schedules.csv",
                listOf("id", "medicationId", "timeOfDay", "enabled", "daysOfWeek", "recurrenceType", "startDate", "endDate"),
                snapshot.schedules.map { sched ->
                    listOf(
                        sched.id,
                        sched.medicationId,
                        sched.timeOfDay,
                        sched.enabled == 1,
                        decodeDaysOfWeek(sched.daysOfWeek)?.joinToString("|"),
                        sched.recurrenceType,
                        sched.startDate,
                        sched.endDate,
                    )
                },
            )
            zip.writeCsv(
                "intake_logs.csv",
                listOf("id", "medicationId", "scheduleId", "scheduledDate", "scheduledTime", "status", "takenAt"),
                snapshot.intakeLogs.map { log ->
                    listOf(log.id, log.medicationId, log.scheduleId, log.scheduledDate, log.scheduledTime, log.status, log.takenAt)
                },
            )
            zip.writeCsv(
                "incidents.csv",
                listOf("id", "type", "startedAt", "endedAt", "severity", "notes", "createdAt"),
                snapshot.incidents.map { incident ->
                    listOf(incident.id, incident.type, incident.startedAt, incident.endedAt, incident.severity, incident.notes, incident.createdAt)
                },
            )
            zip.writeCsv(
                "settings.csv",
                listOf("key", "value"),
                listOf(
                    listOf("followUpMinutes", snapshot.settings.followUpMinutes),
                    listOf("use24HourFormat", snapshot.settings.use24HourFormat),
                    listOf("aiAssistantEnabled", snapshot.settings.aiAssistantEnabled),
                ),
            )
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.writeCsv(entryName: String, header: List<String>, rows: List<List<Any?>>) {
        putNextEntry(ZipEntry(entryName))
        val csv = StringBuilder()
        csv.append(header.joinToString(",") { it.toCsvField() }).append("\r\n")
        rows.forEach { row -> csv.append(row.joinToString(",") { it.toCsvField() }).append("\r\n") }
        write(csv.toString().toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun Any?.toCsvField(): String {
        val text = this?.toString() ?: ""
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }
}

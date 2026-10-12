package com.tymed.app.export

import com.tymed.app.data.repository.ExportSnapshot
import com.tymed.app.data.repository.ProfileExport
import com.tymed.app.data.repository.decodeDaysOfWeek
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Renders an [ExportSnapshot] as a zip of one CSV per table — the shape a spreadsheet app
 * expects, as opposed to [JsonExporter]'s single nested document. Every row carries a "profile"
 * column so a multi-profile export stays one flat table per type rather than one file per
 * profile. */
object CsvExporter {
    fun render(snapshot: ExportSnapshot): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeCsv(
                "medications.csv",
                listOf("profile", "id", "name", "dosage", "form", "notes", "pillsRemaining", "refillThreshold", "createdAt"),
                snapshot.profiles.flatMap { profile ->
                    profile.medications.map { med ->
                        listOf(
                            profile.profileName, med.id, med.name, med.dosage, med.form, med.notes,
                            med.pillsRemaining, med.refillThreshold, med.createdAt,
                        )
                    }
                },
            )
            zip.writeCsv(
                "schedules.csv",
                listOf("profile", "id", "medicationId", "timeOfDay", "enabled", "daysOfWeek", "recurrenceType", "startDate", "endDate"),
                snapshot.profiles.flatMap { profile ->
                    profile.schedules.map { sched ->
                        listOf(
                            profile.profileName,
                            sched.id,
                            sched.medicationId,
                            sched.timeOfDay,
                            sched.enabled == 1,
                            decodeDaysOfWeek(sched.daysOfWeek)?.joinToString("|"),
                            sched.recurrenceType,
                            sched.startDate,
                            sched.endDate,
                        )
                    }
                },
            )
            zip.writeCsv(
                "intake_logs.csv",
                listOf("profile", "id", "medicationId", "scheduleId", "scheduledDate", "scheduledTime", "status", "takenAt"),
                snapshot.profiles.flatMap { profile ->
                    profile.intakeLogs.map { log ->
                        listOf(
                            profile.profileName, log.id, log.medicationId, log.scheduleId,
                            log.scheduledDate, log.scheduledTime, log.status, log.takenAt,
                        )
                    }
                },
            )
            zip.writeCsv(
                "incidents.csv",
                listOf("profile", "id", "type", "startedAt", "endedAt", "severity", "notes", "createdAt"),
                snapshot.profiles.flatMap { profile ->
                    profile.incidents.map { incident ->
                        listOf(
                            profile.profileName, incident.id, incident.type, incident.startedAt,
                            incident.endedAt, incident.severity, incident.notes, incident.createdAt,
                        )
                    }
                },
            )
            zip.writeCsv(
                "settings.csv",
                listOf("profile", "key", "value"),
                snapshot.profiles.flatMap { profile: ProfileExport ->
                    listOf(
                        listOf(profile.profileName, "followUpMinutes", profile.settings.followUpMinutes),
                        listOf(profile.profileName, "use24HourFormat", profile.settings.use24HourFormat),
                        listOf(profile.profileName, "aiAssistantEnabled", profile.settings.aiAssistantEnabled),
                    )
                },
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

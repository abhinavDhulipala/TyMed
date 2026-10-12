package com.tymed.app.importer

import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.entity.Schedule
import com.tymed.app.data.repository.ImportData
import com.tymed.app.data.repository.ImportProfileData
import com.tymed.app.data.repository.encodeDaysOfWeek
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A file that can't be imported. [message] is shown to the user as-is. */
class ImportException(message: String) : Exception(message)

// Far beyond any real export (years of doses are a few MB of JSON), but bounded so a zip bomb
// or a wrongly-picked huge file fails cleanly instead of running the app out of memory.
private const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

// Used for a CSV export with no "profile" column (e.g. one hand-built in a test, or a very old
// single-profile export) — grouped as a single section under this name.
private const val DEFAULT_IMPORT_PROFILE_NAME = "Imported"

private val TIME_OF_DAY = Regex("""([01]\d|2[0-3]):[0-5]\d""")
private val RECURRENCE_TYPES = setOf(RecurrenceType.DAILY, RecurrenceType.WEEKLY, RecurrenceType.MONTHLY)
private val DOSE_STATUSES = setOf(DoseStatus.PENDING, DoseStatus.TAKEN, DoseStatus.SKIPPED)

/** Reads back what [com.tymed.app.export.JsonExporter] and [com.tymed.app.export.CsvExporter]
 * write (the PDF report is human-only, see [com.tymed.app.export.PdfExporter]). Both formats are
 * funneled through the same [ImportRecord] accessors, so validation is identical either way —
 * and strict about anything the rest of the app parses without a fallback (times of day, dates,
 * instants), since a malformed value there would crash a screen long after the import finished. */
object ImportParser {
    fun parse(input: InputStream): ImportData = parse(input.readCapped())

    fun parse(bytes: ByteArray): ImportData = when {
        bytes.startsWith("PK\u0003\u0004") -> parseCsvZip(bytes)
        bytes.startsWith("%PDF") -> throw ImportException(
            "PDF exports are a printable report and can't be imported. Use a JSON or CSV export instead.",
        )
        else -> parseJson(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
    }

    /** The exporters write one JSON object per profile under a top-level "profiles" array (see
     * [com.tymed.app.export.JsonExporter]) \u2014 a single-profile export is just the one-element
     * case. */
    private fun parseJson(text: String): ImportData {
        val root = try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw notAnExport()
        }
        val profilesArray = root.optJSONArray("profiles") ?: throw notAnExport()
        val profiles = (0 until profilesArray.length()).map { profileIndex ->
            val profileObj = profilesArray.optJSONObject(profileIndex)
                ?: throw ImportException("profiles entry ${profileIndex + 1} isn't an object.")
            val profileName = profileObj.optString("profileName").takeIf { it.isNotBlank() } ?: DEFAULT_IMPORT_PROFILE_NAME

            fun records(key: String): List<ImportRecord> {
                if (profileObj.isNull(key)) return emptyList()
                val array = profileObj.optJSONArray(key) ?: throw ImportException("\"$key\" should be a list.")
                return (0 until array.length()).map { index ->
                    val obj = array.optJSONObject(index) ?: throw ImportException("$key entry ${index + 1} isn't an object.")
                    ImportRecord("$key entry ${index + 1}") { field -> if (obj.isNull(field)) null else obj.get(field) }
                }
            }
            build(profileName, records("medications"), records("schedules"), records("intakeLogs"), records("incidents"))
        }
        return ImportData(profiles)
    }

    /** The exporters give every row a "profile" column (see [com.tymed.app.export.CsvExporter]),
     * so rows are grouped into profile sections by that column rather than by file. A CSV with no
     * such column (e.g. a minimal hand-built fixture) falls back to one [DEFAULT_IMPORT_PROFILE_NAME]
     * section. */
    private fun parseCsvZip(bytes: ByteArray): ImportData {
        val files = mutableMapOf<String, String>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val content = zip.readCapped(MAX_IMPORT_BYTES - totalBytes)
                totalBytes += content.size
                // Match on the bare file name, so a zip that was extracted and re-compressed into
                // a folder still imports.
                files[entry.name.substringAfterLast('/')] = content.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            }
        }
        if ("medications.csv" !in files) throw notAnExport()
        fun recordsByProfile(fileName: String): Map<String, List<ImportRecord>> {
            val rows = parseCsv(files[fileName] ?: return emptyMap())
            val header = rows.firstOrNull() ?: return emptyMap()
            return rows.drop(1).mapIndexed { index, row ->
                val get: (String) -> String? = { field ->
                    header.indexOf(field).takeIf { it >= 0 }?.let { row.getOrNull(it) }?.takeIf { it.isNotEmpty() }
                }
                val profileName = get("profile") ?: DEFAULT_IMPORT_PROFILE_NAME
                // Row numbers as a spreadsheet shows them: the header is row 1.
                profileName to ImportRecord("$fileName row ${index + 2}", get)
            }.groupBy({ it.first }, { it.second })
        }

        val medications = recordsByProfile("medications.csv")
        val schedules = recordsByProfile("schedules.csv")
        val intakeLogs = recordsByProfile("intake_logs.csv")
        val incidents = recordsByProfile("incidents.csv")
        val profileNames = (medications.keys + schedules.keys + intakeLogs.keys + incidents.keys).distinct()

        return ImportData(
            profileNames.map { name ->
                build(
                    name,
                    medications[name] ?: emptyList(),
                    schedules[name] ?: emptyList(),
                    intakeLogs[name] ?: emptyList(),
                    incidents[name] ?: emptyList(),
                )
            },
        )
    }

    private fun build(
        profileName: String,
        medications: List<ImportRecord>,
        schedules: List<ImportRecord>,
        intakeLogs: List<ImportRecord>,
        incidents: List<ImportRecord>,
    ) = ImportProfileData(
        profileName = profileName,
        medications = medications.map { it.toMedication() },
        schedules = schedules.map { it.toSchedule() },
        intakeLogs = intakeLogs.map { it.toIntakeLog() },
        incidents = incidents.map { it.toIncident() },
    )

    private fun ImportRecord.toMedication() = Medication(
        id = requireLong("id"),
        // Overwritten by ImportRepository with the resolved target profile — unknown at parse time.
        profileId = 0,
        name = requireString("name").trim().ifEmpty { fail("\"name\" is empty") },
        dosage = string("dosage"),
        form = string("form"),
        notes = string("notes"),
        pillsRemaining = int("pillsRemaining"),
        refillThreshold = int("refillThreshold"),
        createdAt = instant("createdAt") ?: Instant.now().toString(),
    )

    private fun ImportRecord.toSchedule(): Schedule {
        val recurrenceType = string("recurrenceType") ?: RecurrenceType.DAILY
        if (recurrenceType !in RECURRENCE_TYPES) fail("unknown recurrenceType \"$recurrenceType\"")
        val daysOfWeek = intList("daysOfWeek")
        if (daysOfWeek != null && daysOfWeek.any { it !in 0..6 }) fail("daysOfWeek must be between 0 (Sun) and 6 (Sat)")
        val startDate = date("startDate")
        if (recurrenceType == RecurrenceType.MONTHLY && startDate == null) fail("a monthly schedule needs a startDate")
        return Schedule(
            id = requireLong("id"),
            medicationId = requireLong("medicationId"),
            timeOfDay = timeOfDay("timeOfDay"),
            enabled = if (bool("enabled") == false) 0 else 1,
            daysOfWeek = encodeDaysOfWeek(daysOfWeek),
            recurrenceType = recurrenceType,
            startDate = startDate,
            endDate = date("endDate"),
        )
    }

    private fun ImportRecord.toIntakeLog(): IntakeLog {
        val status = string("status") ?: DoseStatus.PENDING
        if (status !in DOSE_STATUSES) fail("unknown status \"$status\"")
        return IntakeLog(
            id = long("id") ?: 0,
            medicationId = requireLong("medicationId"),
            scheduleId = long("scheduleId"),
            scheduledDate = date("scheduledDate") ?: fail("\"scheduledDate\" is missing"),
            scheduledTime = timeOfDay("scheduledTime"),
            status = status,
            takenAt = instant("takenAt"),
        )
    }

    private fun ImportRecord.toIncident(): Incident {
        val startedAt = instant("startedAt") ?: fail("\"startedAt\" is missing")
        val endedAt = instant("endedAt") ?: fail("\"endedAt\" is missing")
        return Incident(
            id = long("id") ?: 0,
            // Overwritten by ImportRepository with the resolved target profile — unknown at parse time.
            profileId = 0,
            type = requireString("type").trim().ifEmpty { fail("\"type\" is empty") },
            startedAt = startedAt,
            endedAt = endedAt,
            severity = string("severity"),
            notes = string("notes"),
            createdAt = instant("createdAt") ?: Instant.now().toString(),
        )
    }

    private fun ImportRecord.timeOfDay(field: String): String {
        val value = requireString(field)
        if (!TIME_OF_DAY.matches(value)) fail("\"$field\" should be a time like 08:30, not \"$value\"")
        return value
    }

    private fun ImportRecord.date(field: String): String? = string(field)?.also { value ->
        try {
            LocalDate.parse(value)
        } catch (error: DateTimeParseException) {
            fail("\"$field\" should be a date like 2026-01-31, not \"$value\"")
        }
    }

    private fun ImportRecord.instant(field: String): String? = string(field)?.also { value ->
        try {
            Instant.parse(value)
        } catch (error: DateTimeParseException) {
            fail("\"$field\" isn't a valid timestamp: \"$value\"")
        }
    }

    private fun InputStream.readCapped(limit: Long = MAX_IMPORT_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) throw ImportException("This file is too large to import.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun notAnExport() = ImportException("This doesn't look like a TyMed export. Choose a JSON or CSV file exported from TyMed.")

    private fun ByteArray.startsWith(prefix: String): Boolean {
        val prefixBytes = prefix.toByteArray(Charsets.ISO_8859_1)
        return size >= prefixBytes.size && prefixBytes.indices.all { this[it] == prefixBytes[it] }
    }
}

/** One row of an export, whichever format it came from: [raw] returns a field's value (a
 * String, Number, Boolean, JSONArray, or null when missing/blank). [where] locates the row for
 * error messages. */
internal class ImportRecord(private val where: String, private val raw: (String) -> Any?) {
    fun fail(problem: String): Nothing = throw ImportException("$where: $problem.")

    fun string(field: String): String? = raw(field)?.toString()

    fun requireString(field: String): String = string(field) ?: fail("\"$field\" is missing")

    fun long(field: String): Long? = when (val value = raw(field)) {
        null -> null
        is Number -> value.toLong()
        else -> value.toString().trim().toLongOrNull() ?: fail("\"$field\" should be a whole number, not \"$value\"")
    }

    fun requireLong(field: String): Long = long(field) ?: fail("\"$field\" is missing")

    fun int(field: String): Int? = long(field)?.let {
        if (it !in Int.MIN_VALUE..Int.MAX_VALUE) fail("\"$field\" is out of range")
        it.toInt()
    }

    fun bool(field: String): Boolean? = when (val value = raw(field)) {
        null -> null
        is Boolean -> value
        else -> when (value.toString().trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> fail("\"$field\" should be true or false, not \"$value\"")
        }
    }

    /** A JSON array in a JSON export, or "1|3|5" in a CSV one (see CsvExporter). */
    fun intList(field: String): List<Int>? = when (val value = raw(field)) {
        null -> null
        is JSONArray -> (0 until value.length()).map { index ->
            (value.opt(index) as? Number)?.toInt() ?: fail("\"$field\" should only contain numbers")
        }
        else -> value.toString().split('|').map { part ->
            part.trim().toIntOrNull() ?: fail("\"$field\" should only contain numbers, not \"$value\"")
        }
    }
}

/** RFC 4180 CSV: quoted fields may contain commas, line breaks, and "" as an escaped quote.
 * Blank lines are dropped. */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false

    fun endField() {
        row += field.toString()
        field.setLength(0)
    }
    fun endRow() {
        endField()
        if (row.size > 1 || row[0].isNotEmpty()) rows += row
        row = mutableListOf()
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            inQuotes && c == '"' -> inQuotes = false
            inQuotes -> field.append(c)
            c == '"' && field.isEmpty() -> inQuotes = true
            c == ',' -> endField()
            c == '\r' && text.getOrNull(i + 1) == '\n' -> Unit // the \n ends the row
            c == '\n' || c == '\r' -> endRow()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

package com.tymed.app.importer

import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.entity.Schedule
import com.tymed.app.data.repository.ExportSettings
import com.tymed.app.data.repository.ExportSnapshot
import com.tymed.app.data.repository.ProfileExport
import com.tymed.app.data.repository.decodeDaysOfWeek
import com.tymed.app.data.repository.encodeDaysOfWeek
import com.tymed.app.export.CsvExporter
import com.tymed.app.export.JsonExporter
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportParserTest {
    private val profileExport = ProfileExport(
        profileName = "Me",
        medications = listOf(
            Medication(id = 7, profileId = 1, name = "Keppra", dosage = "500mg", form = "tablet", notes = "With food, \"not\" before bed", pillsRemaining = 30, refillThreshold = 5, createdAt = "2026-01-01T08:00:00Z"),
            Medication(id = 9, profileId = 1, name = "Vitamin D", createdAt = "2026-01-02T08:00:00Z"),
        ),
        schedules = listOf(
            Schedule(id = 3, medicationId = 7, timeOfDay = "08:00"),
            Schedule(id = 4, medicationId = 9, timeOfDay = "21:30", enabled = 0, daysOfWeek = encodeDaysOfWeek(listOf(1, 3, 5)), recurrenceType = RecurrenceType.WEEKLY, endDate = "2026-12-31"),
        ),
        intakeLogs = listOf(
            IntakeLog(id = 11, medicationId = 7, scheduleId = 3, scheduledDate = "2026-02-01", scheduledTime = "08:00", status = DoseStatus.TAKEN, takenAt = "2026-02-01T08:05:00Z"),
            IntakeLog(id = 12, medicationId = 7, scheduleId = 3, scheduledDate = "2026-02-02", scheduledTime = "08:00", status = DoseStatus.SKIPPED),
        ),
        incidents = listOf(
            Incident(id = 2, profileId = 1, type = "Seizure", startedAt = "2026-02-03T10:00:00Z", endedAt = "2026-02-03T10:01:30Z", severity = "mild", notes = "Line one\nline two, with a comma", createdAt = "2026-02-03T10:05:00Z"),
        ),
        settings = ExportSettings(followUpMinutes = 10, use24HourFormat = true, aiAssistantEnabled = false),
    )
    private val snapshot = ExportSnapshot(listOf(profileExport))

    private fun assertMatchesSnapshot(parsed: com.tymed.app.data.repository.ImportData) {
        val profile = parsed.profiles.single()
        assertEquals("Me", profile.profileName)
        assertEquals(profileExport.medications, profile.medications.map { it.copy(profileId = 1) })
        // notificationIds is a legacy column the exporters never write.
        assertEquals(profileExport.schedules.map { it.copy(notificationIds = null) }, profile.schedules)
        assertEquals(listOf(1, 3, 5), decodeDaysOfWeek(profile.schedules[1].daysOfWeek))
        assertEquals(profileExport.intakeLogs, profile.intakeLogs)
        assertEquals(profileExport.incidents, profile.incidents.map { it.copy(profileId = 1) })
    }

    @Test
    fun `round-trips a JSON export`() {
        assertMatchesSnapshot(ImportParser.parse(JsonExporter.render(snapshot).toByteArray()))
    }

    @Test
    fun `round-trips a CSV zip export, including quoted commas, quotes, and newlines`() {
        assertMatchesSnapshot(ImportParser.parse(CsvExporter.render(snapshot)))
    }

    @Test
    fun `round-trips an all-profiles export as separate sections`() {
        val other = profileExport.copy(
            profileName = "Someone Else",
            medications = listOf(Medication(id = 99, profileId = 2, name = "Metformin", createdAt = "2026-01-01T00:00:00Z")),
            schedules = emptyList(),
            intakeLogs = emptyList(),
            incidents = emptyList(),
        )
        val multi = ExportSnapshot(listOf(profileExport, other))

        val parsedJson = ImportParser.parse(JsonExporter.render(multi).toByteArray())
        assertEquals(setOf("Me", "Someone Else"), parsedJson.profiles.map { it.profileName }.toSet())
        assertEquals(1, parsedJson.profiles.single { it.profileName == "Someone Else" }.medications.size)

        val parsedCsv = ImportParser.parse(CsvExporter.render(multi))
        assertEquals(setOf("Me", "Someone Else"), parsedCsv.profiles.map { it.profileName }.toSet())
    }

    @Test
    fun `accepts a CSV zip whose files were re-zipped inside a folder, falling back to a default profile name`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("tymed-export/medications.csv"))
            zip.write("id,name\r\n1,Keppra\r\n".toByteArray())
            zip.closeEntry()
        }

        val parsed = ImportParser.parse(output.toByteArray())

        assertEquals("Keppra", parsed.profiles.single().medications.single().name)
    }

    @Test
    fun `rejects a PDF export with a pointer to the importable formats`() {
        val error = assertThrows(ImportException::class.java) { ImportParser.parse("%PDF-1.4 ...".toByteArray()) }
        assertTrue(error.message!!.contains("JSON or CSV"))
    }

    @Test
    fun `rejects JSON that isn't a TyMed export`() {
        assertThrows(ImportException::class.java) { ImportParser.parse("""{"hello": "world"}""".toByteArray()) }
        assertThrows(ImportException::class.java) { ImportParser.parse("not json at all".toByteArray()) }
    }

    @Test
    fun `rejects a malformed time of day, naming the offending row`() {
        val json = """{"profiles": [{"profileName": "Me", "medications": [{"id": 1, "name": "A"}], "schedules": [{"id": 1, "medicationId": 1, "timeOfDay": "8am"}]}]}"""

        val error = assertThrows(ImportException::class.java) { ImportParser.parse(json.toByteArray()) }

        assertTrue(error.message!!, error.message!!.startsWith("schedules entry 1"))
    }

    @Test
    fun `rejects an incident with an unparseable timestamp`() {
        val json = """{"profiles": [{"profileName": "Me", "medications": [], "incidents": [{"type": "Seizure", "startedAt": "yesterday", "endedAt": "2026-01-01T00:00:00Z"}]}]}"""

        assertThrows(ImportException::class.java) { ImportParser.parse(json.toByteArray()) }
    }

    @Test
    fun `rejects an unknown dose status`() {
        val json = """{"profiles": [{"profileName": "Me", "medications": [], "intakeLogs": [{"medicationId": 1, "scheduledDate": "2026-01-01", "scheduledTime": "08:00", "status": "maybe"}]}]}"""

        assertThrows(ImportException::class.java) { ImportParser.parse(json.toByteArray()) }
    }

    @Test
    fun `tolerates a UTF-8 byte order mark`() {
        val parsed = ImportParser.parse("﻿{\"profiles\": [{\"profileName\": \"Me\", \"medications\": [{\"id\": 1, \"name\": \"A\"}]}]}".toByteArray())

        assertEquals("A", parsed.profiles.single().medications.single().name)
    }

    @Test
    fun `parseCsv handles quoting, escaped quotes, embedded newlines, and blank lines`() {
        val rows = parseCsv("a,b,c\r\n\"x, y\",\"say \"\"hi\"\"\",\"line1\nline2\"\r\n\r\n1,,3")

        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("x, y", "say \"hi\"", "line1\nline2"), listOf("1", "", "3")),
            rows,
        )
    }
}

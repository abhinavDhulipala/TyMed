package com.tymed.app.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.tymed.app.data.repository.ExportSnapshot
import com.tymed.app.data.repository.ProfileExport
import java.io.ByteArrayOutputStream
import java.time.Instant

// A4 at 72dpi, matching PdfDocument's point-based page size.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val LINE_HEIGHT = 16f

/** Renders an [ExportSnapshot] as a printable, human-readable report, one section per profile —
 * meant for handing to a doctor or keeping as a paper record, not for re-importing. Built on
 * [PdfDocument] (part of the Android framework since API 19) rather than a third-party PDF
 * library. */
object PdfExporter {
    fun render(snapshot: ExportSnapshot): ByteArray {
        val profilePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 11f }

        val document = PdfDocument()
        val writer = PageWriter(document)

        writer.line("TyMed Data Export", titlePaint)
        writer.line("Generated ${Instant.now()}", bodyPaint)

        snapshot.profiles.forEach { profile ->
            writer.heading(profile.profileName, profilePaint)
            renderProfile(writer, profile, headingPaint, bodyPaint)
        }

        writer.finish()
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun renderProfile(writer: PageWriter, profile: ProfileExport, headingPaint: Paint, bodyPaint: Paint) {
        val medicationNameById = profile.medications.associateBy({ it.id }, { it.name })

        writer.heading("Medications", headingPaint)
        if (profile.medications.isEmpty()) writer.line("None recorded.", bodyPaint)
        profile.medications.forEach { med ->
            val details = listOfNotNull(med.dosage, med.form).joinToString(", ")
            writer.line("- ${med.name}${if (details.isNotEmpty()) " ($details)" else ""}", bodyPaint)
            med.notes?.takeIf { it.isNotBlank() }?.let { writer.line("    Notes: $it", bodyPaint) }
            med.pillsRemaining?.let { writer.line("    Pills remaining: $it", bodyPaint) }
        }

        writer.heading("Schedules", headingPaint)
        if (profile.schedules.isEmpty()) writer.line("None recorded.", bodyPaint)
        profile.schedules.forEach { sched ->
            val medName = medicationNameById[sched.medicationId] ?: "Unknown medication"
            val status = if (sched.enabled == 1) sched.recurrenceType else "${sched.recurrenceType}, disabled"
            writer.line("- $medName at ${sched.timeOfDay} ($status)", bodyPaint)
        }

        writer.heading("Dose history", headingPaint)
        if (profile.intakeLogs.isEmpty()) writer.line("None recorded.", bodyPaint)
        profile.intakeLogs.forEach { log ->
            val medName = medicationNameById[log.medicationId] ?: "Unknown medication"
            writer.line("- ${log.scheduledDate} ${log.scheduledTime} $medName: ${log.status}", bodyPaint)
        }

        writer.heading("Incidents", headingPaint)
        if (profile.incidents.isEmpty()) writer.line("None recorded.", bodyPaint)
        profile.incidents.forEach { incident ->
            val severity = incident.severity?.let { " ($it)" } ?: ""
            writer.line("- ${incident.startedAt} to ${incident.endedAt}: ${incident.type}$severity", bodyPaint)
            incident.notes?.takeIf { it.isNotBlank() }?.let { writer.line("    Notes: $it", bodyPaint) }
        }
    }

    /** Lays out lines top-to-bottom on a [PdfDocument], starting a new page whenever the next
     * line would run past the bottom margin. */
    private class PageWriter(private val document: PdfDocument) {
        private var pageNumber = 1
        private var page = startPage()
        private var y = MARGIN

        private fun startPage(): PdfDocument.Page =
            document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())

        private fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = startPage()
            y = MARGIN
        }

        fun heading(text: String, paint: Paint) {
            y += LINE_HEIGHT * 0.5f
            line(text, paint)
        }

        fun line(text: String, paint: Paint) {
            if (y + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) newPage()
            page.canvas.drawText(text, MARGIN, y, paint)
            y += LINE_HEIGHT
        }

        fun finish() = document.finishPage(page)
    }
}

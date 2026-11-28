package com.tymed.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// End dates are far in the future on purpose: formatFullDateLabel renders the current date as
// "today" (and the day before as "Yesterday"), so a near-term fixture date breaks on that day.
class MessagesTest {
    // Incidents are labelled with the local date they started on, so start at local noon: a fixed
    // UTC instant like 2099-09-30T12:00Z is already October 1st at UTC+12 and beyond.
    private val noonInstant = LocalDate.of(2099, 9, 30).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
    private val noon = noonInstant.toString()

    @Test
    fun `confirmationQuestion describes an add with its schedule`() {
        val result = ToolResult.AddMedicationOutcome(
            AddMedicationResult.NeedsConfirmation("Amoxicillin", "500 mg", listOf("08:00", "20:00"), "2099-09-30", emptyList()),
        )
        assertEquals(
            "Add Amoxicillin 500 mg at 8:00 AM and 8:00 PM every day through Wednesday, September 30?",
            confirmationQuestion(result),
        )
    }

    @Test
    fun `confirmationQuestion notes a similar existing medication`() {
        val result = ToolResult.AddMedicationOutcome(
            AddMedicationResult.NeedsConfirmation(
                "Aspirin",
                null,
                listOf("08:00"),
                null,
                listOf(SimilarMedication("Aspirin", "325mg", "tablet")),
            ),
        )
        assertEquals(
            "Add Aspirin at 8:00 AM every day? Note: you already track Aspirin 325mg — this would be a separate medication.",
            confirmationQuestion(result),
        )
    }

    @Test
    fun `confirmationQuestion describes a schedule change as before to after`() {
        val result = ToolResult.UpdateScheduleOutcome(
            UpdateScheduleResult.NeedsConfirmation(
                "Ibuprofen",
                ScheduleSummary(listOf("08:00"), null),
                ScheduleSummary(listOf("08:00", "20:00"), null),
            ),
        )
        assertEquals(
            "Change Ibuprofen from 8:00 AM every day to 8:00 AM and 8:00 PM every day?",
            confirmationQuestion(result),
        )
    }

    @Test
    fun `confirmationQuestion asks about marking a dose taken`() {
        val result = ToolResult.MarkDoseTakenOutcome(MarkDoseTakenResult.NeedsConfirmation("Aspirin", "08:00"))
        assertEquals("Mark your 8:00 AM Aspirin dose as taken?", confirmationQuestion(result))
    }

    @Test
    fun `confirmationQuestion reads back an incident with its notes`() {
        val summary = IncidentSummary("seizure", noon, noonInstant.plusSeconds(90).toString(), 90, "mild", "In the yard")
        val question = confirmationQuestion(ToolResult.LogIncidentOutcome(LogIncidentResult.NeedsConfirmation(summary)))!!
        assertTrue(question, question.startsWith("Log a mild seizure at "))
        assertTrue(question, question.contains(" on 2099-09-30, lasting 1m 30s?"))
        assertTrue(question, question.endsWith(" Notes: \"In the yard\"."))
    }

    @Test
    fun `confirmationQuestion is null for a result shape it does not recognize`() {
        assertNull(confirmationQuestion(ToolResult.Error("boom")))
        assertNull(confirmationQuestion(ToolResult.UpdateScheduleOutcome(UpdateScheduleResult.NoChange("Aspirin"))))
    }

    @Test
    fun `doneMessage reports an added medication`() {
        val result = ToolResult.AddMedicationOutcome(AddMedicationResult.Added("Amoxicillin", listOf("08:00", "20:00"), "2099-09-30"))
        assertEquals(
            "Done — added Amoxicillin at 8:00 AM and 8:00 PM every day through Wednesday, September 30.",
            doneMessage(result),
        )
    }

    @Test
    fun `doneMessage reports an updated schedule`() {
        val result = ToolResult.UpdateScheduleOutcome(
            UpdateScheduleResult.Updated("Ibuprofen", ScheduleSummary(listOf("08:00"), null), ScheduleSummary(listOf("08:00", "20:00"), null)),
        )
        assertEquals("Done — Ibuprofen is now at 8:00 AM and 8:00 PM every day.", doneMessage(result))
    }

    @Test
    fun `doneMessage reports a marked-taken dose`() {
        val result = ToolResult.MarkDoseTakenOutcome(MarkDoseTakenResult.MarkedTaken("Aspirin", "08:00"))
        assertEquals("Done — marked your 8:00 AM Aspirin dose as taken.", doneMessage(result))
    }

    @Test
    fun `doneMessage reports a logged incident`() {
        val summary = IncidentSummary("seizure", noon, noon, 0, null, null)
        val done = doneMessage(ToolResult.LogIncidentOutcome(LogIncidentResult.Logged(summary)))!!
        assertTrue(done, done.startsWith("Done — logged a seizure at "))
        assertTrue(done, done.endsWith(" on 2099-09-30."))
    }

    @Test
    fun `doneMessage is null when nothing was saved`() {
        assertNull(doneMessage(ToolResult.UpdateScheduleOutcome(UpdateScheduleResult.NoChange("Aspirin"))))
        assertNull(doneMessage(ToolResult.Error("boom")))
    }
}

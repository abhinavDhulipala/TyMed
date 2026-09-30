package com.tymed.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// End dates are far in the future on purpose: formatFullDateLabel renders the current date as
// "today" (and the day before as "Yesterday"), so a near-term fixture date breaks on that day.
class MessagesTest {

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
    fun `doneMessage is null when nothing was saved`() {
        assertNull(doneMessage(ToolResult.UpdateScheduleOutcome(UpdateScheduleResult.NoChange("Aspirin"))))
        assertNull(doneMessage(ToolResult.Error("boom")))
    }
}

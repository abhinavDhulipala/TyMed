package com.tymed.app.ai

import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.ActiveProfile
import com.tymed.app.data.repository.MedicationInput
import com.tymed.app.data.repository.MedicationRepository
import com.tymed.app.data.repository.TEST_PROFILE_ID
import com.tymed.app.data.repository.newInMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The orchestrator's own logic — confirmation handling, loop guards — against a scripted fake
// model and a scripted fake tool runner. How the real Gemini Nano behaves is covered by manual
// on-device testing (there's no off-device equivalent for that).
@RunWith(RobolectricTestRunner::class)
class AiOrchestratorTest {
    private lateinit var client: FakeAiClient
    private lateinit var toolRunner: FakeToolRunner
    private lateinit var orchestrator: AiOrchestrator

    private val addArgs = mapOf("name" to "Amoxicillin", "dosage" to "500 mg", "times" to listOf("08:00", "20:00"), "durationDays" to 7)
    private val addPending = ToolResult.AddMedicationOutcome(
        AddMedicationResult.NeedsConfirmation("Amoxicillin", "500 mg", listOf("08:00", "20:00"), "2099-09-30", emptyList()),
    )
    private val addDone = ToolResult.AddMedicationOutcome(AddMedicationResult.Added("Amoxicillin", listOf("08:00", "20:00"), "2099-09-30"))

    private fun toolCall(tool: String, args: Map<String, Any?>) =
        org.json.JSONObject().put("tool", tool).put("arguments", mapToJson(args)).toString()

    private fun reply(text: String) = org.json.JSONObject().put("reply", text).toString()

    @Before
    fun setUp() {
        client = FakeAiClient()
        toolRunner = FakeToolRunner()
        val medicationRepository = MedicationRepository(newInMemoryDatabase().medicationDao())
        val activeProfile = ActiveProfile(ApplicationProvider.getApplicationContext()).apply { switchTo(TEST_PROFILE_ID) }
        orchestrator = AiOrchestrator(activeProfile, medicationRepository, toolRunner, client)
        runTest { medicationRepository.createMedication(TEST_PROFILE_ID, MedicationInput("Ibuprofen", "200 mg", null, null, null, null)) }
    }

    @Test
    fun `asks a templated question for needs_confirmation and remembers the call as pending`() = runTest {
        client.willReturnOnce(toolCall("add_medication", addArgs))
        toolRunner.willReturnOnce(addPending)

        val result = orchestrator.runTurn("add amoxicillin 500 mg at 8am and 8pm for 7 days", emptyList(), null)

        assertEquals(1, client.callCount)
        assertTrue(result.reply.startsWith("Add Amoxicillin 500 mg at 8:00 AM and 8:00 PM every day through"))
        assertTrue(result.reply.endsWith("?"))
        assertEquals(PendingAction("add_medication", addArgs), result.pending)
    }

    @Test
    fun `applies the pending call with confirmed true on a yes without calling the model`() = runTest {
        toolRunner.willReturnOnce(addDone)

        val result = orchestrator.runTurn("Yes", emptyList(), PendingAction("add_medication", addArgs))

        assertEquals(1, toolRunner.calls.size)
        assertEquals("add_medication" to (addArgs + ("confirmed" to true)), toolRunner.calls.single())
        assertEquals(0, client.callCount)
        assertTrue(result.reply.startsWith("Done — added Amoxicillin at 8:00 AM and 8:00 PM"))
        assertNull(result.pending)
    }

    @Test
    fun `lets the model explain when the confirmed call fails`() = runTest {
        toolRunner.willReturnOnce(ToolResult.Error("add_medication requires a non-empty name"))
        client.willReturnOnce(reply("Sorry, that medication needs a name."))

        val result = orchestrator.runTurn("yes", emptyList(), PendingAction("add_medication", addArgs))

        assertEquals("Sorry, that medication needs a name.", result.reply)
    }

    @Test
    fun `describes a schedule change as before to after`() = runTest {
        client.willReturnOnce(toolCall("update_medication_schedule", mapOf("medicationName" to "Ibuprofen", "times" to listOf("08:00", "20:00"))))
        toolRunner.willReturnOnce(
            ToolResult.UpdateScheduleOutcome(
                UpdateScheduleResult.NeedsConfirmation(
                    "Ibuprofen",
                    ScheduleSummary(listOf("08:00"), null),
                    ScheduleSummary(listOf("08:00", "20:00"), null),
                ),
            ),
        )

        val result = orchestrator.runTurn("make ibuprofen twice a day", emptyList(), null)

        assertEquals("Change Ibuprofen from 8:00 AM every day to 8:00 AM and 8:00 PM every day?", result.reply)
    }

    @Test
    fun `drops a durationDays the model invented when the user never mentioned a duration`() = runTest {
        client.willReturnOnce(
            toolCall("update_medication_schedule", mapOf("medicationName" to "Ibuprofen", "times" to listOf("08:00", "20:00"), "durationDays" to 7)),
        )
        toolRunner.willReturnOnce(
            ToolResult.UpdateScheduleOutcome(
                UpdateScheduleResult.NeedsConfirmation("Ibuprofen", ScheduleSummary(listOf("08:00"), null), ScheduleSummary(listOf("08:00", "20:00"), null)),
            ),
        )

        orchestrator.runTurn("make ibuprofen twice a day at 8am and 8pm", emptyList(), null)

        assertEquals(mapOf("medicationName" to "Ibuprofen", "times" to listOf("08:00", "20:00")), toolRunner.calls.single().second)
    }

    @Test
    fun `keeps durationDays when the user did mention a duration`() = runTest {
        client.willReturnOnce(
            toolCall("update_medication_schedule", mapOf("medicationName" to "Ibuprofen", "times" to listOf("08:00", "20:00"), "durationDays" to 7)),
        )
        toolRunner.willReturnOnce(addPending)

        orchestrator.runTurn("make ibuprofen twice a day for 7 days", emptyList(), null)

        assertEquals(
            mapOf("medicationName" to "Ibuprofen", "times" to listOf("08:00", "20:00"), "durationDays" to 7),
            toolRunner.calls.single().second,
        )
    }

    @Test
    fun `includes the tracked medications in the prompt`() = runTest {
        client.willReturnOnce(reply("Hi!"))
        orchestrator.runTurn("hello", emptyList(), null)
        assertTrue(client.prompts.single().contains("already tracks these medications: Ibuprofen 200 mg"))
    }

    @Test
    fun `drops the pending call on a no without calling the model or any tool`() = runTest {
        val result = orchestrator.runTurn("no thanks", emptyList(), PendingAction("add_medication", addArgs))

        assertNull(result.pending)
        assertTrue(result.reply.contains("won't"))
        assertEquals(0, client.callCount)
        assertEquals(0, toolRunner.calls.size)
    }

    @Test
    fun `hands a qualified answer back to the model and forgets the stale pending call`() = runTest {
        client.willReturnOnce(reply("What time instead?"))

        val result = orchestrator.runTurn("yes but make it 9pm", emptyList(), PendingAction("add_medication", addArgs))

        assertEquals(0, toolRunner.calls.size)
        assertNull(result.pending)
    }

    @Test
    fun `strips confirmed true from model tool calls so the model can never confirm on its own`() = runTest {
        client.willReturnOnce(toolCall("mark_dose_taken", mapOf("medicationName" to "Aspirin", "confirmed" to true)))
        toolRunner.willReturnOnce(ToolResult.MarkDoseTakenOutcome(MarkDoseTakenResult.NeedsConfirmation("Aspirin", "08:00")))

        val result = orchestrator.runTurn("I took my aspirin", emptyList(), null)

        assertEquals(mapOf("medicationName" to "Aspirin"), toolRunner.calls.single().second)
        assertEquals("Mark your 8:00 AM Aspirin dose as taken?", result.reply)
    }

    @Test
    fun `pushes back once when the model claims a write without a saved tool result`() = runTest {
        client.willReturnOnce(reply("Okay, I've marked your aspirin dose as taken."))
        client.willReturnOnce(toolCall("mark_dose_taken", mapOf("medicationName" to "aspirin")))
        toolRunner.willReturnOnce(ToolResult.MarkDoseTakenOutcome(MarkDoseTakenResult.NeedsConfirmation("Aspirin", "08:00")))

        val result = orchestrator.runTurn("I just took my aspirin", emptyList(), null)

        assertTrue(client.prompts[1].contains("You have NOT changed anything"))
        assertEquals("Mark your 8:00 AM Aspirin dose as taken?", result.reply)
    }

    @Test
    fun `falls back rather than show a second unbacked claim`() = runTest {
        client.willAlwaysReturn(reply("I've added it for you."))

        val result = orchestrator.runTurn("add aspirin", emptyList(), null)

        assertTrue(result.reply.contains("didn't catch that"))
    }

    @Test
    fun `lets ordinary replies through`() = runTest {
        client.willReturnOnce(reply("You have aspirin at 8:00 AM left today."))
        val result = orchestrator.runTurn("what's left", emptyList(), null)
        assertEquals("You have aspirin at 8:00 AM left today.", result.reply)
    }

    @Test
    fun `stops with a fallback when the model repeats the same tool call`() = runTest {
        client.willAlwaysReturn(toolCall("get_todays_doses", emptyMap()))
        toolRunner.willReturnOnce(ToolResult.TodaysDosesOutcome(emptyList()))
        toolRunner.willReturnOnce(ToolResult.TodaysDosesOutcome(emptyList()))
        toolRunner.willReturnOnce(ToolResult.TodaysDosesOutcome(emptyList()))
        toolRunner.willReturnOnce(ToolResult.TodaysDosesOutcome(emptyList()))

        val result = orchestrator.runTurn("what's left today", emptyList(), null)

        assertTrue(result.reply.contains("didn't catch that"))
        assertEquals(1, toolRunner.calls.size)
    }

    @Test
    fun `retries once with a correction after unparseable output then falls back`() = runTest {
        client.willAlwaysReturn("not json at all")

        val result = orchestrator.runTurn("hello", emptyList(), null)

        assertEquals(2, client.callCount)
        assertTrue(result.reply.contains("didn't catch that"))
    }

    @Test
    fun `isAffirmative recognizes clear yes variants`() {
        for (text in listOf("Yes", "yes.", "Yep", "ok", "Sounds good!", "that's right", "go ahead")) {
            assertTrue("expected '$text' to be affirmative", isAffirmative(text))
        }
    }

    @Test
    fun `isAffirmative rejects qualified or unrelated answers`() {
        for (text in listOf("yes but 9pm", "no", "Yesterday I took it", "what?", "ok, change it to 9pm")) {
            assertTrue("expected '$text' to not be affirmative", !isAffirmative(text))
        }
    }

    @Test
    fun `isNegative recognizes clear no variants`() {
        for (text in listOf("No", "nope", "cancel", "never mind", "don't")) {
            assertTrue("expected '$text' to be negative", isNegative(text))
        }
    }
}

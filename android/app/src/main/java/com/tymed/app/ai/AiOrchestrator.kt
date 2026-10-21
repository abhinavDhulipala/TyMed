package com.tymed.app.ai

import com.tymed.app.data.ActiveProfile
import com.tymed.app.data.repository.MedicationRepository
import org.json.JSONObject

private const val MAX_ITERATIONS = 4
private const val FALLBACK_REPLY = "I didn't catch that — could you try rephrasing?"
private const val CANCELLED_REPLY = "Okay, I won't make that change."
private const val GENERATION_FAILED_REPLY = "Sorry, I couldn't respond to that. Could you try asking another way?"
private const val UNBACKED_CLAIM_CORRECTION =
    "You have NOT changed anything: no tool result in this turn says saved:true. Call the tool that does what the " +
        "user asked, or tell them you could not do it."

// Arguments only the app sets, never the model: confirmed is the user's yes (see [AiOrchestrator]),
// startedAt is log_incident's start pinned at question time (see [pinnedArgs]).
private val APP_ONLY_ARGS = setOf("confirmed", "startedAt")

// "I've marked…", "I added…" — a claim that something was written, which must be backed by a
// saved tool result this turn. Evals caught Gemini Nano replying "I've marked your aspirin dose
// as taken" without calling any tool.
private val WRITE_CLAIM = Regex("\\b(i'?ve|i have|i)\\s+(just\\s+|now\\s+)?(added|marked|updated|changed|saved|scheduled|set|removed)\\b", RegexOption.IGNORE_CASE)

// A digit before day(s)/week(s)/month(s) — "for 7 days", "in 2 weeks" — but not "every day" or
// "twice a day", which are frequency, not duration.
private val DURATION_MENTION = Regex("\\b\\d+\\s*-?\\s*(day|days|week|weeks|month|months)\\b", RegexOption.IGNORE_CASE)

private val AFFIRMATIVE = Regex(
    "^(y|yes|yeah|yep|yup|sure|ok|okay|confirm(ed)?|correct|right|do it|go ahead|please do|sounds (good|right)|that'?s (right|correct))\\b",
)
private val NEGATIVE = Regex("^(n|no|nope|nah|cancel|stop|don'?t|never ?mind)\\b")
private val QUALIFIER = Regex("\\b(but|no|not|don'?t|instead|change|except)\\b")

private fun normalize(message: String): String = message.trim().lowercase().replace(Regex("[.!]+$"), "")

fun isAffirmative(message: String): Boolean {
    val text = normalize(message)
    return AFFIRMATIVE.containsMatchIn(text) && !QUALIFIER.containsMatchIn(text)
}

fun isNegative(message: String): Boolean = NEGATIVE.containsMatchIn(normalize(message))

/** Strips a durationDays the user's own message doesn't support. On-device evals showed Gemini
 * Nano copying durationDays from the prompt's worked example even when nothing about how long the
 * change should run was said. */
private fun stripUnmentionedDuration(args: Map<String, Any?>, userMessage: String): Map<String, Any?> {
    if (!args.containsKey("durationDays") || DURATION_MENTION.containsMatchIn(userMessage)) return args
    return args - "durationDays"
}

/** A write the user has been asked to confirm — held by the app, not the model. Tied to the
 * profile it was asked under, so switching profiles before answering can't apply it to someone
 * else's medications. */
data class PendingAction(val name: String, val arguments: Map<String, Any?>, val profileId: Long)

data class RunTurnResult(val reply: String, val history: List<HistoryEntry>, val pending: PendingAction?)

private fun wasSaved(result: ToolResult): Boolean = doneMessage(result) != null

/** The pending call's arguments with whatever the confirmation question resolved pinned in, so a
 * "yes" applies exactly what was asked even if things moved in between: without this, a dose
 * marked taken from its notification meanwhile would make "mark aspirin taken" resolve to the
 * *next* pending dose, and an incident's "5 minutes ago" would be re-measured from the yes. */
private fun pinnedArgs(args: Map<String, Any?>, result: ToolResult): Map<String, Any?> = when (result) {
    is ToolResult.MarkDoseTakenOutcome -> (result.result as? MarkDoseTakenResult.NeedsConfirmation)
        ?.let { args + ("scheduledTime" to it.scheduledTime) } ?: args
    is ToolResult.LogIncidentOutcome -> (result.result as? LogIncidentResult.NeedsConfirmation)
        ?.let { args - "minutesAgo" + ("startedAt" to it.summary.startedAt) } ?: args
    else -> args
}

/** JSON text for a tool result as it appears in the transcript, with an explicit `saved` field
 * added the same way TS's annotateToolResult did — spelling out for the model, on later turns,
 * whether anything was actually written. */
private fun annotatedToolResultText(result: ToolResult): String {
    val json = result.toJsonValue()
    if (json !is JSONObject || !json.has("status")) return json.toString()
    return when {
        json.optString("status") == "needs_confirmation" -> JSONObject(json.toString()).put("saved", false).toString()
        wasSaved(result) -> JSONObject(json.toString()).put("saved", true).toString()
        else -> json.toString()
    }
}

private fun toolCallText(name: String, arguments: Map<String, Any?>): String =
    JSONObject().put("tool", name).put("arguments", mapToJson(arguments)).toString()

/**
 * Drives one user message through the model, running any tool calls it makes and looping back
 * with the result, until it produces a direct reply. Bounded so a model stuck retrying the same
 * malformed call (or looping tool calls) can't hang the conversation.
 *
 * Confirmation is decided here, not by the model: the model's tool calls never carry
 * confirmed:true (it's stripped), a needs_confirmation result is remembered as `pending`, and
 * only the user's next message being a clear yes re-runs that exact call confirmed. Gemini Nano
 * doesn't reliably set the flag itself — and for health data it shouldn't be the one deciding.
 */
class AiOrchestrator(
    private val activeProfile: ActiveProfile,
    private val medicationRepository: MedicationRepository,
    private val tools: ToolRunner,
    private val client: AiClient,
) {
    suspend fun runTurn(userMessage: String, history: List<HistoryEntry>, pendingAction: PendingAction?): RunTurnResult {
        val profileId = activeProfile.current
        val pending = pendingAction?.takeIf { it.profileId == profileId }
        var workingHistory = history + HistoryEntry(HistoryRole.USER, userMessage)

        if (pending != null && isNegative(userMessage)) {
            workingHistory = workingHistory + HistoryEntry(HistoryRole.ASSISTANT, CANCELLED_REPLY)
            return RunTurnResult(CANCELLED_REPLY, workingHistory, null)
        }

        var savedThisTurn = false

        if (pending != null && isAffirmative(userMessage)) {
            val confirmedArgs = pending.arguments + ("confirmed" to true)
            val result = tools.runTool(pending.name, confirmedArgs)
            workingHistory = workingHistory +
                HistoryEntry(HistoryRole.ASSISTANT, toolCallText(pending.name, confirmedArgs)) +
                HistoryEntry(HistoryRole.TOOL, annotatedToolResultText(result))
            val done = doneMessage(result)
            if (done != null) {
                workingHistory = workingHistory + HistoryEntry(HistoryRole.ASSISTANT, done)
                return RunTurnResult(done, workingHistory, null)
            }
            // An error or an unexpected result — let the model explain it.
        }

        val medications = medicationRepository.listMedications(profileId).map { TrackedMedication(it.name, it.dosage) }
        var usedCorrectiveRetry = false
        var usedClaimRetry = false
        var lastToolCallSignature: String? = null

        repeat(MAX_ITERATIONS) {
            val prompt = buildPrompt(workingHistory, medications)
            val raw = try {
                client.generate(prompt)
            } catch (e: AiGenerationException) {
                // Unless something was saved this turn, drop the exchange entirely: a message the
                // safety filter blocked would otherwise be resent in every later prompt and get
                // the rest of the conversation blocked too.
                return if (savedThisTurn) {
                    RunTurnResult(GENERATION_FAILED_REPLY, workingHistory, null)
                } else {
                    RunTurnResult(GENERATION_FAILED_REPLY, history, pending)
                }
            }
            when (val turn = parseTurn(raw)) {
                is ParsedTurn.Reply -> {
                    if (!savedThisTurn && WRITE_CLAIM.containsMatchIn(turn.text)) {
                        if (usedClaimRetry) {
                            return RunTurnResult(FALLBACK_REPLY, workingHistory, null)
                        }
                        usedClaimRetry = true
                        workingHistory = workingHistory +
                            HistoryEntry(HistoryRole.ASSISTANT, JSONObject().put("reply", turn.text).toString()) +
                            HistoryEntry(HistoryRole.TOOL, UNBACKED_CLAIM_CORRECTION)
                        return@repeat
                    }
                    workingHistory = workingHistory + HistoryEntry(HistoryRole.ASSISTANT, turn.text)
                    return RunTurnResult(turn.text, workingHistory, null)
                }
                is ParsedTurn.Unparseable -> {
                    if (usedCorrectiveRetry) {
                        return RunTurnResult(FALLBACK_REPLY, workingHistory, null)
                    }
                    usedCorrectiveRetry = true
                    workingHistory = workingHistory + HistoryEntry(
                        HistoryRole.TOOL,
                        "Your last response was not valid JSON. Respond again with ONLY {\"tool\": ...} or {\"reply\": ...}.",
                    )
                    return@repeat
                }
                is ParsedTurn.Tool -> {
                    val rawArgs = turn.arguments - APP_ONLY_ARGS
                    val args = stripUnmentionedDuration(rawArgs, userMessage)

                    // A model retrying the exact same tool call after seeing its own result is
                    // stuck, not making progress — stop rather than burning the rest of the
                    // iteration budget on it.
                    val callSignature = "${turn.name}:${mapToJson(args)}"
                    if (callSignature == lastToolCallSignature) {
                        return RunTurnResult(FALLBACK_REPLY, workingHistory, null)
                    }
                    lastToolCallSignature = callSignature

                    val result = tools.runTool(turn.name, args)
                    savedThisTurn = savedThisTurn || wasSaved(result)
                    workingHistory = workingHistory +
                        HistoryEntry(HistoryRole.ASSISTANT, toolCallText(turn.name, args)) +
                        HistoryEntry(HistoryRole.TOOL, annotatedToolResultText(result))

                    // Every needs-confirmation result has a templated question, so having one is
                    // what makes this call pending.
                    val question = confirmationQuestion(result)
                    if (question != null) {
                        val nextPending = PendingAction(turn.name, pinnedArgs(args, result), profileId)
                        workingHistory = workingHistory + HistoryEntry(HistoryRole.ASSISTANT, question)
                        return RunTurnResult(question, workingHistory, nextPending)
                    }
                }
            }
        }

        return RunTurnResult(FALLBACK_REPLY, workingHistory, null)
    }
}

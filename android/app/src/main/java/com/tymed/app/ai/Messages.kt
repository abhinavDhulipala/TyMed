package com.tymed.app.ai

import com.tymed.app.util.formatFullDateLabel
import com.tymed.app.util.formatTime

/** The assistant's confirmation questions and "done" messages are rendered here from the tool
 * result, not phrased by the model: on-device evals showed Gemini Nano describing a
 * not-yet-saved change as done ("I've updated your schedule… is that correct?") and
 * sometimes skipping the question entirely. For health data the exact change being confirmed
 * must be stated accurately, so it's deterministic. */

private fun describeTimes(times: List<String>): String {
    val formatted = times.map(::formatTime)
    return if (formatted.size <= 2) {
        formatted.joinToString(" and ")
    } else {
        "${formatted.dropLast(1).joinToString(", ")} and ${formatted.last()}"
    }
}

private fun describeSchedule(schedule: ScheduleSummary): String {
    val label = schedule.endDate?.let(::formatFullDateLabel)
    val until = label?.let { " through ${if (it == "Today") "today" else it}" } ?: ""
    return "${describeTimes(schedule.times)} every day$until"
}

private fun nameWithDosage(name: String, dosage: String?): String = if (dosage != null) "$name $dosage" else name

/** The yes/no question for a needs-confirmation result, or null for a result shape it doesn't
 * apply to (the model phrases those instead). */
fun confirmationQuestion(result: ToolResult): String? = when (result) {
    is ToolResult.AddMedicationOutcome -> {
        val r = result.result
        if (r is AddMedicationResult.NeedsConfirmation) {
            val note = if (r.similar.isNotEmpty()) {
                " Note: you already track ${r.similar.joinToString(", ") { nameWithDosage(it.name, it.dosage) }} " +
                    "— this would be a separate medication."
            } else {
                ""
            }
            val schedule = describeSchedule(ScheduleSummary(r.times, r.endDate))
            "Add ${nameWithDosage(r.medicationName, r.dosage)} at $schedule?$note"
        } else if (r is AddMedicationResult.ExistingUpdated) {
            confirmationQuestion(ToolResult.UpdateScheduleOutcome(r.update))
        } else {
            null
        }
    }
    is ToolResult.UpdateScheduleOutcome -> {
        val r = result.result
        if (r is UpdateScheduleResult.NeedsConfirmation) {
            "Change ${r.medicationName} from ${describeSchedule(r.before)} to ${describeSchedule(r.after)}?"
        } else {
            null
        }
    }
    is ToolResult.MarkDoseTakenOutcome -> {
        val r = result.result
        if (r is MarkDoseTakenResult.NeedsConfirmation) {
            "Mark your ${formatTime(r.scheduledTime)} ${r.medicationName} dose as taken?"
        } else {
            null
        }
    }
    else -> null
}

/** The confirmation message after a write went through, or null if nothing was saved. */
fun doneMessage(result: ToolResult): String? = when (result) {
    is ToolResult.AddMedicationOutcome -> when (val r = result.result) {
        is AddMedicationResult.Added -> "Done — added ${r.medicationName} at ${describeSchedule(ScheduleSummary(r.times, r.endDate))}."
        is AddMedicationResult.ExistingUpdated -> doneMessage(ToolResult.UpdateScheduleOutcome(r.update))
        else -> null
    }
    is ToolResult.UpdateScheduleOutcome -> (result.result as? UpdateScheduleResult.Updated)?.let {
        "Done — ${it.medicationName} is now at ${describeSchedule(it.after)}."
    }
    is ToolResult.MarkDoseTakenOutcome -> (result.result as? MarkDoseTakenResult.MarkedTaken)?.let {
        "Done — marked your ${formatTime(it.scheduledTime)} ${it.medicationName} dose as taken."
    }
    else -> null
}

package com.tymed.app.ai

import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.DoseReminderParams
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.repository.DoseActions
import com.tymed.app.data.repository.IntakeLogRepository
import com.tymed.app.data.repository.MedicationInput
import com.tymed.app.data.repository.MedicationRepository
import com.tymed.app.data.repository.RecurrenceInput
import com.tymed.app.data.repository.ScheduleRepository
import com.tymed.app.data.repository.ScheduleSyncRepository
import com.tymed.app.data.repository.decodeDaysOfWeek
import com.tymed.app.util.todayDateString
import org.json.JSONObject
import java.time.LocalDate

private val DEFAULT_TIMES = listOf("08:00")
private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

private fun assertValidTimes(tool: String, times: List<String>) {
    for (time in times) {
        require(TIME_PATTERN.matches(time)) { "$tool: invalid time \"$time\", expected 24-hour \"HH:MM\"" }
    }
}

/** Last day (inclusive) of a course that starts today and runs for [days] days — computed here
 * rather than asking the model for an end date, since small on-device models are unreliable at
 * calendar arithmetic. */
fun endDateForDuration(days: Int, today: LocalDate = LocalDate.now()): String = todayDateString(today.plusDays((days - 1).toLong()))

data class ScheduleSummary(val times: List<String>, val endDate: String?)
data class SimilarMedication(val name: String, val dosage: String?, val form: String?)
data class TodaysDose(val medicationName: String, val dosage: String?, val scheduledTime: String, val status: String)

sealed interface AddMedicationResult {
    data class Exists(val medicationName: String) : AddMedicationResult
    /** An "add" for something already tracked that also names new times/duration is really a
     * schedule change — handled by the update path so the user gets one before/after
     * confirmation. */
    data class ExistingUpdated(val update: UpdateScheduleResult) : AddMedicationResult
    data class NeedsConfirmation(
        val medicationName: String,
        val dosage: String?,
        val times: List<String>,
        val endDate: String?,
        val similar: List<SimilarMedication>,
    ) : AddMedicationResult
    data class Added(val medicationName: String, val times: List<String>, val endDate: String?) : AddMedicationResult
}

sealed interface UpdateScheduleResult {
    data class NotFound(val medicationName: String, val medications: List<String>) : UpdateScheduleResult
    data class Ambiguous(val medicationName: String, val dosages: List<String?>) : UpdateScheduleResult
    data class NoChange(val medicationName: String) : UpdateScheduleResult
    data class NeedsConfirmation(val medicationName: String, val before: ScheduleSummary, val after: ScheduleSummary) : UpdateScheduleResult
    data class Updated(val medicationName: String, val before: ScheduleSummary, val after: ScheduleSummary) : UpdateScheduleResult
}

sealed interface MarkDoseTakenResult {
    data class NotFound(val medicationName: String, val todaysMedications: List<String>) : MarkDoseTakenResult
    data class Ambiguous(val medicationName: String, val candidateTimes: List<String>) : MarkDoseTakenResult
    data class NeedsConfirmation(val medicationName: String, val scheduledTime: String) : MarkDoseTakenResult
    data class MarkedTaken(val medicationName: String, val scheduledTime: String) : MarkDoseTakenResult
}

/** Mirrors the shape of runTool's dynamic "unknown" TS result as a closed set of Kotlin types. */
sealed interface ToolResult {
    data class Error(val message: String) : ToolResult
    data class AddMedicationOutcome(val result: AddMedicationResult) : ToolResult
    data class UpdateScheduleOutcome(val result: UpdateScheduleResult) : ToolResult
    data class TodaysDosesOutcome(val doses: List<TodaysDose>) : ToolResult
    data class MarkDoseTakenOutcome(val result: MarkDoseTakenResult) : ToolResult
}

val TOOL_DESCRIPTIONS: Map<String, String> = linkedMapOf(
    "add_medication" to (
        "add_medication(name: string, dosage?: string, times?: string[] [\"HH:MM\" 24h, default [\"08:00\"]], " +
            "durationDays?: number) — adds a NEW medication with daily reminders; \"twice a day\" means two times, " +
            "\"for 7 days\" means durationDays:7. If the result status is \"exists\", say it's already tracked. If the " +
            "result has existing:true, it was already tracked and this is a schedule change: describe the before/after."
        ),
    "update_medication_schedule" to (
        "update_medication_schedule(medicationName: string, dosage?: string, times?: string[] [\"HH:MM\" 24h, the " +
            "complete new set], durationDays?: number) — changes an EXISTING medication's reminder times and/or how " +
            "many more days it runs. If the user gives a frequency but no times, ask which times first. If " +
            "\"ambiguous\", ask which dosage. If \"not_found\", list the medications."
        ),
    "get_todays_doses" to "get_todays_doses() — returns every dose scheduled for today with its status.",
    "mark_dose_taken" to (
        "mark_dose_taken(medicationName: string, scheduledTime?: string [\"HH:MM\"]) — marks a pending dose as " +
            "taken. If the result status is \"not_found\", tell the user and list today's medications. If " +
            "\"ambiguous\", ask which time and re-call with scheduledTime set."
        ),
)

/** Seam between [AiOrchestrator] and tool execution, so tests can substitute a scripted fake
 * instead of running real repository/alarm code for every scenario. */
interface ToolRunner {
    suspend fun runTool(name: String, args: Map<String, Any?>): ToolResult
}

class Tools(
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeLogRepository: IntakeLogRepository,
    private val scheduleSyncRepository: ScheduleSyncRepository,
    private val alarmScheduler: AlarmScheduler,
    private val doseActions: DoseActions,
) : ToolRunner {

    /** Mirrors app/(tabs)/medications/new.tsx's submit flow: one createMedication, then one
     * createSchedule + scheduleDoseReminders per requested time, all on a fixed daily recurrence
     * — voice/chat add doesn't offer weekly/monthly recurrence in v1. Nothing is written until
     * the user has confirmed the exact summary returned as needsConfirmation. */
    suspend fun addMedication(
        name: String,
        dosage: String?,
        times: List<String>?,
        durationDays: Int?,
        confirmed: Boolean,
    ): AddMedicationResult {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "add_medication requires a non-empty name" }

        val resolvedTimes = times?.takeIf { it.isNotEmpty() } ?: DEFAULT_TIMES
        assertValidTimes("add_medication", resolvedTimes)
        val endDate = durationDays?.let { endDateForDuration(it) }

        val dosageTrimmed = dosage?.trim()?.takeIf { it.isNotEmpty() }
        val duplicate = medicationRepository.findDuplicateMedication(trimmedName, dosageTrimmed, null)

        if (duplicate.exact != null) {
            if (times.isNullOrEmpty() && durationDays == null) {
                return AddMedicationResult.Exists(duplicate.exact.name)
            }
            val update = updateMedicationSchedule(duplicate.exact.name, duplicate.exact.dosage, times, durationDays, confirmed)
            return AddMedicationResult.ExistingUpdated(update)
        }

        if (!confirmed) {
            return AddMedicationResult.NeedsConfirmation(
                medicationName = trimmedName,
                dosage = dosageTrimmed,
                times = resolvedTimes,
                endDate = endDate,
                similar = duplicate.partial.map { SimilarMedication(it.name, it.dosage, it.form) },
            )
        }

        val medicationId = medicationRepository.createMedication(
            MedicationInput(trimmedName, dosageTrimmed, null, null, null, null),
        )
        for (time in resolvedTimes) {
            val recurrence = RecurrenceInput(RecurrenceType.DAILY, null, null, endDate)
            val scheduleId = scheduleRepository.createSchedule(medicationId, time, recurrence)
            alarmScheduler.scheduleDoseReminders(
                DoseReminderParams(
                    scheduleId = scheduleId,
                    medicationId = medicationId,
                    medicationName = trimmedName,
                    dosage = dosageTrimmed,
                    timeOfDay = time,
                    recurrenceType = RecurrenceType.DAILY,
                    daysOfWeek = null,
                    startDate = null,
                    endDate = endDate,
                ),
            )
        }

        return AddMedicationResult.Added(trimmedName, resolvedTimes, endDate)
    }

    /** Changes an existing medication's reminder times and/or end date through the same
     * syncMedicationSchedules path the edit screen uses, so dose history for kept time slots
     * survives. Keeps the medication's current repeat pattern (daily/weekly/monthly) — only
     * the edit screen changes that. Gated on confirmed like every other write. */
    suspend fun updateMedicationSchedule(
        medicationName: String,
        dosage: String?,
        times: List<String>?,
        durationDays: Int?,
        confirmed: Boolean,
    ): UpdateScheduleResult {
        val name = medicationName.trim()
        require(name.isNotEmpty()) { "update_medication_schedule requires a medicationName" }
        require(!(times.isNullOrEmpty() && durationDays == null)) {
            "update_medication_schedule needs times and/or durationDays to change"
        }

        val all = medicationRepository.listMedications()
        val sameName = all.filter { it.name.trim().equals(name, ignoreCase = true) }
        if (sameName.isEmpty()) {
            return UpdateScheduleResult.NotFound(name, all.map { it.name })
        }
        val wantedDosage = dosage?.trim()?.lowercase()
        val matches = if (sameName.size > 1 && wantedDosage != null) {
            sameName.filter { (it.dosage ?: "").trim().lowercase() == wantedDosage }
        } else {
            sameName
        }
        if (matches.size != 1) {
            return UpdateScheduleResult.Ambiguous(name, sameName.map { it.dosage })
        }
        val medication = matches.first()

        val schedules = scheduleRepository.listSchedulesForMedication(medication.id)
        val first = schedules.firstOrNull()
        val before = ScheduleSummary(schedules.map { it.timeOfDay }.sorted(), first?.endDate)

        val nextTimes = times?.takeIf { it.isNotEmpty() }?.distinct()?.sorted() ?: before.times
        assertValidTimes("update_medication_schedule", nextTimes)
        val after = ScheduleSummary(nextTimes, durationDays?.let { endDateForDuration(it) } ?: before.endDate)

        if (after.endDate == before.endDate && after.times == before.times) {
            return UpdateScheduleResult.NoChange(medication.name)
        }
        if (!confirmed) {
            return UpdateScheduleResult.NeedsConfirmation(medication.name, before, after)
        }

        scheduleSyncRepository.syncMedicationSchedules(
            medicationId = medication.id,
            medicationName = medication.name,
            medicationDosage = medication.dosage,
            times = after.times,
            recurrenceType = first?.recurrenceType ?: RecurrenceType.DAILY,
            daysOfWeek = first?.daysOfWeek?.let { decodeDaysOfWeek(it) },
            endDate = after.endDate,
        )
        return UpdateScheduleResult.Updated(medication.name, before, after)
    }

    suspend fun getTodaysDoses(): List<TodaysDose> =
        intakeLogRepository.getDosesForDate(todayDateString()).map {
            TodaysDose(it.medicationName, it.dosage, it.log.scheduledTime, it.log.status)
        }

    /** Never calls setLogStatus directly — DoseActions.markDose is what keeps
     * pills_remaining in sync. Requires an explicit confirmed before the actual write (even for
     * an unambiguous single match): a misparsed voice/chat command silently marking a dose taken
     * has real double-dosing risk. */
    suspend fun markDoseTaken(medicationName: String, scheduledTime: String?, confirmed: Boolean): MarkDoseTakenResult {
        val name = medicationName.trim()
        require(name.isNotEmpty()) { "mark_dose_taken requires a medicationName" }

        val doses = intakeLogRepository.getDosesForDate(todayDateString())
        val pending = doses.filter { it.log.status == DoseStatus.PENDING }

        val normalized = name.lowercase()
        var matches = pending.filter { it.medicationName.lowercase() == normalized }

        if (matches.isEmpty()) {
            return MarkDoseTakenResult.NotFound(name, doses.map { it.medicationName }.distinct())
        }

        if (matches.size > 1 && scheduledTime != null) {
            val narrowed = matches.filter { it.log.scheduledTime == scheduledTime }
            if (narrowed.size == 1) matches = narrowed
        }

        if (matches.size > 1) {
            return MarkDoseTakenResult.Ambiguous(name, matches.map { it.log.scheduledTime })
        }

        val match = matches.first()
        if (!confirmed) {
            return MarkDoseTakenResult.NeedsConfirmation(match.medicationName, match.log.scheduledTime)
        }

        doseActions.markDose(match.log.id, DoseStatus.TAKEN)
        return MarkDoseTakenResult.MarkedTaken(match.medicationName, match.log.scheduledTime)
    }

    /** Runs a tool call and always resolves — a bad tool name or invalid arguments becomes
     * an [ToolResult.Error] fed back to the model instead of throwing, so a single malformed
     * model response can't crash the whole conversation turn. */
    override suspend fun runTool(name: String, args: Map<String, Any?>): ToolResult =
        try {
            when (name) {
                "add_medication" -> ToolResult.AddMedicationOutcome(
                    addMedication(
                        name = args["name"] as? String ?: "",
                        dosage = args["dosage"] as? String,
                        times = stringList(args["times"]),
                        durationDays = intArg(args["durationDays"]),
                        confirmed = args["confirmed"] == true,
                    ),
                )
                "update_medication_schedule" -> ToolResult.UpdateScheduleOutcome(
                    updateMedicationSchedule(
                        medicationName = args["medicationName"] as? String ?: "",
                        dosage = args["dosage"] as? String,
                        times = stringList(args["times"]),
                        durationDays = intArg(args["durationDays"]),
                        confirmed = args["confirmed"] == true,
                    ),
                )
                "get_todays_doses" -> ToolResult.TodaysDosesOutcome(getTodaysDoses())
                "mark_dose_taken" -> ToolResult.MarkDoseTakenOutcome(
                    markDoseTaken(
                        medicationName = args["medicationName"] as? String ?: "",
                        scheduledTime = args["scheduledTime"] as? String,
                        confirmed = args["confirmed"] == true,
                    ),
                )
                else -> ToolResult.Error("Unknown tool \"$name\"")
            }
        } catch (error: Exception) {
            ToolResult.Error(error.message ?: "Tool call failed")
        }

    private fun stringList(value: Any?): List<String>? = (value as? List<*>)?.filterIsInstance<String>()

    private fun intArg(value: Any?): Int? = when (value) {
        is Int -> value.takeIf { it > 0 }
        is Double -> value.toInt().takeIf { it > 0 && value == value.toInt().toDouble() }
        is Long -> value.toInt().takeIf { it > 0 }
        else -> null
    }
}

/** JSON serialization of a [ToolResult]'s raw payload, for embedding in the prompt transcript as
 * `Tool result: ...`. Returns a [JSONObject] for every tool except get_todays_doses, which is a
 * bare [org.json.JSONArray] — matching the exact wire shape the prompt's few-shot example
 * shows the model (`Tool result: [{"medicationName": ...}, ...]`), not an object wrapping it. */
fun ToolResult.toJsonValue(): Any = when (this) {
    is ToolResult.Error -> JSONObject().put("error", message)
    is ToolResult.TodaysDosesOutcome -> org.json.JSONArray(
        doses.map {
            JSONObject()
                .put("medicationName", it.medicationName)
                .put("dosage", it.dosage)
                .put("scheduledTime", it.scheduledTime)
                .put("status", it.status)
        },
    )
    is ToolResult.AddMedicationOutcome -> addMedicationResultJson(result)
    is ToolResult.UpdateScheduleOutcome -> updateScheduleResultJson(result)
    is ToolResult.MarkDoseTakenOutcome -> markDoseTakenResultJson(result)
}

private fun scheduleSummaryJson(summary: ScheduleSummary): JSONObject =
    JSONObject().put("times", org.json.JSONArray(summary.times)).put("endDate", summary.endDate)

private fun addMedicationResultJson(result: AddMedicationResult): JSONObject = when (result) {
    is AddMedicationResult.Exists -> JSONObject().put("status", "exists").put("medicationName", result.medicationName)
    is AddMedicationResult.ExistingUpdated -> updateScheduleResultJson(result.update).put("existing", true)
    is AddMedicationResult.NeedsConfirmation -> JSONObject()
        .put("status", "needs_confirmation")
        .put("medicationName", result.medicationName)
        .put("dosage", result.dosage)
        .put("times", org.json.JSONArray(result.times))
        .put("endDate", result.endDate)
        .put(
            "similar",
            org.json.JSONArray(
                result.similar.map { JSONObject().put("name", it.name).put("dosage", it.dosage).put("form", it.form) },
            ),
        )
    is AddMedicationResult.Added -> JSONObject()
        .put("status", "added")
        .put("medicationName", result.medicationName)
        .put("times", org.json.JSONArray(result.times))
        .put("endDate", result.endDate)
}

private fun updateScheduleResultJson(result: UpdateScheduleResult): JSONObject = when (result) {
    is UpdateScheduleResult.NotFound -> JSONObject()
        .put("status", "not_found")
        .put("medicationName", result.medicationName)
        .put("medications", org.json.JSONArray(result.medications))
    is UpdateScheduleResult.Ambiguous -> JSONObject()
        .put("status", "ambiguous")
        .put("medicationName", result.medicationName)
        .put("dosages", org.json.JSONArray(result.dosages))
    is UpdateScheduleResult.NoChange -> JSONObject().put("status", "no_change").put("medicationName", result.medicationName)
    is UpdateScheduleResult.NeedsConfirmation -> JSONObject()
        .put("status", "needs_confirmation")
        .put("medicationName", result.medicationName)
        .put("before", scheduleSummaryJson(result.before))
        .put("after", scheduleSummaryJson(result.after))
    is UpdateScheduleResult.Updated -> JSONObject()
        .put("status", "updated")
        .put("medicationName", result.medicationName)
        .put("before", scheduleSummaryJson(result.before))
        .put("after", scheduleSummaryJson(result.after))
}

private fun markDoseTakenResultJson(result: MarkDoseTakenResult): JSONObject = when (result) {
    is MarkDoseTakenResult.NotFound -> JSONObject()
        .put("status", "not_found")
        .put("medicationName", result.medicationName)
        .put("todaysMedications", org.json.JSONArray(result.todaysMedications))
    is MarkDoseTakenResult.Ambiguous -> JSONObject()
        .put("status", "ambiguous")
        .put("medicationName", result.medicationName)
        .put(
            "candidates",
            org.json.JSONArray(result.candidateTimes.map { JSONObject().put("scheduledTime", it) }),
        )
    is MarkDoseTakenResult.NeedsConfirmation -> JSONObject()
        .put("status", "needs_confirmation")
        .put("medicationName", result.medicationName)
        .put("scheduledTime", result.scheduledTime)
    is MarkDoseTakenResult.MarkedTaken -> JSONObject()
        .put("status", "marked_taken")
        .put("medicationName", result.medicationName)
        .put("scheduledTime", result.scheduledTime)
}

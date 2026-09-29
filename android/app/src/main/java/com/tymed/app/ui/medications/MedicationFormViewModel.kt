package com.tymed.app.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.repository.MedicationInput
import com.tymed.app.data.repository.decodeDaysOfWeek
import com.tymed.app.util.todayDateString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

val ALL_DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

data class MedicationFormValues(
    val name: String = "",
    val dosage: String = "",
    val form: String = "",
    val notes: String = "",
    val times: List<String> = listOf("08:00"),
    val recurrenceType: String = RecurrenceType.DAILY,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5), // Mon-Fri default if switched to weekly
    val hasEndDate: Boolean = false,
    val endDate: String = todayDateString(LocalDate.now().plusDays(30)),
    val pillsRemaining: String = "",
    val refillThreshold: String = "",
)

data class MedicationFormUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val values: MedicationFormValues = MedicationFormValues(),
    val monthlyAnchorDay: Int? = null,
    val duplicateExactMessage: String? = null,
    val duplicatePartialNames: List<String>? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

class MedicationFormViewModel(
    private val container: AppContainer,
    private val medicationId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationFormUiState(isEdit = medicationId != null))
    val uiState: StateFlow<MedicationFormUiState> = _uiState

    init {
        viewModelScope.launch {
            if (medicationId != null) {
                val medication = container.medicationRepository.getMedication(medicationId)
                val schedules = container.scheduleRepository.listSchedulesForMedication(medicationId)
                if (medication != null) {
                    val first = schedules.firstOrNull()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            values = MedicationFormValues(
                                name = medication.name,
                                dosage = medication.dosage ?: "",
                                form = medication.form ?: "",
                                notes = medication.notes ?: "",
                                times = schedules.map { s -> s.timeOfDay }.ifEmpty { listOf("08:00") },
                                recurrenceType = first?.recurrenceType ?: RecurrenceType.DAILY,
                                daysOfWeek = first?.let { s -> decodeDaysOfWeek(s.daysOfWeek) }?.toSet() ?: setOf(1, 2, 3, 4, 5),
                                hasEndDate = first?.endDate != null,
                                endDate = first?.endDate ?: todayDateString(LocalDate.now().plusDays(30)),
                                pillsRemaining = medication.pillsRemaining?.toString() ?: "",
                                refillThreshold = medication.refillThreshold?.toString() ?: "",
                            ),
                            monthlyAnchorDay = first?.startDate?.let { s -> LocalDate.parse(s).dayOfMonth },
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun update(transform: (MedicationFormValues) -> MedicationFormValues) {
        _uiState.update { it.copy(values = transform(it.values), duplicateExactMessage = null, duplicatePartialNames = null) }
    }

    fun dismissDuplicateWarning() {
        _uiState.update { it.copy(duplicateExactMessage = null, duplicatePartialNames = null) }
    }

    fun submit(force: Boolean = false) {
        val values = _uiState.value.values
        viewModelScope.launch {
            if (!force) {
                val duplicate = container.medicationRepository.findDuplicateMedication(
                    name = values.name,
                    dosage = values.dosage.ifBlank { null },
                    form = values.form.ifBlank { null },
                    excludeId = medicationId,
                )
                if (duplicate.exact != null) {
                    _uiState.update { it.copy(duplicateExactMessage = "${values.name} is already in your list with the same dosage and form.") }
                    return@launch
                }
                if (duplicate.partial.isNotEmpty()) {
                    _uiState.update { it.copy(duplicatePartialNames = duplicate.partial.map { m -> m.name }) }
                    return@launch
                }
            }
            save(values)
        }
    }

    private suspend fun save(values: MedicationFormValues) {
        val input = MedicationInput(
            name = values.name,
            dosage = values.dosage.ifBlank { null },
            form = values.form.ifBlank { null },
            notes = values.notes.ifBlank { null },
            pillsRemaining = values.pillsRemaining.toIntOrNull(),
            refillThreshold = values.refillThreshold.toIntOrNull(),
        )
        val id = if (medicationId != null) {
            container.medicationRepository.updateMedication(medicationId, input)
            medicationId
        } else {
            container.medicationRepository.createMedication(input)
        }

        container.scheduleSyncRepository.syncMedicationSchedules(
            medicationId = id,
            medicationName = input.name,
            medicationDosage = input.dosage,
            times = values.times,
            recurrenceType = values.recurrenceType,
            daysOfWeek = if (values.recurrenceType == RecurrenceType.WEEKLY) values.daysOfWeek.toList() else null,
            endDate = if (values.hasEndDate) values.endDate else null,
        )

        _uiState.update { it.copy(saved = true) }
    }

    fun delete() {
        val id = medicationId ?: return
        viewModelScope.launch {
            val schedules = container.scheduleRepository.listSchedulesForMedication(id)
            for (schedule in schedules) {
                container.alarmScheduler.cancelDoseReminders(schedule.id)
            }
            container.medicationRepository.deleteMedication(id)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}

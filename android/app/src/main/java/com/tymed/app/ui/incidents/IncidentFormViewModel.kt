package com.tymed.app.ui.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.entity.durationSeconds
import com.tymed.app.data.repository.IncidentInput
import com.tymed.app.util.parseDateStr
import com.tymed.app.util.todayDateString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private fun nowTimeString(): String {
    val now = LocalTime.now()
    return "%02d:%02d".format(now.hour, now.minute)
}

data class IncidentFormValues(
    val type: String = "",
    val startDate: String = todayDateString(),
    val startTime: String = nowTimeString(),
    val durationMinutes: String = "0",
    val durationSeconds: String = "0",
    val severity: String = "",
    val notes: String = "",
)

data class IncidentFormUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val values: IncidentFormValues = IncidentFormValues(),
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

class IncidentFormViewModel(
    private val container: AppContainer,
    private val incidentId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IncidentFormUiState(isEdit = incidentId != null))
    val uiState: StateFlow<IncidentFormUiState> = _uiState

    init {
        viewModelScope.launch {
            if (incidentId != null) {
                val incident = container.incidentRepository.getIncident(incidentId)
                if (incident != null) {
                    val start = Instant.parse(incident.startedAt).atZone(ZoneId.systemDefault())
                    val totalSeconds = incident.durationSeconds()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            values = IncidentFormValues(
                                type = incident.type,
                                startDate = todayDateString(start.toLocalDate()),
                                startTime = "%02d:%02d".format(start.hour, start.minute),
                                durationMinutes = (totalSeconds / 60).toString(),
                                durationSeconds = (totalSeconds % 60).toString(),
                                severity = incident.severity ?: "",
                                notes = incident.notes ?: "",
                            ),
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun update(transform: (IncidentFormValues) -> IncidentFormValues) {
        _uiState.update { it.copy(values = transform(it.values)) }
    }

    fun submit() {
        val values = _uiState.value.values
        viewModelScope.launch {
            val start = toInstant(values.startDate, values.startTime)
            val totalSeconds = (values.durationMinutes.toLongOrNull() ?: 0) * 60 + (values.durationSeconds.toLongOrNull() ?: 0)
            val input = IncidentInput(
                type = values.type.trim(),
                startedAt = start.toString(),
                endedAt = start.plusSeconds(totalSeconds).toString(),
                severity = values.severity.ifBlank { null },
                notes = values.notes.ifBlank { null },
            )
            if (incidentId != null) {
                container.incidentRepository.updateIncident(incidentId, input)
            } else {
                container.incidentRepository.createIncident(input)
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = incidentId ?: return
        viewModelScope.launch {
            container.incidentRepository.deleteIncident(id)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    private fun toInstant(dateStr: String, timeStr: String): Instant {
        val date = parseDateStr(dateStr)
        val parts = timeStr.split(":")
        val time = LocalTime.of(parts[0].toInt(), parts[1].toInt())
        return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant()
    }
}

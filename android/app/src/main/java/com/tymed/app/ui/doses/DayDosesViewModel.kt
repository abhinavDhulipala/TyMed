package com.tymed.app.ui.doses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.dao.DoseWithMedication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Backs both the Today screen and the generic day-detail screen — "today" is just this called
 * with today's date; a day is a day, today isn't special. */
class DayDosesViewModel(private val container: AppContainer, private val dateStr: String) : ViewModel() {
    private val _doses = MutableStateFlow<List<DoseWithMedication>>(emptyList())
    val doses: StateFlow<List<DoseWithMedication>> = _doses

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _doses.value = container.intakeLogRepository.getDosesForDate(dateStr)
            _loading.value = false
        }
    }

    fun markDose(logId: Long, status: String) {
        viewModelScope.launch {
            container.doseActions.markDose(logId, status)
            refresh()
        }
    }
}

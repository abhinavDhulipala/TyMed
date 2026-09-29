package com.tymed.app.ui.doses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.dao.DoseWithMedication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DoseViewModel(private val container: AppContainer, private val logId: Long) : ViewModel() {
    private val _dose = MutableStateFlow<DoseWithMedication?>(null)
    val dose: StateFlow<DoseWithMedication?> = _dose

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _dose.value = container.intakeLogRepository.getDoseById(logId)
        }
    }

    fun markDose(status: String) {
        viewModelScope.launch {
            container.doseActions.markDose(logId, status)
            refresh()
        }
    }

    fun setTakenAt(isoDateTime: String) {
        viewModelScope.launch {
            container.intakeLogRepository.setLogTakenAt(logId, isoDateTime)
            refresh()
        }
    }
}

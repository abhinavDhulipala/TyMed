package com.tymed.app.ui.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.entity.Incident
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IncidentsListViewModel(private val container: AppContainer) : ViewModel() {
    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    val incidents: StateFlow<List<Incident>> = _incidents

    fun refresh() {
        viewModelScope.launch {
            _incidents.value = container.incidentRepository.listIncidents()
        }
    }

    /** Quick "Stop" action for an in-progress incident, straight from the list row. */
    fun endNow(incidentId: Long) {
        viewModelScope.launch {
            container.incidentRepository.endIncidentNow(incidentId)
            refresh()
        }
    }

    init {
        refresh()
    }
}

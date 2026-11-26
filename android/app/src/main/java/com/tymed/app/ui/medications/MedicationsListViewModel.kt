package com.tymed.app.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.entity.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MedicationsListViewModel(private val container: AppContainer) : ViewModel() {
    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications

    fun refresh(profileId: Long) {
        viewModelScope.launch {
            _medications.value = container.medicationRepository.listMedications(profileId)
        }
    }

    /** [days] null mutes until unmuted. */
    fun mute(medication: Medication, days: Int?) {
        viewModelScope.launch {
            container.muteActions.mute(medication.id, days)
            refresh(medication.profileId)
        }
    }

    fun unmute(medication: Medication) {
        viewModelScope.launch {
            container.muteActions.unmute(medication.id)
            refresh(medication.profileId)
        }
    }
}

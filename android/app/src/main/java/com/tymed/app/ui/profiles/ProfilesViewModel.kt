package com.tymed.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tymed.app.AppContainer
import com.tymed.app.data.entity.Profile
import com.tymed.app.data.entity.ProfileColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val deleteBlockedMessage: String? = null,
)

class ProfilesViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(profiles = container.profileRepository.listProfiles())
        }
    }

    fun addProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val nextColor = ProfileColors.forIndex(_uiState.value.profiles.size)
            val id = container.profileRepository.createProfile(trimmed, nextColor)
            refresh()
            container.activeProfile.switchTo(id)
        }
    }

    fun renameProfile(id: Long, name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            container.profileRepository.renameProfile(id, trimmed, colorHex)
            refresh()
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            val deleted = container.profileRepository.deleteProfile(id)
            if (!deleted) {
                _uiState.value = _uiState.value.copy(deleteBlockedMessage = "You need at least one profile.")
                return@launch
            }
            if (container.activeProfile.current == id) {
                val remaining = container.profileRepository.listProfiles()
                remaining.firstOrNull()?.let { container.activeProfile.switchTo(it.id) }
            }
            refresh()
        }
    }

    fun dismissDeleteBlockedMessage() {
        _uiState.value = _uiState.value.copy(deleteBlockedMessage = null)
    }
}

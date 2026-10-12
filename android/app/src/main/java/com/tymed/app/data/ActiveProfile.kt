package com.tymed.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "active_profile_prefs"
private const val PREF_ACTIVE_PROFILE_ID = "active_profile_id"

/** Observable "who's active right now" selection — same plain-`mutableStateOf` pattern as
 * [com.tymed.app.ui.AiAssistantPreference], so every screen reading [current] recomposes the
 * instant the top-bar switcher changes it, but backed by a tiny SharedPreferences file (not Room)
 * so the selection survives a process restart without needing a profile already loaded to read a
 * Room-backed setting. [TymedApp] reconciles [current] against the real profile list on launch
 * (it may point at NO_PROFILE on first run, or at a profile since deleted). */
class ActiveProfile(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var current: Long by mutableStateOf(prefs.getLong(PREF_ACTIVE_PROFILE_ID, NO_PROFILE))
        private set

    fun switchTo(profileId: Long) {
        current = profileId
        prefs.edit().putLong(PREF_ACTIVE_PROFILE_ID, profileId).apply()
    }

    companion object {
        const val NO_PROFILE = -1L
    }
}

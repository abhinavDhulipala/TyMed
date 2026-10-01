package com.tymed.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Mirrors SettingsRepository's `ai_assistant_enabled` flag as Compose state — unlike
 * [com.tymed.app.util.TimeFormatPreference]'s plain `var`, readers of [enabled] need to actually
 * recompose the instant the user flips the setting (to show/hide the Assistant bottom-nav tab),
 * not just pick up the latest value on some unrelated redraw. [TymedApp] loads the initial value
 * from the repository; [com.tymed.app.ui.settings.SettingsViewModel] writes through to it the
 * same way it writes through to `TimeFormatPreference`. */
object AiAssistantPreference {
    var enabled by mutableStateOf(false)
}

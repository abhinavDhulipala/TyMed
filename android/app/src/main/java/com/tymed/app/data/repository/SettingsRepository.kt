package com.tymed.app.data.repository

import com.tymed.app.data.dao.AppSettingDao
import com.tymed.app.data.entity.AppSettingEntity

object SettingsKeys {
    const val FOLLOW_UP_MINUTES = "follow_up_minutes"
    const val USE_24_HOUR_FORMAT = "use_24_hour_format"
    const val AI_ASSISTANT_ENABLED = "ai_assistant_enabled"
}

const val DEFAULT_FOLLOW_UP_MINUTES = 5

class SettingsRepository(private val dao: AppSettingDao) {
    private suspend fun getSetting(key: String): String? = dao.get(key)

    private suspend fun setSetting(key: String, value: String) = dao.upsert(AppSettingEntity(key, value))

    suspend fun getFollowUpMinutes(): Int {
        val raw = getSetting(SettingsKeys.FOLLOW_UP_MINUTES)?.toIntOrNull()
        return if (raw != null && raw > 0) raw else DEFAULT_FOLLOW_UP_MINUTES
    }

    suspend fun setFollowUpMinutes(minutes: Int) = setSetting(SettingsKeys.FOLLOW_UP_MINUTES, minutes.toString())

    suspend fun getUse24HourFormat(): Boolean = getSetting(SettingsKeys.USE_24_HOUR_FORMAT) == "1"

    suspend fun setUse24HourFormat(value: Boolean) = setSetting(SettingsKeys.USE_24_HOUR_FORMAT, if (value) "1" else "0")

    // Opt-in (default off): enabling it is the first time the app talks to anything beyond its
    // own local SQLite — even though inference stays on-device, the first prepare() may need to
    // download the model, so this shouldn't happen silently just because the user opened the
    // Assistant tab.
    suspend fun getAiAssistantEnabled(): Boolean = getSetting(SettingsKeys.AI_ASSISTANT_ENABLED) == "1"

    suspend fun setAiAssistantEnabled(value: Boolean) = setSetting(SettingsKeys.AI_ASSISTANT_ENABLED, if (value) "1" else "0")
}

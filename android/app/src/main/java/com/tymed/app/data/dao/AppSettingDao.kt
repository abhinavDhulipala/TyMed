package com.tymed.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tymed.app.data.entity.AppSettingEntity

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSettingEntity)
}

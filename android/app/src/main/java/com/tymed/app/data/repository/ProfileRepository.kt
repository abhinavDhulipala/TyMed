package com.tymed.app.data.repository

import com.tymed.app.data.dao.ProfileDao
import com.tymed.app.data.entity.Profile
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {
    suspend fun listProfiles(): List<Profile> = dao.getAll()

    fun observeProfiles(): Flow<List<Profile>> = dao.observeAll()

    suspend fun getProfile(id: Long): Profile? = dao.getById(id)

    suspend fun createProfile(name: String, colorHex: String): Long =
        dao.insert(Profile(name = name, colorHex = colorHex, createdAt = Instant.now().toString()))

    suspend fun renameProfile(id: Long, name: String, colorHex: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name, colorHex = colorHex))
    }

    /** Cascades (via the `profile_id` foreign keys on medications/incidents/app_settings, and
     * transitively through medications to schedules/intake_logs) to delete every bit of this
     * profile's data. Refuses to delete the last remaining profile — there must always be
     * someone active. */
    suspend fun deleteProfile(id: Long): Boolean {
        if (dao.count() <= 1) return false
        val existing = dao.getById(id) ?: return false
        dao.delete(existing)
        return true
    }
}

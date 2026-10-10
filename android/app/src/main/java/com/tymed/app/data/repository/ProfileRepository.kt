package com.tymed.app.data.repository

import com.tymed.app.data.ProfilePhotoStore
import com.tymed.app.data.dao.ProfileDao
import com.tymed.app.data.entity.Profile
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {
    suspend fun listProfiles(): List<Profile> = dao.getAll()

    fun observeProfiles(): Flow<List<Profile>> = dao.observeAll()

    suspend fun getProfile(id: Long): Profile? = dao.getById(id)

    suspend fun createProfile(name: String, colorHex: String, photoPath: String? = null): Long =
        dao.insert(Profile(name = name, colorHex = colorHex, photoPath = photoPath, createdAt = Instant.now().toString()))

    /** [photoPath] is the file [ProfilePhotoStore] already saved the new photo to (or null to
     * clear it) — if that's different from what's currently stored, the old file is deleted so
     * replacing/removing a photo doesn't leak files on disk. */
    suspend fun updateProfile(id: Long, name: String, colorHex: String, photoPath: String?) {
        val existing = dao.getById(id) ?: return
        if (existing.photoPath != null && existing.photoPath != photoPath) {
            ProfilePhotoStore.delete(existing.photoPath)
        }
        dao.update(existing.copy(name = name, colorHex = colorHex, photoPath = photoPath))
    }

    /** Cascades (via the `profile_id` foreign keys on medications/incidents/app_settings, and
     * transitively through medications to schedules/intake_logs) to delete every bit of this
     * profile's data. Refuses to delete the last remaining profile — there must always be
     * someone active. */
    suspend fun deleteProfile(id: Long): Boolean {
        if (dao.count() <= 1) return false
        val existing = dao.getById(id) ?: return false
        dao.delete(existing)
        existing.photoPath?.let { ProfilePhotoStore.delete(it) }
        return true
    }
}

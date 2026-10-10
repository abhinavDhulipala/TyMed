package com.tymed.app.data.repository

import com.tymed.app.data.dao.IncidentDao
import com.tymed.app.data.entity.Incident
import java.time.Instant

data class IncidentInput(
    val type: String,
    val startedAt: String,
    val endedAt: String,
    val severity: String?,
    val notes: String?,
)

class IncidentRepository(private val dao: IncidentDao) {
    suspend fun listIncidents(profileId: Long): List<Incident> = dao.getAllForProfile(profileId)

    /** Every incident across every profile, for a full "all profiles" data export. */
    suspend fun listAllIncidents(): List<Incident> = dao.getAll()

    suspend fun getIncident(id: Long): Incident? = dao.getById(id)

    suspend fun recentIncidents(profileId: Long, type: String? = null, limit: Int = 20): List<Incident> =
        if (type != null) dao.getByType(profileId, type, limit) else dao.getRecent(profileId, limit)

    suspend fun createIncident(profileId: Long, input: IncidentInput): Long =
        dao.insert(
            Incident(
                profileId = profileId,
                type = input.type,
                startedAt = input.startedAt,
                endedAt = input.endedAt,
                severity = input.severity,
                notes = input.notes,
                createdAt = Instant.now().toString(),
            ),
        )

    suspend fun updateIncident(id: Long, input: IncidentInput) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                type = input.type,
                startedAt = input.startedAt,
                endedAt = input.endedAt,
                severity = input.severity,
                notes = input.notes,
            ),
        )
    }

    suspend fun deleteIncident(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.delete(existing)
    }
}

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
    suspend fun listIncidents(): List<Incident> = dao.getAll()

    suspend fun getIncident(id: Long): Incident? = dao.getById(id)

    suspend fun recentIncidents(type: String? = null, limit: Int = 20): List<Incident> =
        if (type != null) dao.getByType(type, limit) else dao.getRecent(limit)

    suspend fun createIncident(input: IncidentInput): Long =
        dao.insert(
            Incident(
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

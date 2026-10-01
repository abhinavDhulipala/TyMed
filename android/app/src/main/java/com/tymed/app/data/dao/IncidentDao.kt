package com.tymed.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tymed.app.data.entity.Incident

@Dao
interface IncidentDao {
    @Insert
    suspend fun insert(incident: Incident): Long

    @Update
    suspend fun update(incident: Incident)

    @Delete
    suspend fun delete(incident: Incident)

    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun getById(id: Long): Incident?

    @Query("SELECT * FROM incidents ORDER BY started_at DESC")
    suspend fun getAll(): List<Incident>

    @Query("SELECT * FROM incidents ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<Incident>

    @Query("SELECT * FROM incidents WHERE type = :type ORDER BY started_at DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int): List<Incident>
}

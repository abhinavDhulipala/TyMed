package com.tymed.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tymed.app.data.entity.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun getAll(): List<Profile>

    /** Backs [TymedApp]'s top-level profile list so the UI never reads a stale snapshot — a
     * profile added/renamed/deleted from anywhere (e.g. the Profiles screen) is reflected the
     * instant the write commits, not just on the next cold load. */
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}

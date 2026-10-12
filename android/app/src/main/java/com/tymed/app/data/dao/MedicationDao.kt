package com.tymed.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tymed.app.data.entity.Medication

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Query("SELECT * FROM medications WHERE profile_id = :profileId ORDER BY name COLLATE NOCASE")
    suspend fun getAllForProfile(profileId: Long): List<Medication>

    /** Every medication across every profile, for a full "all profiles" data export. */
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Medication>

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE medications SET pills_remaining = pills_remaining - 1 " +
            "WHERE id = :id AND pills_remaining IS NOT NULL AND pills_remaining > 0",
    )
    suspend fun decrementPillCount(id: Long)

    @Query("UPDATE medications SET pills_remaining = pills_remaining + 1 WHERE id = :id AND pills_remaining IS NOT NULL")
    suspend fun incrementPillCount(id: Long)
}

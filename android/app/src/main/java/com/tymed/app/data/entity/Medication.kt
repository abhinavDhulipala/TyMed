package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profile_id"], name = "idx_medications_profile")],
)
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "dosage")
    val dosage: String? = null,
    @ColumnInfo(name = "form")
    val form: String? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "pills_remaining")
    val pillsRemaining: Int? = null,
    @ColumnInfo(name = "refill_threshold")
    val refillThreshold: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
)

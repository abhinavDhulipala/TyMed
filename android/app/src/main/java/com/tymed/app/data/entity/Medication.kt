package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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

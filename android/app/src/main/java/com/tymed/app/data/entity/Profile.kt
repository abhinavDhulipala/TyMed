package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One person sharing this device — each owns their own medications, schedules, dose history,
 * incidents, and app settings. [colorHex] picks a swatch from [ProfileColors.PALETTE] for the
 * initial-letter avatar bubble shown in the top bar switcher; there's no photo upload in v1. */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
)

/** The profile every pre-existing install's data is migrated under (see MIGRATION_7_8) — always
 * row id 1, since it's inserted first into an empty table. */
const val DEFAULT_PROFILE_ID = 1L

object ProfileColors {
    val PALETTE = listOf("#E07A5F", "#3D405B", "#81B29A", "#F2CC8F", "#5B8E7D", "#9B5DE5", "#4A7C96", "#C9594C")

    fun forIndex(index: Int): String = PALETTE[index.mod(PALETTE.size)]
}

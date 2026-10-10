package com.tymed.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One person sharing this device — each owns their own medications, schedules, dose history,
 * incidents, and app settings. [colorHex] picks a swatch from [ProfileColors.PALETTE] for the
 * initial-letter avatar bubble shown wherever [photoPath] is null (no photo set, or the file is
 * gone). [photoPath] is an absolute path to a downscaled JPEG copy this app made of whatever the
 * user picked — see [com.tymed.app.data.ProfilePhotoStore] — never a content:// Uri, since a
 * picker Uri's read grant isn't guaranteed to outlive the app session. */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "photo_path")
    val photoPath: String? = null,
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

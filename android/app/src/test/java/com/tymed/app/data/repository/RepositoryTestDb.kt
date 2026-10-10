package com.tymed.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.entity.Profile
import kotlinx.coroutines.runBlocking

/** Every repository test exercises this one profile — its id is fixed since [newInMemoryDatabase]
 * always seeds it first into an empty table. */
const val TEST_PROFILE_ID = 1L

/** A fresh in-memory Room database per call — Robolectric supplies the real SQLite
 * implementation these repository tests run real queries against. Seeded with one profile since
 * Room enforces the `profile_id` foreign keys on medications/incidents/app_settings. */
fun newInMemoryDatabase(): TymedDatabase {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TymedDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    runBlocking {
        db.profileDao().insert(Profile(name = "Test", colorHex = "#E07A5F", createdAt = "2026-01-01T00:00:00Z"))
    }
    return db
}

package com.tymed.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.TymedDatabase

/** A fresh in-memory Room database per call — Robolectric supplies the real SQLite
 * implementation these repository tests run real queries against. */
fun newInMemoryDatabase(): TymedDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TymedDatabase::class.java)
        .allowMainThreadQueries()
        .build()

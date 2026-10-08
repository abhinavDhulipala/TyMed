package com.tymed.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves the Room adoption strategy documented on [MIGRATION_4_5]: an existing install's on-disk
 * `tymed.db` — built by the old app's hand-written SQLite migrations, ending at
 * `PRAGMA user_version = 4` with no `room_master_table` — upgrades to Room's declared version 5
 * without `fallbackToDestructiveMigration`, and keeps every row intact. This is the concrete
 * regression test for "existing sideloaded users don't lose their medications on update."
 *
 * Builds the "legacy" database file by hand with the framework SQLite APIs directly (not through
 * Room at all) rather than via MigrationTestHelper, since MigrationTestHelper's createDatabase()
 * expects an *exported Room schema* for the starting version — but version 4 here was never a
 * Room version, it's the old expo-sqlite app's hand-written schema. Writing the file by hand is
 * also a more faithful simulation of the real scenario: a file dropped in place before Room ever
 * touches it — **at expo-sqlite's actual storage path** (`filesDir/SQLite/<name>`, not Room's
 * default `context.getDatabasePath()` location). An earlier version of this test used Room's
 * default path for both writing and reading the fake legacy file, which passed while the real
 * app quietly opened an empty database at the wrong path on every real device — the exact bug
 * this test exists to catch. See [TymedDatabase.databaseFile].
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val testDbName = "migration-test.db"

    private fun testDbFile(context: android.content.Context) = File(context.filesDir, "SQLite/$testDbName")

    @Test
    fun getInstanceOpensTheDatabaseAtExpoSqlitesHistoricalPath() {
        // The single most direct regression guard: whatever path TymedDatabase actually opens,
        // it must be filesDir/SQLite/tymed.db — not Room/Android's default `databases/` location
        // — because that's where every existing install's real data physically lives on disk.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("tymed.db")
        File(context.filesDir, "SQLite/tymed.db").delete()

        val db = TymedDatabase.getInstance(context)
        try {
            runBlocking { db.appSettingDao().get(1L, "__probe__") }
        } finally {
            db.close()
        }

        val expected = File(context.filesDir, "SQLite/tymed.db")
        assertTrue("expected a database file at ${expected.path}", expected.exists())
        assertTrue(
            "Room's default databases/ location should NOT be used",
            !context.getDatabasePath("tymed.db").exists(),
        )
    }

    @Test
    fun migrate4To5OnLegacySchemaPreservesExistingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = testDbFile(context)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // Exactly the end state of the old app's 001-004 migrations (see the deleted
        // src/db/migrations/*.ts, quoted verbatim in the rewrite plan).
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE medications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    dosage TEXT,
                    form TEXT,
                    notes TEXT,
                    pills_remaining INTEGER,
                    refill_threshold INTEGER,
                    created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
                    time_of_day TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    notification_ids TEXT,
                    days_of_week TEXT,
                    recurrence_type TEXT NOT NULL DEFAULT 'daily',
                    start_date TEXT,
                    end_date TEXT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE intake_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
                    schedule_id INTEGER REFERENCES schedules(id) ON DELETE CASCADE,
                    scheduled_date TEXT NOT NULL,
                    scheduled_time TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    taken_at TEXT
                )
                """.trimIndent(),
            )
            legacy.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            legacy.execSQL("CREATE INDEX idx_schedules_medication ON schedules(medication_id)")
            legacy.execSQL("CREATE INDEX idx_logs_schedule_date ON intake_logs(schedule_id, scheduled_date)")
            legacy.execSQL("CREATE INDEX idx_logs_date ON intake_logs(scheduled_date)")

            // Seed real rows so the test proves data survives, not just that the schema matches.
            legacy.execSQL(
                "INSERT INTO medications (id, name, dosage, form, notes, pills_remaining, refill_threshold, created_at) " +
                    "VALUES (1, 'Aspirin', '81mg', 'tablet', NULL, 30, 5, '2026-01-01T00:00:00.000Z')",
            )
            legacy.execSQL(
                "INSERT INTO schedules (id, medication_id, time_of_day, enabled, notification_ids, days_of_week, " +
                    "recurrence_type, start_date, end_date) VALUES (1, 1, '08:00', 1, NULL, NULL, 'daily', NULL, NULL)",
            )
            legacy.execSQL(
                "INSERT INTO intake_logs (id, medication_id, schedule_id, scheduled_date, scheduled_time, status, taken_at) " +
                    "VALUES (1, 1, 1, '2026-09-20', '08:00', 'taken', '2026-09-20T08:05:00.000Z')",
            )
            legacy.execSQL("INSERT INTO app_settings (key, value) VALUES ('use_24_hour_format', '1')")

            legacy.version = 4
        }

        // Opens the same file through Room at its exact absolute path — exactly what
        // TymedDatabase.getInstance() does in production — exercising the real
        // onUpgrade -> MIGRATION_4_5 -> MIGRATION_5_6 -> MIGRATION_6_7 -> onValidateSchema path,
        // which throws immediately if these entities don't structurally match what's actually on
        // disk. All three migrations are required here (not just MIGRATION_4_5) since the legacy
        // file is still at version 4 and the database's declared version has since moved to 7.
        val db = Room.databaseBuilder(context, TymedDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            runBlocking {
                val medication = db.medicationDao().getById(1)
                assertEquals("Aspirin", medication?.name)
                assertEquals("81mg", medication?.dosage)
                assertEquals(30, medication?.pillsRemaining)

                val schedule = db.scheduleDao().getById(1)
                assertEquals("08:00", schedule?.timeOfDay)
                assertEquals("daily", schedule?.recurrenceType)
                assertEquals(1, schedule?.enabled)

                val log = db.intakeLogDao().getById(1)
                assertEquals("taken", log?.status)
                assertEquals("2026-09-20T08:05:00.000Z", log?.takenAt)
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    /** Every real install is already at version 5 (there's no pre-Room version to simulate here,
     * unlike [migrate4To5OnLegacySchemaPreservesExistingData]) — writes exactly the schema
     * [MIGRATION_4_5] itself produces, then proves [MIGRATION_5_6] adds a working `incidents`
     * table on top of it without disturbing anything else. */
    @Test
    fun migrate5To6AddsIncidentsTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = testDbFile(context)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE medications (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL, dosage TEXT, form TEXT, notes TEXT,
                    pills_remaining INTEGER, refill_threshold INTEGER, created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE schedules (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL, time_of_day TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
                    notification_ids TEXT, days_of_week TEXT, recurrence_type TEXT NOT NULL DEFAULT 'daily',
                    start_date TEXT, end_date TEXT,
                    FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE intake_logs (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL, schedule_id INTEGER, scheduled_date TEXT NOT NULL,
                    scheduled_time TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', taken_at TEXT,
                    FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE,
                    FOREIGN KEY(schedule_id) REFERENCES schedules(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            legacy.execSQL("CREATE TABLE app_settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            legacy.execSQL("CREATE INDEX idx_schedules_medication ON schedules(medication_id)")
            legacy.execSQL("CREATE UNIQUE INDEX idx_logs_schedule_date ON intake_logs(schedule_id, scheduled_date)")
            legacy.execSQL("CREATE INDEX idx_logs_date ON intake_logs(scheduled_date)")

            legacy.execSQL(
                "INSERT INTO medications (id, name, dosage, form, notes, pills_remaining, refill_threshold, created_at) " +
                    "VALUES (1, 'Aspirin', '81mg', 'tablet', NULL, 30, 5, '2026-01-01T00:00:00.000Z')",
            )

            legacy.version = 5
        }

        val db = Room.databaseBuilder(context, TymedDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            runBlocking {
                val medication = db.medicationDao().getById(1)
                assertEquals("Aspirin", medication?.name)

                val incidentId = db.incidentDao().insert(
                    com.tymed.app.data.entity.Incident(
                        profileId = 1,
                        type = "Seizure",
                        startedAt = "2026-09-20T08:00:00Z",
                        endedAt = "2026-09-20T08:01:30Z",
                        severity = "mild",
                        notes = null,
                        createdAt = "2026-09-20T08:01:30Z",
                    ),
                )
                val incident = db.incidentDao().getById(incidentId)
                assertEquals("Seizure", incident?.type)
                assertEquals("2026-09-20T08:01:30Z", incident?.endedAt)
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    /** Proves [MIGRATION_6_7]'s backfill: a row written while `ended_at` could still be null (the
     * old "still ongoing" state) comes out the other side with `ended_at` set to its own
     * `started_at` — a zero-duration instant — rather than losing the row or leaving `ended_at`
     * null, which the now-non-nullable [com.tymed.app.data.entity.Incident.endedAt] can't hold. */
    @Test
    fun migrate6To7BackfillsNullEndedAt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = testDbFile(context)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE medications (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL, dosage TEXT, form TEXT, notes TEXT,
                    pills_remaining INTEGER, refill_threshold INTEGER, created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE schedules (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL, time_of_day TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
                    notification_ids TEXT, days_of_week TEXT, recurrence_type TEXT NOT NULL DEFAULT 'daily',
                    start_date TEXT, end_date TEXT,
                    FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE intake_logs (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL, schedule_id INTEGER, scheduled_date TEXT NOT NULL,
                    scheduled_time TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', taken_at TEXT,
                    FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE,
                    FOREIGN KEY(schedule_id) REFERENCES schedules(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            legacy.execSQL("CREATE TABLE app_settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            legacy.execSQL(
                """
                CREATE TABLE incidents (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL, started_at TEXT NOT NULL, ended_at TEXT,
                    severity TEXT, notes TEXT, created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL("CREATE INDEX idx_schedules_medication ON schedules(medication_id)")
            legacy.execSQL("CREATE UNIQUE INDEX idx_logs_schedule_date ON intake_logs(schedule_id, scheduled_date)")
            legacy.execSQL("CREATE INDEX idx_logs_date ON intake_logs(scheduled_date)")

            // The "still ongoing" row this migration needs to backfill.
            legacy.execSQL(
                "INSERT INTO incidents (id, type, started_at, ended_at, severity, notes, created_at) " +
                    "VALUES (1, 'Seizure', '2026-09-20T08:00:00Z', NULL, NULL, NULL, '2026-09-20T08:00:00Z')",
            )
            // An already-ended row, to prove the backfill doesn't touch it.
            legacy.execSQL(
                "INSERT INTO incidents (id, type, started_at, ended_at, severity, notes, created_at) " +
                    "VALUES (2, 'Vomiting', '2026-09-21T08:00:00Z', '2026-09-21T08:00:05Z', NULL, NULL, '2026-09-21T08:00:05Z')",
            )

            legacy.version = 6
        }

        val db = Room.databaseBuilder(context, TymedDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            runBlocking {
                val backfilled = db.incidentDao().getById(1)
                assertEquals("2026-09-20T08:00:00Z", backfilled?.endedAt)

                val untouched = db.incidentDao().getById(2)
                assertEquals("2026-09-21T08:00:05Z", untouched?.endedAt)
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    /** Proves [MIGRATION_7_8]'s multi-profile adoption: a single pre-existing install (no concept
     * of profiles at all) ends up with exactly one seeded "Me" profile, and every pre-existing
     * medication/incident/app_setting row is backfilled onto it. */
    @Test
    fun migrate7To8SeedsDefaultProfileAndBackfillsExistingRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = testDbFile(context)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE medications (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL, dosage TEXT, form TEXT, notes TEXT,
                    pills_remaining INTEGER, refill_threshold INTEGER, created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE schedules (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL, time_of_day TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
                    notification_ids TEXT, days_of_week TEXT, recurrence_type TEXT NOT NULL DEFAULT 'daily',
                    start_date TEXT, end_date TEXT,
                    FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                CREATE TABLE intake_logs (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    medication_id INTEGER NOT NULL, schedule_id INTEGER, scheduled_date TEXT NOT NULL,
                    scheduled_time TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', taken_at TEXT,
                    FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE,
                    FOREIGN KEY(schedule_id) REFERENCES schedules(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            legacy.execSQL("CREATE TABLE app_settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            legacy.execSQL(
                """
                CREATE TABLE incidents (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL, started_at TEXT NOT NULL, ended_at TEXT NOT NULL,
                    severity TEXT, notes TEXT, created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL("CREATE INDEX idx_schedules_medication ON schedules(medication_id)")
            legacy.execSQL("CREATE UNIQUE INDEX idx_logs_schedule_date ON intake_logs(schedule_id, scheduled_date)")
            legacy.execSQL("CREATE INDEX idx_logs_date ON intake_logs(scheduled_date)")

            legacy.execSQL(
                "INSERT INTO medications (id, name, dosage, form, notes, pills_remaining, refill_threshold, created_at) " +
                    "VALUES (1, 'Aspirin', '81mg', 'tablet', NULL, 30, 5, '2026-01-01T00:00:00.000Z')",
            )
            legacy.execSQL(
                "INSERT INTO incidents (id, type, started_at, ended_at, severity, notes, created_at) " +
                    "VALUES (1, 'Seizure', '2026-09-20T08:00:00Z', '2026-09-20T08:01:30Z', 'mild', NULL, '2026-09-20T08:01:30Z')",
            )
            legacy.execSQL("INSERT INTO app_settings (key, value) VALUES ('use_24_hour_format', '1')")

            legacy.version = 7
        }

        val db = Room.databaseBuilder(context, TymedDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_7_8)
            .build()
        try {
            runBlocking {
                val profiles = db.profileDao().getAll()
                assertEquals(1, profiles.size)
                assertEquals("Me", profiles.first().name)
                val defaultProfileId = profiles.first().id

                val medication = db.medicationDao().getById(1)
                assertEquals("Aspirin", medication?.name)
                assertEquals(defaultProfileId, medication?.profileId)

                val incident = db.incidentDao().getById(1)
                assertEquals("Seizure", incident?.type)
                assertEquals(defaultProfileId, incident?.profileId)

                assertEquals("1", db.appSettingDao().get(defaultProfileId, "use_24_hour_format"))
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}

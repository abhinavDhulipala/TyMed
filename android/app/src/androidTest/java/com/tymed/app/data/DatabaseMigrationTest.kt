package com.tymed.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

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
 * touches it.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val testDbName = "migration-test.db"

    @Test
    fun migrate4To5OnLegacySchemaPreservesExistingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = context.getDatabasePath(testDbName)
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

        // Opens the same file through Room, exactly as the app does on a real upgrade — this
        // exercises the real onUpgrade -> MIGRATION_4_5 -> onValidateSchema path, which throws
        // immediately if these entities don't structurally match what's actually on disk.
        val db = Room.databaseBuilder(context, TymedDatabase::class.java, testDbName)
            .addMigrations(MIGRATION_4_5)
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
            context.deleteDatabase(testDbName)
        }
    }
}

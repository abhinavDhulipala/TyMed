package com.tymed.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tymed.app.data.dao.AppSettingDao
import com.tymed.app.data.dao.IncidentDao
import com.tymed.app.data.dao.IntakeLogDao
import com.tymed.app.data.dao.MedicationDao
import com.tymed.app.data.dao.ScheduleDao
import com.tymed.app.data.entity.AppSettingEntity
import com.tymed.app.data.entity.Incident
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Medication
import com.tymed.app.data.entity.Schedule
import java.io.File

/**
 * Existing installs (from the old Expo/expo-sqlite build this app replaces) already have a
 * `tymed.db` on disk with `PRAGMA user_version = 4` — the end state of the four hand-written
 * migrations that used to live in src/db/migrations/. That file has no `room_master_table`
 * (Room-specific; the DB was never touched by Room), so declaring this database at version 4
 * would make Room think "already at the target version, nothing to migrate" and skip its
 * schema validation entirely — silently trusting a schema Room never actually checked. Any
 * mismatch would then only surface later as a runtime SQLiteException on some future query.
 *
 * Declaring the Room version one past that (5) with an explicit [MIGRATION_4_5] forces every
 * pre-existing install through Room's real upgrade path, which *does* validate the resulting
 * schema against these entities and throws immediately on first launch if anything doesn't
 * match — a loud failure instead of silent corruption. A fresh install has no user_version at
 * all, so Room just creates the schema directly from the entities at version 5; the migration
 * only matters for the upgrade path.
 *
 * The migration turns out not to be a no-op: SQLite reports `notnull=0` for a `PRIMARY KEY`
 * column that has no *explicit* `NOT NULL` keyword — true for every id/key column in the old
 * hand-written schema (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `key TEXT PRIMARY KEY`, neither
 * says `NOT NULL`) — even though such a column can in practice never actually hold NULL (SQLite
 * auto-assigns a rowid instead). Room's entities expect `NOT NULL` there (Kotlin's `Long`/
 * `String` id fields are non-null), so validation fails unless the columns are rebuilt with an
 * explicit `NOT NULL`. SQLite has no `ALTER TABLE ... ALTER COLUMN`, so each table is rebuilt via
 * the standard create-copy-drop-rename dance instead. See DatabaseMigrationTest for the adoption
 * test that caught this and proves the fixed migration preserves every row.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE medications_new (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, dosage TEXT, form TEXT, notes TEXT, " +
                "pills_remaining INTEGER, refill_threshold INTEGER, created_at TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO medications_new (id, name, dosage, form, notes, pills_remaining, refill_threshold, created_at) " +
                "SELECT id, name, dosage, form, notes, pills_remaining, refill_threshold, created_at FROM medications",
        )
        db.execSQL("DROP TABLE medications")
        db.execSQL("ALTER TABLE medications_new RENAME TO medications")

        db.execSQL(
            "CREATE TABLE schedules_new (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "medication_id INTEGER NOT NULL, time_of_day TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, " +
                "notification_ids TEXT, days_of_week TEXT, recurrence_type TEXT NOT NULL DEFAULT 'daily', " +
                "start_date TEXT, end_date TEXT, " +
                "FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE)",
        )
        db.execSQL(
            "INSERT INTO schedules_new (id, medication_id, time_of_day, enabled, notification_ids, days_of_week, " +
                "recurrence_type, start_date, end_date) " +
                "SELECT id, medication_id, time_of_day, enabled, notification_ids, days_of_week, " +
                "recurrence_type, start_date, end_date FROM schedules",
        )
        db.execSQL("DROP TABLE schedules")
        db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
        db.execSQL("CREATE INDEX idx_schedules_medication ON schedules(medication_id)")

        db.execSQL(
            "CREATE TABLE intake_logs_new (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "medication_id INTEGER NOT NULL, schedule_id INTEGER, scheduled_date TEXT NOT NULL, " +
                "scheduled_time TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', taken_at TEXT, " +
                "FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(schedule_id) REFERENCES schedules(id) ON DELETE CASCADE)",
        )
        // Unique index created *before* the copy (not after) so INSERT OR IGNORE below can
        // actually use it to drop duplicates — the old app had no such constraint, and a
        // since-fixed race in ensureLogsForDate's check-then-insert could have left a few
        // (schedule_id, scheduled_date) pairs duplicated on some installs. Ordering by "resolved
        // status first" means a real taken/skipped row wins over a stray pending duplicate
        // rather than being silently dropped.
        // SQLite index names are unique database-wide, not per-table — the legacy
        // `intake_logs` table (not yet dropped, its data is still needed for the copy below)
        // already has a same-named non-unique index, so it has to go first.
        db.execSQL("DROP INDEX idx_logs_schedule_date")
        db.execSQL("CREATE UNIQUE INDEX idx_logs_schedule_date ON intake_logs_new(schedule_id, scheduled_date)")
        db.execSQL(
            "INSERT OR IGNORE INTO intake_logs_new (id, medication_id, schedule_id, scheduled_date, scheduled_time, status, taken_at) " +
                "SELECT id, medication_id, schedule_id, scheduled_date, scheduled_time, status, taken_at FROM intake_logs " +
                "ORDER BY (status <> 'pending') DESC, id ASC",
        )
        db.execSQL("DROP TABLE intake_logs")
        db.execSQL("ALTER TABLE intake_logs_new RENAME TO intake_logs")
        db.execSQL("CREATE INDEX idx_logs_date ON intake_logs(scheduled_date)")

        db.execSQL("CREATE TABLE app_settings_new (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO app_settings_new (key, value) SELECT key, value FROM app_settings")
        db.execSQL("DROP TABLE app_settings")
        db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
    }
}

/** A brand-new table with no legacy on-disk data to preserve, so (unlike [MIGRATION_4_5]) a
 * plain `CREATE TABLE` is enough — no create-copy-drop-rename dance needed. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS incidents (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT NOT NULL, started_at TEXT NOT NULL, ended_at TEXT, " +
                "severity TEXT, notes TEXT, created_at TEXT NOT NULL)",
        )
    }
}

/** Incidents are always logged after the fact now — no more live "still ongoing" timer — so
 * `ended_at` goes from optional to required. Any row written while that was still possible
 * (`ended_at IS NULL`) is backfilled to `started_at`, i.e. treated as a zero-duration instant
 * rather than guessing at how long it actually lasted. SQLite has no `ALTER COLUMN`, so the
 * table is rebuilt via the same create-copy-drop-rename dance as [MIGRATION_4_5]. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE incidents_new (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT NOT NULL, started_at TEXT NOT NULL, ended_at TEXT NOT NULL, " +
                "severity TEXT, notes TEXT, created_at TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO incidents_new (id, type, started_at, ended_at, severity, notes, created_at) " +
                "SELECT id, type, started_at, COALESCE(ended_at, started_at), severity, notes, created_at FROM incidents",
        )
        db.execSQL("DROP TABLE incidents")
        db.execSQL("ALTER TABLE incidents_new RENAME TO incidents")
    }
}

@Database(
    entities = [Medication::class, Schedule::class, IntakeLog::class, AppSettingEntity::class, Incident::class],
    version = 7,
    exportSchema = true,
)
abstract class TymedDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun intakeLogDao(): IntakeLogDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun incidentDao(): IncidentDao

    companion object {
        @Volatile
        private var instance: TymedDatabase? = null

        /** Historically expo-sqlite's own storage convention (`FileSystem.documentDirectory +
         * "SQLite/"` on Android, i.e. `filesDir/SQLite/`), not Room/Android's default
         * `context.getDatabasePath()` location (`databases/`). This *must* stay exactly this
         * path: existing installs' real data lives here on disk, and Room silently creates a
         * fresh empty database with no error if it doesn't find a file at the path it's given —
         * getting this path wrong doesn't crash or fail loudly, it just quietly looks empty
         * while the real file sits untouched one directory over. */
        fun databaseFile(context: Context): File = File(context.filesDir, "SQLite/tymed.db")

        fun getInstance(context: Context): TymedDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): TymedDatabase {
            val dbFile = databaseFile(context)
            dbFile.parentFile?.mkdirs()
            return Room.databaseBuilder(context.applicationContext, TymedDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        }
    }
}

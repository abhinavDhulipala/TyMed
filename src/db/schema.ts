import type { SQLiteDatabase } from 'expo-sqlite';

const CURRENT_VERSION = 3;

export async function runMigrations(db: SQLiteDatabase): Promise<void> {
  await db.execAsync('PRAGMA journal_mode = WAL;');
  await db.execAsync('PRAGMA foreign_keys = ON;');

  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  const version = row?.user_version ?? 0;

  if (version < 1) {
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS medications (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        dosage TEXT,
        form TEXT,
        notes TEXT,
        pills_remaining INTEGER,
        refill_threshold INTEGER,
        created_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS schedules (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        medication_id INTEGER NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
        time_of_day TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        notification_ids TEXT
      );

      CREATE TABLE IF NOT EXISTS intake_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        medication_id INTEGER NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
        schedule_id INTEGER REFERENCES schedules(id) ON DELETE CASCADE,
        scheduled_date TEXT NOT NULL,
        scheduled_time TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'pending',
        taken_at TEXT
      );

      CREATE INDEX IF NOT EXISTS idx_schedules_medication ON schedules(medication_id);
      CREATE INDEX IF NOT EXISTS idx_logs_schedule_date ON intake_logs(schedule_id, scheduled_date);
      CREATE INDEX IF NOT EXISTS idx_logs_date ON intake_logs(scheduled_date);
    `);
  }

  if (version < 2) {
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS app_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);
  }

  if (version < 3) {
    // NULL means "every day" (the existing behavior for every schedule created before this
    // migration); a non-null value is a JSON array of 0=Sun..6=Sat weekday numbers.
    // Checked via table_info rather than assumed from version alone, so this step is safe to
    // re-run if a previous migration attempt bumped user_version without completing (SQLite has
    // no "ADD COLUMN IF NOT EXISTS").
    const columns = await db.getAllAsync<{ name: string }>('PRAGMA table_info(schedules)');
    if (!columns.some((c) => c.name === 'days_of_week')) {
      await db.execAsync(`ALTER TABLE schedules ADD COLUMN days_of_week TEXT;`);
    }
  }

  await db.execAsync(`PRAGMA user_version = ${CURRENT_VERSION}`);
}

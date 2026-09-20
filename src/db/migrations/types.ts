import type { SQLiteDatabase } from 'expo-sqlite';

export interface Migration {
  /** Matches PRAGMA user_version once applied. Sequential and permanent — once a migration
   * has shipped, never edit its SQL or reuse/renumber its version; add a new migration for
   * any further change, the same way you'd never edit a merged git commit. */
  version: number;
  /** Short, unique, past-tense-free slug — shows up in logs/errors, e.g. "schedule_end_dates". */
  name: string;
  /** Runs inside its own exclusive transaction (see runMigrations) — every query in here must
   * go through the `db` handle it's given, not some other reference, so it stays inside that
   * transaction. */
  up: (db: SQLiteDatabase) => Promise<void>;
}

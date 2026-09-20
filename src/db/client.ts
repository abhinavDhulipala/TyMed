import * as SQLite from 'expo-sqlite';
import { runMigrations } from './schema';

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

// Single shared connection used both by React components and by the
// notification handler (which runs outside the React tree), so there is
// only one code path for reading/writing app data.
export function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync('tymed.db').then(async (db) => {
      await runMigrations(db);
      return db;
    });
  }
  return dbPromise;
}

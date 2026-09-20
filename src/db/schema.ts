import type { SQLiteDatabase } from 'expo-sqlite';
import { MIGRATIONS } from './migrations';

/**
 * Applies every migration newer than the database's current PRAGMA user_version, in order,
 * each inside its own exclusive transaction — so a migration either fully applies (schema
 * change + version bump together) or fully rolls back, never leaves the schema half-updated.
 * (An earlier hand-rolled version of this runner could bump the version without actually
 * finishing the schema change if interrupted; wrapping each step in one transaction makes that
 * failure mode structurally impossible instead of something to remember to guard against.)
 *
 * To ship a schema change, add a file under src/db/migrations/ and list it in
 * src/db/migrations/index.ts — nothing here needs to change.
 */
export async function runMigrations(db: SQLiteDatabase): Promise<void> {
  await db.execAsync('PRAGMA journal_mode = WAL;');
  await db.execAsync('PRAGMA foreign_keys = ON;');

  for (const migration of MIGRATIONS) {
    const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
    const currentVersion = row?.user_version ?? 0;
    if (currentVersion >= migration.version) continue;

    try {
      await db.withExclusiveTransactionAsync(async (txn) => {
        await migration.up(txn);
        await txn.execAsync(`PRAGMA user_version = ${migration.version}`);
      });
    } catch (error) {
      throw new Error(`Migration ${migration.version} (${migration.name}) failed: ${error}`, { cause: error });
    }
  }
}

import type { SQLiteDatabase } from 'expo-sqlite';

/** SQLite has no "ADD COLUMN IF NOT EXISTS" — check first so a migration stays safe to re-run
 * (defense in depth on top of the runner's own transactional guarantee). */
export async function hasColumn(db: SQLiteDatabase, table: string, column: string): Promise<boolean> {
  const columns = await db.getAllAsync<{ name: string }>(`PRAGMA table_info(${table})`);
  return columns.some((c) => c.name === column);
}

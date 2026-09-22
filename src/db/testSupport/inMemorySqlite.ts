import Database from 'better-sqlite3';
import type { SQLiteDatabase } from 'expo-sqlite';
import { runMigrations } from '@/src/db/schema';

// expo-sqlite's actual engine is a native (JSI) binding with no JS/WASM build, so it can't run
// under jest at all (see src/db/__tests__/logs.test.ts for the probe that proves this). This
// wraps better-sqlite3 — a real SQLite engine — behind the same async method shape app code
// calls (execAsync/getAllAsync/getFirstAsync/runAsync/withExclusiveTransactionAsync), so the
// actual migrations in src/db/migrations and the actual SQL in src/db/*.ts run against a real
// database, catching migration bugs and malformed SQL that a hand-rolled fake can't.
function normalizeParams(params: unknown[]): unknown[] {
  if (params.length === 1 && Array.isArray(params[0])) {
    return params[0];
  }
  return params;
}

class InMemorySqliteAdapter {
  constructor(private readonly raw: Database.Database) {}

  async execAsync(source: string): Promise<void> {
    this.raw.exec(source);
  }

  async runAsync(source: string, ...params: unknown[]): Promise<{ lastInsertRowId: number; changes: number }> {
    const info = this.raw.prepare(source).run(...(normalizeParams(params) as never[]));
    return { lastInsertRowId: Number(info.lastInsertRowid), changes: info.changes };
  }

  async getFirstAsync<T>(source: string, ...params: unknown[]): Promise<T | null> {
    const row = this.raw.prepare(source).get(...(normalizeParams(params) as never[]));
    return (row as T) ?? null;
  }

  async getAllAsync<T>(source: string, ...params: unknown[]): Promise<T[]> {
    return this.raw.prepare(source).all(...(normalizeParams(params) as never[])) as T[];
  }

  async withExclusiveTransactionAsync(task: (txn: InMemorySqliteAdapter) => Promise<void>): Promise<void> {
    this.raw.exec('BEGIN EXCLUSIVE');
    try {
      await task(this);
      this.raw.exec('COMMIT');
    } catch (error) {
      this.raw.exec('ROLLBACK');
      throw error;
    }
  }
}

/** A fresh, fully-migrated in-memory database backed by a real SQLite engine — for db-layer
 * tests that need actual migrations and actual SQL to run, not a hand-rolled fake. */
export async function createTestDb(): Promise<SQLiteDatabase> {
  const raw = new Database(':memory:');
  const adapter = new InMemorySqliteAdapter(raw);
  // Mirrors src/db/client.ts's getDb(): open, then run every migration for real.
  await runMigrations(adapter as unknown as SQLiteDatabase);
  return adapter as unknown as SQLiteDatabase;
}

import * as SQLite from 'expo-sqlite';

// Documents *why* src/db/testSupport/inMemorySqlite.ts exists instead of using expo-sqlite
// directly in tests: its engine is a native (JSI) class with no JS/WASM build, so it simply
// cannot run under jest. If a future expo-sqlite upgrade ships a jest-compatible engine, this
// test will start failing (openDatabaseAsync will stop throwing) — that's the signal to drop
// the better-sqlite3 adapter and use the real module in tests instead.
test('expo-sqlite cannot open a database under jest (no native module)', async () => {
  await expect(SQLite.openDatabaseAsync(':memory:')).rejects.toThrow();
});

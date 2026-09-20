import type { Migration } from './types';

export const appSettings: Migration = {
  version: 2,
  name: 'app_settings',
  up: async (db) => {
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS app_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);
  },
};

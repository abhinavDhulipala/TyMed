import { hasColumn } from './helpers';
import type { Migration } from './types';

export const scheduleDaysOfWeek: Migration = {
  version: 3,
  name: 'schedule_days_of_week',
  up: async (db) => {
    // NULL means "every day" (the original default); a non-null value is a JSON array of
    // 0=Sun..6=Sat weekday numbers.
    if (!(await hasColumn(db, 'schedules', 'days_of_week'))) {
      await db.execAsync('ALTER TABLE schedules ADD COLUMN days_of_week TEXT;');
    }
  },
};

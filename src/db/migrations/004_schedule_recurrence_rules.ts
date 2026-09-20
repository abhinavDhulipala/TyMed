import { hasColumn } from './helpers';
import type { Migration } from './types';

export const scheduleRecurrenceRules: Migration = {
  version: 4,
  name: 'schedule_recurrence_rules',
  up: async (db) => {
    // recurrence_type: 'daily' | 'weekly' | 'monthly'. start_date anchors monthly recurrence
    // (same day-of-month every month) and end_date optionally stops reminders after that date
    // (both "YYYY-MM-DD", inclusive). Existing rows are backfilled from their days_of_week:
    // NULL (every day) -> daily, otherwise -> weekly, with no start/end date restriction.
    if (!(await hasColumn(db, 'schedules', 'recurrence_type'))) {
      await db.execAsync(`ALTER TABLE schedules ADD COLUMN recurrence_type TEXT NOT NULL DEFAULT 'daily';`);
    }
    if (!(await hasColumn(db, 'schedules', 'start_date'))) {
      await db.execAsync(`ALTER TABLE schedules ADD COLUMN start_date TEXT;`);
    }
    if (!(await hasColumn(db, 'schedules', 'end_date'))) {
      await db.execAsync(`ALTER TABLE schedules ADD COLUMN end_date TEXT;`);
    }
    await db.execAsync(
      `UPDATE schedules SET recurrence_type = CASE WHEN days_of_week IS NULL THEN 'daily' ELSE 'weekly' END
       WHERE recurrence_type = 'daily' AND days_of_week IS NOT NULL;`
    );
  },
};

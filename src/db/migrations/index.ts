import { initialSchema } from './001_initial_schema';
import { appSettings } from './002_app_settings';
import { scheduleDaysOfWeek } from './003_schedule_days_of_week';
import { scheduleRecurrenceRules } from './004_schedule_recurrence_rules';
import type { Migration } from './types';

// Ordered by version. To ship a schema change: add a new NNN_description.ts file exporting a
// Migration with the next version number, and append it here — runMigrations (src/db/schema.ts)
// takes care of the rest. Never edit or reorder an entry once it has shipped.
export const MIGRATIONS: Migration[] = [initialSchema, appSettings, scheduleDaysOfWeek, scheduleRecurrenceRules];

export type { Migration } from './types';

import type { SQLiteDatabase } from 'expo-sqlite';
import { todayDateString } from '@/src/utils/date';
import { createTestDb } from '../testSupport/inMemorySqlite';

// expo-sqlite has no in-memory implementation that runs under jest — it's a native (JSI)
// binding with no JS/WASM build, confirmed by src/db/__tests__/sqliteUnavailable.test.ts.
// createTestDb() runs the real migrations in src/db/migrations against a real SQLite engine
// (better-sqlite3), so these tests exercise the actual SQL in src/db/*.ts end to end: a typo'd
// column name or a broken migration fails here the same way it would against a device.
let mockDb: SQLiteDatabase;

jest.mock('../client', () => ({
  getDb: async () => mockDb,
}));

// Imported after the mock so every module picks up the mocked client.
import { createMedication } from '../medications';
import { createSchedule } from '../schedules';
import { ensureLogsForDate, findOrCreateTodayLogForSchedule, getDosesForDate } from '../logs';
import type { RecurrenceInput } from '../schedules';

function daysFromToday(offset: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return todayDateString(d);
}

const DAILY: RecurrenceInput = { recurrenceType: 'daily', daysOfWeek: null, startDate: null, endDate: null };

async function seedMedication(): Promise<number> {
  return createMedication({
    name: 'Metformin',
    dosage: '500mg',
    form: null,
    notes: null,
    pillsRemaining: null,
    refillThreshold: null,
  });
}

beforeEach(async () => {
  mockDb = await createTestDb();
});

describe('getDosesForDate (real migrations + real SQL via in-memory SQLite)', () => {
  it('returns doses for a future date (regression: used to only materialize for today)', async () => {
    const medicationId = await seedMedication();
    await createSchedule(medicationId, '08:00', DAILY);

    const future = daysFromToday(5);
    const doses = await getDosesForDate(future);

    expect(doses).toHaveLength(1);
    expect(doses[0]).toMatchObject({
      medicationName: 'Metformin',
      dosage: '500mg',
      scheduledDate: future,
      scheduledTime: '08:00',
      status: 'pending',
    });
  });

  it('returns doses for a past date with no prior activity, not just today', async () => {
    const medicationId = await seedMedication();
    await createSchedule(medicationId, '09:00', DAILY);

    const past = daysFromToday(-30);
    const doses = await getDosesForDate(past);

    expect(doses).toHaveLength(1);
    expect(doses[0].scheduledDate).toBe(past);
  });

  it('does not create duplicate log rows when a date is requested more than once', async () => {
    const medicationId = await seedMedication();
    await createSchedule(medicationId, '08:00', DAILY);

    const future = daysFromToday(5);
    const first = await getDosesForDate(future);
    const second = await getDosesForDate(future);

    const allLogs = await mockDb.getAllAsync('SELECT * FROM intake_logs');
    expect(allLogs).toHaveLength(1);
    expect(second[0].id).toBe(first[0].id);
  });

  it('excludes a weekly schedule on a date it is not active on', async () => {
    const medicationId = await seedMedication();
    await createSchedule(medicationId, '08:00', {
      recurrenceType: 'weekly',
      daysOfWeek: [1], // Monday only
      startDate: null,
      endDate: null,
    });

    // Fixed Tuesday, well in the future relative to any real "today".
    const tuesday = '2099-01-06';
    const doses = await getDosesForDate(tuesday);

    expect(doses).toHaveLength(0);
  });

  it('excludes a future date past the schedule end date', async () => {
    const medicationId = await seedMedication();
    await createSchedule(medicationId, '08:00', {
      recurrenceType: 'daily',
      daysOfWeek: null,
      startDate: null,
      endDate: daysFromToday(2),
    });

    const doses = await getDosesForDate(daysFromToday(10));
    expect(doses).toHaveLength(0);
  });

  it('excludes disabled schedules regardless of date', async () => {
    const medicationId = await seedMedication();
    const scheduleId = await createSchedule(medicationId, '08:00', DAILY);
    await mockDb.runAsync('UPDATE schedules SET enabled = 0 WHERE id = ?', scheduleId);

    const doses = await getDosesForDate(daysFromToday(5));
    expect(doses).toHaveLength(0);
  });
});

describe('ensureLogsForDate / findOrCreateTodayLogForSchedule', () => {
  it('still materializes and resolves a log row for today', async () => {
    const medicationId = await seedMedication();
    const scheduleId = await createSchedule(medicationId, '08:00', DAILY);

    const log = await findOrCreateTodayLogForSchedule(scheduleId);
    expect(log.scheduledDate).toBe(todayDateString());
    expect(log.status).toBe('pending');
  });

  it('is a no-op for a date with no active schedules', async () => {
    await ensureLogsForDate(daysFromToday(1));
    const allLogs = await mockDb.getAllAsync('SELECT * FROM intake_logs');
    expect(allLogs).toHaveLength(0);
  });
});

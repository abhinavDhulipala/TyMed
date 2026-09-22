import { todayDateString } from '@/src/utils/date';

// expo-sqlite has no in-memory implementation that runs under jest (it needs the native
// module), so instead of a real db we fake just the query shapes src/db/logs.ts issues.
// This is what src/db/logs.ts relies on to decide which schedules materialize a dose for a
// given date, so it's what pins down the "future days show no medications" regression.
interface FakeSchedule {
  id: number;
  medication_id: number;
  time_of_day: string;
  enabled: number;
  days_of_week: string | null;
  recurrence_type: 'daily' | 'weekly' | 'monthly';
  start_date: string | null;
  end_date: string | null;
}

interface FakeMedication {
  id: number;
  name: string;
  dosage: string | null;
}

interface FakeIntakeLog {
  id: number;
  medication_id: number;
  schedule_id: number | null;
  scheduled_date: string;
  scheduled_time: string;
  status: string;
  taken_at: string | null;
}

function createFakeDb() {
  const medications: FakeMedication[] = [];
  const schedules: FakeSchedule[] = [];
  const intakeLogs: FakeIntakeLog[] = [];
  let nextLogId = 1;

  const db = {
    async getAllAsync(sql: string, ...params: unknown[]) {
      if (sql.includes('FROM schedules WHERE enabled')) {
        return schedules.filter((s) => s.enabled === 1);
      }
      if (sql.includes('JOIN medications') && sql.includes('intake_logs.scheduled_date = ?')) {
        const [dateStr] = params as [string];
        return intakeLogs
          .filter((l) => l.scheduled_date === dateStr)
          .sort((a, b) => a.scheduled_time.localeCompare(b.scheduled_time))
          .map((l) => {
            const med = medications.find((m) => m.id === l.medication_id)!;
            return { ...l, medication_name: med.name, medication_dosage: med.dosage };
          });
      }
      throw new Error(`FakeDb: unhandled getAllAsync query: ${sql}`);
    },
    async getFirstAsync(sql: string, ...params: unknown[]) {
      if (sql.includes('SELECT id FROM intake_logs WHERE schedule_id')) {
        const [scheduleId, dateStr] = params as [number, string];
        return intakeLogs.find((l) => l.schedule_id === scheduleId && l.scheduled_date === dateStr) ?? null;
      }
      if (sql.includes('SELECT * FROM intake_logs WHERE schedule_id')) {
        const [scheduleId, dateStr] = params as [number, string];
        return intakeLogs.find((l) => l.schedule_id === scheduleId && l.scheduled_date === dateStr) ?? null;
      }
      throw new Error(`FakeDb: unhandled getFirstAsync query: ${sql}`);
    },
    async runAsync(sql: string, ...params: unknown[]) {
      if (sql.includes('INSERT INTO intake_logs')) {
        const [medicationId, scheduleId, scheduledDate, scheduledTime] = params as [number, number, string, string];
        intakeLogs.push({
          id: nextLogId++,
          medication_id: medicationId,
          schedule_id: scheduleId,
          scheduled_date: scheduledDate,
          scheduled_time: scheduledTime,
          status: 'pending',
          taken_at: null,
        });
        return {};
      }
      throw new Error(`FakeDb: unhandled runAsync query: ${sql}`);
    },
  };

  return {
    db,
    medications,
    schedules,
    intakeLogs,
    seedMedication(med: FakeMedication) {
      medications.push(med);
    },
    seedSchedule(sch: FakeSchedule) {
      schedules.push(sch);
    },
  };
}

let mockFake: ReturnType<typeof createFakeDb>;

jest.mock('../client', () => ({
  getDb: async () => mockFake.db,
}));

// Imported after the mock so logs.ts picks up the mocked client.
import { ensureLogsForDate, findOrCreateTodayLogForSchedule, getDosesForDate } from '../logs';

function daysFromToday(offset: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return todayDateString(d);
}

beforeEach(() => {
  mockFake = createFakeDb();
  mockFake.seedMedication({ id: 1, name: 'Metformin', dosage: '500mg' });
});

describe('getDosesForDate', () => {
  it('returns doses for a future date (regression: used to only materialize for today)', async () => {
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '08:00',
      enabled: 1,
      days_of_week: null,
      recurrence_type: 'daily',
      start_date: null,
      end_date: null,
    });

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
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '09:00',
      enabled: 1,
      days_of_week: null,
      recurrence_type: 'daily',
      start_date: null,
      end_date: null,
    });

    const past = daysFromToday(-30);
    const doses = await getDosesForDate(past);

    expect(doses).toHaveLength(1);
    expect(doses[0].scheduledDate).toBe(past);
  });

  it('does not create duplicate log rows when a date is requested more than once', async () => {
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '08:00',
      enabled: 1,
      days_of_week: null,
      recurrence_type: 'daily',
      start_date: null,
      end_date: null,
    });

    const future = daysFromToday(5);
    const first = await getDosesForDate(future);
    const second = await getDosesForDate(future);

    expect(mockFake.intakeLogs).toHaveLength(1);
    expect(second[0].id).toBe(first[0].id);
  });

  it('excludes a weekly schedule on a future date it is not active on', async () => {
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '08:00',
      enabled: 1,
      days_of_week: JSON.stringify([1]), // Monday only
      recurrence_type: 'weekly',
      start_date: null,
      end_date: null,
    });

    // Fixed Tuesday, well in the future relative to any real "today".
    const tuesday = '2099-01-06';
    const doses = await getDosesForDate(tuesday);

    expect(doses).toHaveLength(0);
    expect(mockFake.intakeLogs).toHaveLength(0);
  });

  it('excludes a future date past the schedule end date', async () => {
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '08:00',
      enabled: 1,
      days_of_week: null,
      recurrence_type: 'daily',
      start_date: null,
      end_date: daysFromToday(2),
    });

    const doses = await getDosesForDate(daysFromToday(10));
    expect(doses).toHaveLength(0);
  });

  it('excludes disabled schedules regardless of date', async () => {
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '08:00',
      enabled: 0,
      days_of_week: null,
      recurrence_type: 'daily',
      start_date: null,
      end_date: null,
    });

    const doses = await getDosesForDate(daysFromToday(5));
    expect(doses).toHaveLength(0);
  });
});

describe('ensureLogsForDate / findOrCreateTodayLogForSchedule', () => {
  it('still materializes and resolves a log row for today', async () => {
    mockFake.seedSchedule({
      id: 1,
      medication_id: 1,
      time_of_day: '08:00',
      enabled: 1,
      days_of_week: null,
      recurrence_type: 'daily',
      start_date: null,
      end_date: null,
    });

    const log = await findOrCreateTodayLogForSchedule(1);
    expect(log.scheduledDate).toBe(todayDateString());
    expect(log.status).toBe('pending');
  });

  it('is a no-op for a date with no active schedules', async () => {
    await ensureLogsForDate(daysFromToday(1));
    expect(mockFake.intakeLogs).toHaveLength(0);
  });
});

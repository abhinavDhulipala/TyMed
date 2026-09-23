import type { SQLiteDatabase } from 'expo-sqlite';
import { Platform } from 'react-native';
import { todayDateString } from '@/src/utils/date';
import { createTestDb } from '../testSupport/inMemorySqlite';

// Regression coverage for: marking a dose taken/skipped still let the Android alarm ring later
// that day. The native AlarmManager chain (AlarmReceiver.kt) has no visibility into
// intake_logs, so markDose has to explicitly tell it to stand down for today — see
// skipTodaysDoseReminder in src/notifications/scheduler.ts. Real migrations + real SQL run
// against an in-memory SQLite engine (see src/db/__tests__/logs.test.ts for the same pattern);
// only the native alarm boundary is stubbed, so this exercises actions.ts and scheduler.ts for
// real, not just the mock wiring.
let mockDb: SQLiteDatabase;

jest.mock('../client', () => ({
  getDb: async () => mockDb,
}));

const mockScheduleNativeAlarm = jest.fn();
const mockCancelNativeAlarm = jest.fn();

jest.mock('@/src/native/alarmModule', () => ({
  scheduleNativeAlarm: (request: unknown) => mockScheduleNativeAlarm(request),
  cancelNativeAlarm: (requestCode: number) => mockCancelNativeAlarm(requestCode),
  isNativeAlarmAvailable: () => true,
  setNativeFollowUpMinutes: jest.fn(),
}));

import { markDose } from '../actions';
import { createMedication } from '../medications';
import { createSchedule, type RecurrenceInput } from '../schedules';
import { ensureLogsForDate, getDosesForDate } from '../logs';

const DAILY: RecurrenceInput = { recurrenceType: 'daily', daysOfWeek: null, startDate: null, endDate: null };

// Mirrors scheduler.ts's private SNOOZE_REQUEST_CODE_OFFSET — kept in sync manually since it
// isn't exported (the snooze chain's request code is an implementation detail of the alarm
// scheduler, not something outside code should need).
const SNOOZE_REQUEST_CODE_OFFSET = 500_000;

function dateOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return todayDateString(d);
}

async function seedLog(
  recurrence: RecurrenceInput = DAILY,
  timeOfDay = '08:00',
  dateStr: string = todayDateString()
): Promise<{ logId: number; scheduleId: number; medicationId: number }> {
  const medicationId = await createMedication({
    name: 'Metformin',
    dosage: '500mg',
    form: null,
    notes: null,
    pillsRemaining: null,
    refillThreshold: null,
  });
  const scheduleId = await createSchedule(medicationId, timeOfDay, recurrence);
  await ensureLogsForDate(dateStr);
  const doses = await getDosesForDate(dateStr);
  return { logId: doses[0].id, scheduleId, medicationId };
}

async function seedTodayLog(timeOfDay = '08:00'): Promise<number> {
  const { logId } = await seedLog(DAILY, timeOfDay);
  return logId;
}

beforeEach(async () => {
  mockDb = await createTestDb();
  mockScheduleNativeAlarm.mockClear();
  mockCancelNativeAlarm.mockClear();
  Platform.OS = 'android';
});

describe('markDose (Android alarm suppression)', () => {
  it('cancels today\'s alarm and re-arms for tomorrow when marked taken ahead of time', async () => {
    const logId = await seedTodayLog('08:00');

    await markDose(logId, 'taken');

    // Both the primary daily chain and any pending snooze follow-up are cancelled.
    expect(mockCancelNativeAlarm).toHaveBeenCalledTimes(2);
    expect(mockScheduleNativeAlarm).toHaveBeenCalledTimes(1);

    const rearmed = mockScheduleNativeAlarm.mock.calls[0][0];
    expect(rearmed.isPrimary).toBe(true);
    expect(rearmed.hour).toBe(8);
    expect(rearmed.minute).toBe(0);

    // The re-armed trigger must not be today's already-passed (or upcoming) 08:00 slot again —
    // this is exactly the bug: without pushing to tomorrow, the alarm would still ring today.
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(8, 0, 0, 0);
    expect(rearmed.triggerAtMillis).toBe(tomorrow.getTime());
  });

  it('also suppresses today\'s alarm when marked skipped', async () => {
    const logId = await seedTodayLog('08:00');

    await markDose(logId, 'skipped');

    expect(mockCancelNativeAlarm).toHaveBeenCalled();
    expect(mockScheduleNativeAlarm).toHaveBeenCalledTimes(1);
  });

  it('does not touch alarms on iOS — the notification handler re-checks status at render time', async () => {
    Platform.OS = 'ios';
    const logId = await seedTodayLog('08:00');

    await markDose(logId, 'taken');

    expect(mockCancelNativeAlarm).not.toHaveBeenCalled();
    expect(mockScheduleNativeAlarm).not.toHaveBeenCalled();
  });

  it('does not touch alarms for a dose scheduled on a different day', async () => {
    const medicationId = await createMedication({
      name: 'Metformin',
      dosage: '500mg',
      form: null,
      notes: null,
      pillsRemaining: null,
      refillThreshold: null,
    });
    await createSchedule(medicationId, '08:00', DAILY);
    const future = new Date();
    future.setDate(future.getDate() + 3);
    const futureDate = todayDateString(future);
    await ensureLogsForDate(futureDate);
    const doses = await getDosesForDate(futureDate);

    await markDose(doses[0].id, 'taken');

    expect(mockCancelNativeAlarm).not.toHaveBeenCalled();
    expect(mockScheduleNativeAlarm).not.toHaveBeenCalled();
  });

  it('leaves the re-armed alarm alone when un-marking back to pending (no alarm calls fired)', async () => {
    const logId = await seedTodayLog('08:00');
    await markDose(logId, 'taken');
    mockScheduleNativeAlarm.mockClear();
    mockCancelNativeAlarm.mockClear();

    await markDose(logId, 'pending');

    expect(mockCancelNativeAlarm).not.toHaveBeenCalled();
    expect(mockScheduleNativeAlarm).not.toHaveBeenCalled();
  });
});

describe('markDose (Android alarm suppression) — edge cases', () => {
  it('cancels the primary chain and the snooze follow-up by their exact request codes', async () => {
    const { logId, scheduleId } = await seedLog();

    await markDose(logId, 'taken');

    expect(mockCancelNativeAlarm).toHaveBeenCalledWith(scheduleId);
    expect(mockCancelNativeAlarm).toHaveBeenCalledWith(scheduleId + SNOOZE_REQUEST_CODE_OFFSET);
    const rearmed = mockScheduleNativeAlarm.mock.calls[0][0];
    expect(rearmed.requestCode).toBe(scheduleId);
    expect(rearmed.scheduleId).toBe(scheduleId);
  });

  it('does not re-arm when the schedule ends today (tomorrow is past the end date)', async () => {
    const { logId } = await seedLog({ ...DAILY, endDate: todayDateString() });

    await markDose(logId, 'taken');

    // Still silences today's ring...
    expect(mockCancelNativeAlarm).toHaveBeenCalled();
    // ...but there's nothing left to arm for tomorrow, matching AlarmReceiver.rearmNextDay's
    // own end-date guard (a chain that had already fired its last occurrence wouldn't have
    // rearmed itself either).
    expect(mockScheduleNativeAlarm).not.toHaveBeenCalled();
  });

  it('still re-arms when the schedule ends tomorrow (tomorrow is the last active day)', async () => {
    const { logId } = await seedLog({ ...DAILY, endDate: dateOffset(1) });

    await markDose(logId, 'taken');

    expect(mockScheduleNativeAlarm).toHaveBeenCalledTimes(1);
  });

  it('preserves weekly recurrence fields (recurrenceType + encoded daysOfWeek) on re-arm', async () => {
    const todayWeekday = new Date().getDay();
    const { logId } = await seedLog({
      recurrenceType: 'weekly',
      daysOfWeek: [todayWeekday],
      startDate: null,
      endDate: null,
    });

    await markDose(logId, 'taken');

    const rearmed = mockScheduleNativeAlarm.mock.calls[0][0];
    expect(rearmed.recurrenceType).toBe('weekly');
    expect(rearmed.daysOfWeek).toBe(String(todayWeekday));
  });

  it('preserves monthly recurrence fields (recurrenceType + day-of-month anchor) on re-arm', async () => {
    const anchor = todayDateString();
    const { logId } = await seedLog({
      recurrenceType: 'monthly',
      daysOfWeek: null,
      startDate: anchor,
      endDate: null,
    });

    await markDose(logId, 'taken');

    const rearmed = mockScheduleNativeAlarm.mock.calls[0][0];
    expect(rearmed.recurrenceType).toBe('monthly');
    expect(rearmed.startDate).toBe(anchor);
  });

  it('suppresses the alarm again when a resolved dose is switched between taken and skipped', async () => {
    const { logId } = await seedLog();

    await markDose(logId, 'taken');
    expect(mockScheduleNativeAlarm).toHaveBeenCalledTimes(1);

    await markDose(logId, 'skipped');
    expect(mockScheduleNativeAlarm).toHaveBeenCalledTimes(2);

    await markDose(logId, 'taken');
    expect(mockScheduleNativeAlarm).toHaveBeenCalledTimes(3);
  });

  it('does not touch alarms for a dose scheduled on a past date', async () => {
    const medicationId = await createMedication({
      name: 'Metformin',
      dosage: '500mg',
      form: null,
      notes: null,
      pillsRemaining: null,
      refillThreshold: null,
    });
    await createSchedule(medicationId, '08:00', DAILY);
    const yesterday = dateOffset(-1);
    await ensureLogsForDate(yesterday);
    const doses = await getDosesForDate(yesterday);

    await markDose(doses[0].id, 'taken');

    expect(mockCancelNativeAlarm).not.toHaveBeenCalled();
    expect(mockScheduleNativeAlarm).not.toHaveBeenCalled();
  });

  it('does not crash and skips alarm calls for a log with no associated schedule', async () => {
    const { logId } = await seedLog();
    // A schedule_id can be null in the data model (see IntakeLog.scheduleId) even though every
    // current write path always sets one — simulate that defensively rather than assuming it
    // can never happen.
    await mockDb.runAsync('UPDATE intake_logs SET schedule_id = NULL WHERE id = ?', logId);

    await expect(markDose(logId, 'taken')).resolves.not.toThrow();

    expect(mockCancelNativeAlarm).not.toHaveBeenCalled();
    expect(mockScheduleNativeAlarm).not.toHaveBeenCalled();
  });
});

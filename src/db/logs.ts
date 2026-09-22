import { getDb } from './client';
import type { DoseStatus, DoseWithMedication, IntakeLog, RecurrenceType } from '@/src/types';
import { todayDateString } from '@/src/utils/date';
import { isScheduleActiveOn } from '@/src/utils/schedule';

interface LogRow {
  id: number;
  medication_id: number;
  schedule_id: number | null;
  scheduled_date: string;
  scheduled_time: string;
  status: DoseStatus;
  taken_at: string | null;
}

function mapLog(row: LogRow): IntakeLog {
  return {
    id: row.id,
    medicationId: row.medication_id,
    scheduleId: row.schedule_id,
    scheduledDate: row.scheduled_date,
    scheduledTime: row.scheduled_time,
    status: row.status,
    takenAt: row.taken_at,
  };
}

/** Lazily creates a date's pending log rows for every enabled schedule that recurs on it and
 * doesn't have a row yet. Works for any date — past, today, or future — so a day's doses exist
 * as soon as it's viewed, not just once it becomes "today". */
export async function ensureLogsForDate(dateStr: string): Promise<void> {
  const db = await getDb();
  const schedules = await db.getAllAsync<{
    id: number;
    medication_id: number;
    time_of_day: string;
    days_of_week: string | null;
    recurrence_type: RecurrenceType;
    start_date: string | null;
    end_date: string | null;
  }>(
    'SELECT id, medication_id, time_of_day, days_of_week, recurrence_type, start_date, end_date FROM schedules WHERE enabled = 1'
  );
  for (const schedule of schedules) {
    const daysOfWeek = schedule.days_of_week ? (JSON.parse(schedule.days_of_week) as number[]) : null;
    const active = isScheduleActiveOn(
      {
        recurrenceType: schedule.recurrence_type,
        daysOfWeek,
        startDate: schedule.start_date,
        endDate: schedule.end_date,
      },
      dateStr
    );
    if (!active) continue;

    const existing = await db.getFirstAsync<{ id: number }>(
      'SELECT id FROM intake_logs WHERE schedule_id = ? AND scheduled_date = ?',
      schedule.id,
      dateStr
    );
    if (!existing) {
      await db.runAsync(
        `INSERT INTO intake_logs (medication_id, schedule_id, scheduled_date, scheduled_time, status)
         VALUES (?, ?, ?, ?, 'pending')`,
        schedule.medication_id,
        schedule.id,
        dateStr,
        schedule.time_of_day
      );
    }
  }
}

const DOSE_SELECT = `
  SELECT intake_logs.*, medications.name AS medication_name, medications.dosage AS medication_dosage
  FROM intake_logs
  JOIN medications ON medications.id = intake_logs.medication_id
`;

interface DoseRow extends LogRow {
  medication_name: string;
  medication_dosage: string | null;
}

function mapDose(row: DoseRow): DoseWithMedication {
  return { ...mapLog(row), medicationName: row.medication_name, dosage: row.medication_dosage };
}

/** All doses scheduled for one specific date — including today, present and future times alike.
 * The home screen is just this called with today's date: a day is a day, today isn't special. */
export async function getDosesForDate(dateStr: string): Promise<DoseWithMedication[]> {
  await ensureLogsForDate(dateStr);
  const db = await getDb();
  const rows = await db.getAllAsync<DoseRow>(
    `${DOSE_SELECT} WHERE intake_logs.scheduled_date = ? ORDER BY intake_logs.scheduled_time`,
    dateStr
  );
  return rows.map(mapDose);
}

export async function getLog(logId: number): Promise<IntakeLog | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<LogRow>('SELECT * FROM intake_logs WHERE id = ?', logId);
  return row ? mapLog(row) : null;
}

export async function getDoseById(logId: number): Promise<DoseWithMedication | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<DoseRow>(`${DOSE_SELECT} WHERE intake_logs.id = ?`, logId);
  return row ? mapDose(row) : null;
}

export async function setLogStatus(logId: number, status: DoseStatus): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    'UPDATE intake_logs SET status = ?, taken_at = ? WHERE id = ?',
    status,
    status === 'taken' ? new Date().toISOString() : null,
    logId
  );
}

/** Corrects the recorded taken time for a dose already marked taken. */
export async function setLogTakenAt(logId: number, takenAtIso: string): Promise<void> {
  const db = await getDb();
  await db.runAsync('UPDATE intake_logs SET taken_at = ? WHERE id = ?', takenAtIso, logId);
}

/** Resolves today's log row for a given schedule, creating it if needed (used from the notification handler). */
export async function findOrCreateTodayLogForSchedule(scheduleId: number): Promise<IntakeLog> {
  await ensureLogsForDate(todayDateString());
  const db = await getDb();
  const today = todayDateString();
  const row = await db.getFirstAsync<LogRow>(
    'SELECT * FROM intake_logs WHERE schedule_id = ? AND scheduled_date = ?',
    scheduleId,
    today
  );
  if (!row) {
    throw new Error(`No log row found for schedule ${scheduleId} on ${today}`);
  }
  return mapLog(row);
}

export interface DailyAdherence {
  taken: number;
  resolved: number; // taken + skipped
}

/** Per-day taken/resolved counts between two dates (inclusive), for the adherence calendar. */
export async function getDailyAdherence(startDate: string, endDate: string): Promise<Record<string, DailyAdherence>> {
  const db = await getDb();
  const rows = await db.getAllAsync<{ scheduled_date: string; taken: number; resolved: number }>(
    `SELECT scheduled_date,
            SUM(CASE WHEN status = 'taken' THEN 1 ELSE 0 END) AS taken,
            SUM(CASE WHEN status IN ('taken', 'skipped') THEN 1 ELSE 0 END) AS resolved
     FROM intake_logs
     WHERE scheduled_date BETWEEN ? AND ?
     GROUP BY scheduled_date`,
    startDate,
    endDate
  );
  const result: Record<string, DailyAdherence> = {};
  for (const row of rows) {
    result[row.scheduled_date] = { taken: row.taken, resolved: row.resolved };
  }
  return result;
}

export async function getTodayStatusForSchedule(scheduleId: number): Promise<DoseStatus | null> {
  const db = await getDb();
  const today = todayDateString();
  const row = await db.getFirstAsync<{ status: DoseStatus }>(
    'SELECT status FROM intake_logs WHERE schedule_id = ? AND scheduled_date = ?',
    scheduleId,
    today
  );
  return row?.status ?? null;
}

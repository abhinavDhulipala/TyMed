import { getDb } from './client';
import type { DoseStatus, DoseWithMedication, IntakeLog } from '@/src/types';
import { todayDateString } from '@/src/utils/date';

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

/** Lazily creates today's pending log rows for every enabled schedule that doesn't have one yet. */
export async function ensureTodayLogs(): Promise<void> {
  const db = await getDb();
  const today = todayDateString();
  const schedules = await db.getAllAsync<{ id: number; medication_id: number; time_of_day: string }>(
    'SELECT id, medication_id, time_of_day FROM schedules WHERE enabled = 1'
  );
  for (const schedule of schedules) {
    const existing = await db.getFirstAsync<{ id: number }>(
      'SELECT id FROM intake_logs WHERE schedule_id = ? AND scheduled_date = ?',
      schedule.id,
      today
    );
    if (!existing) {
      await db.runAsync(
        `INSERT INTO intake_logs (medication_id, schedule_id, scheduled_date, scheduled_time, status)
         VALUES (?, ?, ?, ?, 'pending')`,
        schedule.medication_id,
        schedule.id,
        today,
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

export async function getTodayDoses(): Promise<DoseWithMedication[]> {
  await ensureTodayLogs();
  const db = await getDb();
  const today = todayDateString();
  const rows = await db.getAllAsync<DoseRow>(
    `${DOSE_SELECT} WHERE intake_logs.scheduled_date = ? ORDER BY intake_logs.scheduled_time`,
    today
  );
  return rows.map(mapDose);
}

export async function getHistory(limit = 200): Promise<DoseWithMedication[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<DoseRow>(
    `${DOSE_SELECT} WHERE intake_logs.status != 'pending'
     ORDER BY intake_logs.scheduled_date DESC, intake_logs.scheduled_time DESC
     LIMIT ?`,
    limit
  );
  return rows.map(mapDose);
}

export async function getLog(logId: number): Promise<IntakeLog | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<LogRow>('SELECT * FROM intake_logs WHERE id = ?', logId);
  return row ? mapLog(row) : null;
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

/** Resolves today's log row for a given schedule, creating it if needed (used from the notification handler). */
export async function findOrCreateTodayLogForSchedule(scheduleId: number): Promise<IntakeLog> {
  await ensureTodayLogs();
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

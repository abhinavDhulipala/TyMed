import { getDb } from './client';
import type { RecurrenceType, Schedule } from '@/src/types';
export { isScheduleActiveOn } from '@/src/utils/schedule';

interface ScheduleRow {
  id: number;
  medication_id: number;
  time_of_day: string;
  enabled: number;
  notification_ids: string | null;
  days_of_week: string | null;
  recurrence_type: RecurrenceType;
  start_date: string | null;
  end_date: string | null;
}

function mapSchedule(row: ScheduleRow): Schedule {
  return {
    id: row.id,
    medicationId: row.medication_id,
    timeOfDay: row.time_of_day,
    enabled: row.enabled === 1,
    notificationIds: row.notification_ids ? (JSON.parse(row.notification_ids) as string[]) : [],
    recurrenceType: row.recurrence_type,
    daysOfWeek: row.days_of_week ? (JSON.parse(row.days_of_week) as number[]) : null,
    startDate: row.start_date,
    endDate: row.end_date,
  };
}

export interface RecurrenceInput {
  recurrenceType: RecurrenceType;
  /** Required (non-null) when recurrenceType === 'weekly'; ignored otherwise. */
  daysOfWeek: number[] | null;
  /** Required when recurrenceType === 'monthly' (the day-of-month anchor). */
  startDate: string | null;
  endDate: string | null;
}

export async function listSchedulesForMedication(medicationId: number): Promise<Schedule[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<ScheduleRow>(
    'SELECT * FROM schedules WHERE medication_id = ? ORDER BY time_of_day',
    medicationId
  );
  return rows.map(mapSchedule);
}

export async function getSchedule(id: number): Promise<Schedule | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<ScheduleRow>('SELECT * FROM schedules WHERE id = ?', id);
  return row ? mapSchedule(row) : null;
}

export interface ScheduleWithMedication extends Schedule {
  medicationName: string;
  dosage: string | null;
}

/** Every enabled schedule across all medications, for re-arming native alarms on app start. */
export async function listAllEnabledSchedulesWithMedication(): Promise<ScheduleWithMedication[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<ScheduleRow & { name: string; dosage: string | null }>(
    `SELECT schedules.*, medications.name AS name, medications.dosage AS dosage
     FROM schedules
     JOIN medications ON medications.id = schedules.medication_id
     WHERE schedules.enabled = 1`
  );
  return rows.map((row) => ({ ...mapSchedule(row), medicationName: row.name, dosage: row.dosage }));
}

export async function createSchedule(
  medicationId: number,
  timeOfDay: string,
  recurrence: RecurrenceInput
): Promise<number> {
  const db = await getDb();
  const result = await db.runAsync(
    `INSERT INTO schedules (medication_id, time_of_day, enabled, days_of_week, recurrence_type, start_date, end_date)
     VALUES (?, ?, 1, ?, ?, ?, ?)`,
    medicationId,
    timeOfDay,
    recurrence.daysOfWeek ? JSON.stringify(recurrence.daysOfWeek) : null,
    recurrence.recurrenceType,
    recurrence.startDate,
    recurrence.endDate
  );
  return result.lastInsertRowId;
}

/** Updates a schedule's recurrence in place, keeping its id (and therefore its intake_logs
 * history) stable — used when editing a medication so unchanged time slots aren't touched. */
export async function updateScheduleRecurrence(id: number, recurrence: RecurrenceInput): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    'UPDATE schedules SET days_of_week = ?, recurrence_type = ?, start_date = ?, end_date = ? WHERE id = ?',
    recurrence.daysOfWeek ? JSON.stringify(recurrence.daysOfWeek) : null,
    recurrence.recurrenceType,
    recurrence.startDate,
    recurrence.endDate,
    id
  );
}

export async function setScheduleNotificationIds(scheduleId: number, ids: string[]): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    'UPDATE schedules SET notification_ids = ? WHERE id = ?',
    JSON.stringify(ids),
    scheduleId
  );
}

export async function deleteSchedule(id: number): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM schedules WHERE id = ?', id);
}

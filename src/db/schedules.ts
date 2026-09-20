import { getDb } from './client';
import type { Schedule } from '@/src/types';
export { isScheduleActiveOnWeekday } from '@/src/utils/schedule';

interface ScheduleRow {
  id: number;
  medication_id: number;
  time_of_day: string;
  enabled: number;
  notification_ids: string | null;
  days_of_week: string | null;
}

function mapSchedule(row: ScheduleRow): Schedule {
  return {
    id: row.id,
    medicationId: row.medication_id,
    timeOfDay: row.time_of_day,
    enabled: row.enabled === 1,
    notificationIds: row.notification_ids ? (JSON.parse(row.notification_ids) as string[]) : [],
    daysOfWeek: row.days_of_week ? (JSON.parse(row.days_of_week) as number[]) : null,
  };
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
  daysOfWeek: number[] | null = null
): Promise<number> {
  const db = await getDb();
  const result = await db.runAsync(
    'INSERT INTO schedules (medication_id, time_of_day, enabled, days_of_week) VALUES (?, ?, 1, ?)',
    medicationId,
    timeOfDay,
    daysOfWeek ? JSON.stringify(daysOfWeek) : null
  );
  return result.lastInsertRowId;
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

export async function deleteSchedulesForMedication(medicationId: number): Promise<Schedule[]> {
  const schedules = await listSchedulesForMedication(medicationId);
  const db = await getDb();
  await db.runAsync('DELETE FROM schedules WHERE medication_id = ?', medicationId);
  return schedules;
}

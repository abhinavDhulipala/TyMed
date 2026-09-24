import { createSchedule, deleteSchedule, listSchedulesForMedication, updateScheduleRecurrence, type RecurrenceInput } from './schedules';
import { cancelDoseReminders, scheduleDoseReminders } from '@/src/notifications/scheduler';
import { recurrenceRuleEquals } from '@/src/utils/schedule';
import { todayDateString } from '@/src/utils/date';
import type { MedicationInput, RecurrenceType, Schedule } from '@/src/types';

/** The monthly day-of-month anchor: preserve an existing monthly schedule's own anchor (so
 * editing something unrelated doesn't shift when it fires), otherwise anchor to today — either
 * a brand new schedule, or one just switched to monthly from another recurrence type. */
function resolveStartDate(existing: Schedule | undefined, recurrenceType: RecurrenceType): string | null {
  if (recurrenceType !== 'monthly') return null;
  if (existing && existing.recurrenceType === 'monthly' && existing.startDate) return existing.startDate;
  return todayDateString();
}

/** Brings a medication's schedules in line with the given time slots + repeat pattern, and
 * re-arms their reminders. Shared by the edit screen and the AI assistant's
 * update_medication_schedule tool so both edit schedules the exact same way. */
export async function syncMedicationSchedules(
  medicationId: number,
  medication: Pick<MedicationInput, 'name' | 'dosage'>,
  times: string[],
  // startDate is derived per schedule below (the monthly anchor), not chosen by the caller.
  recurrence: Omit<RecurrenceInput, 'startDate'>
): Promise<void> {
  const uniqueTimes = Array.from(new Set(times));
  const nextTimeSet = new Set(uniqueTimes);

  // Diff against what's already there instead of replacing everything on every save: a
  // schedule for a time slot that still exists keeps its id, which keeps its intake_logs
  // history (including any already-taken doses) and lets the native alarm be updated in
  // place (idempotent re-arm) rather than cancelled and re-created. Only truly removed time
  // slots get deleted/cancelled — this is what actually fixes duplicated/orphaned reminders
  // after an edit.
  const existingSchedules = await listSchedulesForMedication(medicationId);
  const existingByTime = new Map(existingSchedules.map((s) => [s.timeOfDay, s]));

  const removed = existingSchedules.filter((s) => !nextTimeSet.has(s.timeOfDay));
  if (removed.length > 0) {
    await cancelDoseReminders(removed);
    for (const schedule of removed) {
      await deleteSchedule(schedule.id);
    }
  }

  for (const time of uniqueTimes) {
    const existing: Schedule | undefined = existingByTime.get(time);
    const startDate = resolveStartDate(existing, recurrence.recurrenceType);
    const nextRule: RecurrenceInput = {
      recurrenceType: recurrence.recurrenceType,
      daysOfWeek: recurrence.daysOfWeek,
      startDate,
      endDate: recurrence.endDate,
    };

    let scheduleId: number;
    if (existing) {
      scheduleId = existing.id;
      if (!recurrenceRuleEquals(existing, nextRule)) {
        await updateScheduleRecurrence(existing.id, nextRule);
      }
    } else {
      scheduleId = await createSchedule(medicationId, time, nextRule);
    }

    // Re-arming is idempotent (same schedule id => same native request code => the platform
    // updates the existing alarm/notification in place), so it's safe to do unconditionally —
    // covers the medication name/dosage having changed even when the time didn't.
    await scheduleDoseReminders({
      scheduleId,
      medicationId,
      medicationName: medication.name,
      dosage: medication.dosage,
      timeOfDay: time,
      recurrenceType: nextRule.recurrenceType,
      daysOfWeek: nextRule.daysOfWeek,
      startDate: nextRule.startDate,
      endDate: nextRule.endDate,
    });
  }
}

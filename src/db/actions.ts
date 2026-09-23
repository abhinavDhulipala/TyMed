import { getLog, setLogStatus } from './logs';
import { decrementPillCount, incrementPillCount } from './medications';
import { getScheduleWithMedication } from './schedules';
import { skipTodaysDoseReminder } from '@/src/notifications/scheduler';
import type { DoseStatus } from '@/src/types';
import { todayDateString } from '@/src/utils/date';

/** Marks a dose taken/skipped/pending, keeping the medication's pill count in sync. */
export async function markDose(logId: number, status: DoseStatus): Promise<void> {
  const log = await getLog(logId);
  if (!log) return;

  if (log.status === 'taken' && status !== 'taken') {
    await incrementPillCount(log.medicationId);
  } else if (log.status !== 'taken' && status === 'taken') {
    await decrementPillCount(log.medicationId);
  }

  await setLogStatus(logId, status);

  // Resolving today's dose ahead of its alarm time must stop that alarm from still ringing.
  // The Android alarm chain fires natively with no visibility into intake_logs, so it has to
  // be told explicitly (skipTodaysDoseReminder re-arms for tomorrow instead of cancelling
  // outright, so the recurring chain isn't broken).
  if ((status === 'taken' || status === 'skipped') && log.scheduleId != null && log.scheduledDate === todayDateString()) {
    const schedule = await getScheduleWithMedication(log.scheduleId);
    if (schedule) {
      await skipTodaysDoseReminder(schedule);
    }
  }
}

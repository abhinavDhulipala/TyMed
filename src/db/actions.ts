import { getLog, setLogStatus } from './logs';
import { decrementPillCount, incrementPillCount } from './medications';
import type { DoseStatus } from '@/src/types';

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
}

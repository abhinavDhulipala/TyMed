// Pure, DB-free helpers split out from src/db/schedules.ts so they can be unit tested without
// pulling in expo-sqlite (which isn't mockable in this project's jest setup).
import { parseDateStr } from './date';
import type { RecurrenceType } from '@/src/types';

export interface RecurrenceRule {
  recurrenceType: RecurrenceType;
  /** 0=Sun..6=Sat. Only read when recurrenceType === 'weekly'. */
  daysOfWeek: number[] | null;
  /** "YYYY-MM-DD". Day-of-month anchor for monthly; lower bound for all types. */
  startDate: string | null;
  /** "YYYY-MM-DD", inclusive upper bound. */
  endDate: string | null;
}

function daysInMonth(year: number, month0: number): number {
  return new Date(year, month0 + 1, 0).getDate();
}

/** True when a schedule recurs on the given date, per its recurrence type, day-of-week
 * selection (weekly only), and optional start/end date bounds. */
export function isScheduleActiveOn(rule: RecurrenceRule, dateStr: string): boolean {
  if (rule.startDate && dateStr < rule.startDate) return false;
  if (rule.endDate && dateStr > rule.endDate) return false;

  switch (rule.recurrenceType) {
    case 'daily':
      return true;
    case 'weekly': {
      const weekday = parseDateStr(dateStr).getDay();
      return rule.daysOfWeek !== null && rule.daysOfWeek.includes(weekday);
    }
    case 'monthly': {
      if (!rule.startDate) return false;
      const anchor = parseDateStr(rule.startDate);
      const date = parseDateStr(dateStr);
      // A schedule anchored on e.g. the 31st fires on the last day of shorter months instead
      // of never firing that month.
      const targetDay = Math.min(anchor.getDate(), daysInMonth(date.getFullYear(), date.getMonth()));
      return date.getDate() === targetDay;
    }
  }
}

/** Structural equality for two recurrence rules — used to decide whether an existing schedule
 * needs updating when a medication is edited. */
export function recurrenceRuleEquals(a: RecurrenceRule, b: RecurrenceRule): boolean {
  if (a.recurrenceType !== b.recurrenceType) return false;
  if (a.startDate !== b.startDate) return false;
  if (a.endDate !== b.endDate) return false;
  if (a.daysOfWeek === null || b.daysOfWeek === null) return a.daysOfWeek === b.daysOfWeek;
  return a.daysOfWeek.length === b.daysOfWeek.length && a.daysOfWeek.every((d, i) => d === b.daysOfWeek![i]);
}

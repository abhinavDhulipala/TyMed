export type AlarmRequest = {
  /** Epoch milliseconds this alarm should fire at. */
  triggerAtMillis: number;
  /** Unique id for this specific alarm (used to cancel it later). */
  requestCode: number;
  scheduleId: number;
  medicationId: number;
  medicationName: string;
  dosage: string | null;
  /** True for the daily dose alarm (self-reschedules +24h); false for a snooze follow-up. */
  isPrimary: boolean;
  /** Needed so a primary alarm can re-arm itself for the same time tomorrow. */
  hour: number;
  minute: number;
  /** "daily" | "weekly" | "monthly" — gates whether a Chain A alarm actually rings on a given
   * day; the daily rearm poll always happens regardless (until past endDate). */
  recurrenceType: string;
  /** Comma-separated 0=Sun..6=Sat weekdays; only read when recurrenceType is "weekly". */
  daysOfWeek: string;
  /** "YYYY-MM-DD", or "" — the monthly day-of-month anchor (also a general lower bound). */
  startDate: string;
  /** "YYYY-MM-DD", inclusive, or "" for no end date. Past this date the alarm stops
   * rearming itself entirely. */
  endDate: string;
};

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
  /** Comma-separated 0=Sun..6=Sat weekdays this dose recurs on; "" means every day. Gates
   * whether a Chain A alarm actually rings today — the daily rearm always happens regardless. */
  daysOfWeek: string;
};

// Pure, DB-free helper split out from src/db/schedules.ts so it can be unit tested without
// pulling in expo-sqlite (which isn't mockable in this project's jest setup).

/** True when a schedule recurs on the given 0=Sun..6=Sat weekday (null daysOfWeek = every day). */
export function isScheduleActiveOnWeekday(daysOfWeek: number[] | null, weekday: number): boolean {
  return daysOfWeek === null || daysOfWeek.includes(weekday);
}

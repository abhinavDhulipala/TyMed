import { Platform } from 'react-native';
import {
  setScheduleNotificationIds,
  listAllEnabledSchedulesWithMedication,
  type ScheduleWithMedication,
} from '@/src/db/schedules';
import { cancelNativeAlarm, scheduleNativeAlarm, stopNativeAlarmRinging } from '@/src/native/alarmModule';
import type { RecurrenceType, Schedule } from '@/src/types';
import { todayDateString } from '@/src/utils/date';
import { getNotifications, DOSE_CATEGORY } from './setup';

// Android: a single native alarm per schedule rings continuously until the user responds
// (see modules/tymed-alarm) — no separate "nudge" notifications needed, the alarm itself
// won't stop. iOS can't do a custom non-stop alarm without a special Apple entitlement, so
// it keeps the previous approximation: a primary notification plus two fixed nudges.
const IOS_NUDGE_OFFSETS_MINUTES = [0, 10, 20];

// Chain B (snooze) alarms reuse the schedule id as a base, offset so they never collide
// with the Chain A (daily) request code for the same schedule.
const SNOOZE_REQUEST_CODE_OFFSET = 500_000;

interface DoseReminderParams {
  scheduleId: number;
  medicationId: number;
  medicationName: string;
  dosage: string | null;
  timeOfDay: string; // "HH:MM"
  recurrenceType: RecurrenceType;
  daysOfWeek?: number[] | null; // 0=Sun..6=Sat; only meaningful when recurrenceType === 'weekly'
  startDate?: string | null; // "YYYY-MM-DD"; the monthly day-of-month anchor
  endDate?: string | null; // "YYYY-MM-DD", inclusive
}

/** Encodes for the native side: comma-separated weekday numbers, or "" for none/unused. */
function encodeDaysOfWeek(daysOfWeek: number[] | null | undefined): string {
  return daysOfWeek && daysOfWeek.length > 0 ? daysOfWeek.join(',') : '';
}

function nextOccurrenceMillis(hour: number, minute: number): number {
  const now = new Date();
  const next = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, minute, 0, 0);
  if (next.getTime() <= now.getTime()) {
    next.setDate(next.getDate() + 1);
  }
  return next.getTime();
}

function tomorrowOccurrenceMillis(hour: number, minute: number): number {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, hour, minute, 0, 0).getTime();
}

/** Arms (or re-arms) the daily native alarm for a schedule. Android only. The native chain
 * checks the alarm daily and only actually rings on days the recurrence rule matches (see
 * AlarmReceiver.isActiveOn) — a schedule already past its end date is never armed at all. */
function armAndroidDailyAlarm(params: DoseReminderParams, hour: number, minute: number): void {
  if (params.endDate && params.endDate < todayDateString()) {
    cancelNativeAlarm(params.scheduleId);
    return;
  }
  scheduleNativeAlarm({
    triggerAtMillis: nextOccurrenceMillis(hour, minute),
    requestCode: params.scheduleId,
    scheduleId: params.scheduleId,
    medicationId: params.medicationId,
    medicationName: params.medicationName,
    dosage: params.dosage,
    isPrimary: true,
    hour,
    minute,
    recurrenceType: params.recurrenceType,
    daysOfWeek: encodeDaysOfWeek(params.daysOfWeek),
    startDate: params.startDate ?? '',
    endDate: params.endDate ?? '',
  });
}

function buildIosContent(
  params: Pick<DoseReminderParams, 'medicationName' | 'dosage' | 'scheduleId' | 'medicationId'>,
  isPrimary: boolean
) {
  return {
    title: isPrimary ? `Time for ${params.medicationName}` : `Reminder: ${params.medicationName}`,
    body: params.dosage ? `Dose: ${params.dosage}` : 'Time to take your dose',
    categoryIdentifier: DOSE_CATEGORY,
    data: { scheduleId: params.scheduleId, medicationId: params.medicationId },
  };
}

async function scheduleIosDoseReminders(params: DoseReminderParams, hour: number, minute: number): Promise<string[]> {
  const Notifications = getNotifications();
  if (!Notifications) return [];

  try {
    const ids: string[] = [];
    for (const offset of IOS_NUDGE_OFFSETS_MINUTES) {
      const total = hour * 60 + minute + offset;
      const h = Math.floor(total / 60) % 24;
      const m = total % 60;
      const id = await Notifications.scheduleNotificationAsync({
        content: buildIosContent(params, offset === 0),
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.DAILY,
          hour: h,
          minute: m,
        },
      });
      ids.push(id);
    }
    await setScheduleNotificationIds(params.scheduleId, ids);
    return ids;
  } catch (error) {
    console.warn('[notifications] failed to schedule dose reminders:', error);
    return [];
  }
}

/** Schedules the dose reminder for a schedule — a non-stop native alarm on Android, or the
 * expo-notifications approximation on iOS. */
export async function scheduleDoseReminders(params: DoseReminderParams): Promise<string[]> {
  const [hourStr, minuteStr] = params.timeOfDay.split(':');
  const hour = Number(hourStr);
  const minute = Number(minuteStr);

  if (Platform.OS === 'android') {
    armAndroidDailyAlarm(params, hour, minute);
    await setScheduleNotificationIds(params.scheduleId, []);
    return [];
  }

  return scheduleIosDoseReminders(params, hour, minute);
}

/** Cancels all reminders (native alarms and/or notifications) for the given schedules. */
export async function cancelDoseReminders(schedules: Pick<Schedule, 'id' | 'notificationIds'>[]): Promise<void> {
  if (Platform.OS === 'android') {
    for (const schedule of schedules) {
      cancelNativeAlarm(schedule.id);
      cancelNativeAlarm(schedule.id + SNOOZE_REQUEST_CODE_OFFSET);
    }
    return;
  }

  const Notifications = getNotifications();
  if (!Notifications) return;
  const ids = schedules.flatMap((s) => s.notificationIds);
  if (ids.length === 0) return;
  try {
    await Promise.all(ids.map((id) => Notifications.cancelScheduledNotificationAsync(id)));
  } catch (error) {
    console.warn('[notifications] failed to cancel dose reminders:', error);
  }
}

/** Stops today's already-armed Android alarm from ringing (e.g. the dose was just marked
 * taken/skipped ahead of its alarm time) without breaking the recurring chain: the daily
 * alarm re-arms *itself* on each fire (see AlarmReceiver.kt), so simply cancelling it would
 * silently kill tomorrow's reminder too. Re-arming for tomorrow's occurrence instead keeps
 * the chain alive. iOS doesn't need this — its notification handler re-checks intake_logs
 * status at render time (see src/notifications/setup.ts). No-op past the schedule's end date. */
export async function skipTodaysDoseReminder(schedule: ScheduleWithMedication): Promise<void> {
  if (Platform.OS !== 'android') return;

  cancelNativeAlarm(schedule.id);
  cancelNativeAlarm(schedule.id + SNOOZE_REQUEST_CODE_OFFSET);
  // Cancelling above only stops *future* fires — if this schedule's alarm is ringing right
  // now (e.g. the dose was marked taken from the app UI while the alarm sounded), it keeps
  // ringing until told to stop explicitly.
  stopNativeAlarmRinging();

  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  // Mirrors AlarmReceiver.rearmNextDay's own end-date guard: don't arm a day the chain
  // wouldn't have rearmed itself into anyway.
  if (schedule.endDate && todayDateString(tomorrow) > schedule.endDate) return;

  const [hourStr, minuteStr] = schedule.timeOfDay.split(':');
  const hour = Number(hourStr);
  const minute = Number(minuteStr);
  scheduleNativeAlarm({
    triggerAtMillis: tomorrowOccurrenceMillis(hour, minute),
    requestCode: schedule.id,
    scheduleId: schedule.id,
    medicationId: schedule.medicationId,
    medicationName: schedule.medicationName,
    dosage: schedule.dosage,
    isPrimary: true,
    hour,
    minute,
    recurrenceType: schedule.recurrenceType,
    daysOfWeek: encodeDaysOfWeek(schedule.daysOfWeek),
    startDate: schedule.startDate ?? '',
    endDate: schedule.endDate ?? '',
  });
}

/** Re-arms every enabled schedule's native alarm. Android only — call on app start as a
 * backstop for cases the native BootReceiver can't cover (e.g. a dev-client reinstall, which
 * doesn't fire BOOT_COMPLETED); reboots are already handled immediately by BootReceiver
 * reading the DB directly. No-op on iOS. */
export async function rearmAllScheduleAlarms(): Promise<void> {
  if (Platform.OS !== 'android') return;
  const schedules = await listAllEnabledSchedulesWithMedication();
  for (const schedule of schedules) {
    const [hourStr, minuteStr] = schedule.timeOfDay.split(':');
    armAndroidDailyAlarm(
      {
        scheduleId: schedule.id,
        medicationId: schedule.medicationId,
        medicationName: schedule.medicationName,
        dosage: schedule.dosage,
        timeOfDay: schedule.timeOfDay,
        recurrenceType: schedule.recurrenceType,
        daysOfWeek: schedule.daysOfWeek,
        startDate: schedule.startDate,
        endDate: schedule.endDate,
      },
      Number(hourStr),
      Number(minuteStr)
    );
  }
}

interface SnoozeParams {
  scheduleId: number;
  medicationId: number;
  medicationName: string;
  dosage: string | null;
  minutes?: number;
}

/** iOS-only manual snooze from a notification action — Android's snooze is handled entirely
 * natively (AlarmActivity), so this is never called there. */
export async function scheduleSnooze(params: SnoozeParams): Promise<string | null> {
  const Notifications = getNotifications();
  if (!Notifications) return null;
  try {
    return await Notifications.scheduleNotificationAsync({
      content: buildIosContent(params, false),
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
        seconds: (params.minutes ?? 10) * 60,
        repeats: false,
      },
    });
  } catch (error) {
    console.warn('[notifications] failed to schedule snooze:', error);
    return null;
  }
}

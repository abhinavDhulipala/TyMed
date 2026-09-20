import { Platform } from 'react-native';
import { setScheduleNotificationIds } from '@/src/db/schedules';
import { getNotifications, DOSE_CATEGORY, CHANNEL_ID } from './setup';

// Primary reminder plus two follow-up nudges, approximating Pillo's
// "keeps ringing until you take it" alarm within what local notifications
// can do in a plain Expo Go / managed app (see plan notes on this limit).
const NUDGE_OFFSETS_MINUTES = [0, 10, 20];

interface DoseReminderParams {
  scheduleId: number;
  medicationId: number;
  medicationName: string;
  dosage: string | null;
  timeOfDay: string; // "HH:MM"
}

function buildContent(params: DoseReminderParams, isPrimary: boolean) {
  return {
    title: isPrimary ? `Time for ${params.medicationName}` : `Reminder: ${params.medicationName}`,
    body: params.dosage ? `Dose: ${params.dosage}` : 'Time to take your dose',
    categoryIdentifier: DOSE_CATEGORY,
    data: { scheduleId: params.scheduleId, medicationId: params.medicationId },
    ...(Platform.OS === 'android' ? { channelId: CHANNEL_ID } : {}),
  };
}

/** Schedules the daily primary reminder + nudges for a schedule and persists their ids. */
export async function scheduleDoseReminders(params: DoseReminderParams): Promise<string[]> {
  const Notifications = getNotifications();
  if (!Notifications) return [];

  const [hourStr, minuteStr] = params.timeOfDay.split(':');
  const baseHour = Number(hourStr);
  const baseMinute = Number(minuteStr);

  try {
    const ids: string[] = [];
    for (const offset of NUDGE_OFFSETS_MINUTES) {
      const total = baseHour * 60 + baseMinute + offset;
      const hour = Math.floor(total / 60) % 24;
      const minute = total % 60;
      const id = await Notifications.scheduleNotificationAsync({
        content: buildContent(params, offset === 0),
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.CALENDAR,
          hour,
          minute,
          repeats: true,
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

export async function cancelDoseReminders(notificationIds: string[]): Promise<void> {
  const Notifications = getNotifications();
  if (!Notifications || notificationIds.length === 0) return;
  try {
    await Promise.all(notificationIds.map((id) => Notifications.cancelScheduledNotificationAsync(id)));
  } catch (error) {
    console.warn('[notifications] failed to cancel dose reminders:', error);
  }
}

interface SnoozeParams {
  scheduleId: number;
  medicationId: number;
  medicationName: string;
  dosage: string | null;
  minutes?: number;
}

export async function scheduleSnooze(params: SnoozeParams): Promise<string | null> {
  const Notifications = getNotifications();
  if (!Notifications) return null;
  try {
    return await Notifications.scheduleNotificationAsync({
      content: buildContent({ ...params, timeOfDay: '00:00' }, false),
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

import { Platform } from 'react-native';
import { getDb } from '@/src/db/client';
import { todayDateString } from '@/src/utils/date';

export const DOSE_CATEGORY = 'dose-reminder';
export const CHANNEL_ID = 'dose-reminders';

type NotificationsModule = typeof import('expo-notifications');
type NotificationData = { scheduleId?: number; medicationId?: number };

// expo-notifications' native module isn't present in Expo Go on Android (SDK 53+ dropped it
// there entirely, not just push) and may be missing in other constrained runtimes too. The
// module throws *during import*, so it must be loaded with a guarded `require` rather than a
// static `import` — a try/catch around calls into a statically-imported module can't catch a
// failure that happens while the module itself is being evaluated. Every other file in the app
// gets the module (or null) from `getNotifications()` below instead of importing it directly.
let notificationsModule: NotificationsModule | null = null;
let notificationsAvailable = true;

try {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  notificationsModule = require('expo-notifications') as NotificationsModule;
} catch (error) {
  notificationsAvailable = false;
  console.warn('[notifications] module unavailable in this runtime, reminders will be disabled:', error);
}

if (notificationsModule) {
  try {
    // Runs for every notification about to be shown, including daily-repeating
    // follow-up nudges. If the dose was already handled today, suppress it so
    // the app doesn't keep nagging after the user has already taken it.
    notificationsModule.setNotificationHandler({
      handleNotification: async (notification) => {
        const data = notification.request.content.data as NotificationData | undefined;
        if (data?.scheduleId) {
          try {
            const db = await getDb();
            const today = todayDateString();
            const row = await db.getFirstAsync<{ status: string }>(
              'SELECT status FROM intake_logs WHERE schedule_id = ? AND scheduled_date = ?',
              data.scheduleId,
              today
            );
            if (row && row.status !== 'pending') {
              return {
                shouldShowBanner: false,
                shouldShowList: false,
                shouldPlaySound: false,
                shouldSetBadge: false,
              };
            }
          } catch {
            // If the lookup fails, fall through and show the reminder anyway.
          }
        }
        return {
          shouldShowBanner: true,
          shouldShowList: true,
          shouldPlaySound: true,
          shouldSetBadge: false,
        };
      },
    });
  } catch (error) {
    notificationsAvailable = false;
    console.warn('[notifications] unavailable in this runtime, reminders will be disabled:', error);
  }
}

export function getNotifications(): NotificationsModule | null {
  return notificationsAvailable ? notificationsModule : null;
}

export function areNotificationsAvailable(): boolean {
  return notificationsAvailable;
}

export async function initNotifications(): Promise<void> {
  const Notifications = getNotifications();
  if (!Notifications) return;

  try {
    await Notifications.requestPermissionsAsync({
      ios: { allowAlert: true, allowBadge: true, allowSound: true },
    });

    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
        name: 'Dose reminders',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#2F6690',
        sound: 'default',
      });
    }

    await Notifications.setNotificationCategoryAsync(DOSE_CATEGORY, [
      { identifier: 'taken', buttonTitle: 'Taken', options: { opensAppToForeground: false } },
      { identifier: 'snooze', buttonTitle: 'Snooze 10 min', options: { opensAppToForeground: false } },
    ]);
  } catch (error) {
    notificationsAvailable = false;
    console.warn('[notifications] failed to initialize, reminders will be disabled:', error);
  }
}

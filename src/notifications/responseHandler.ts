import { markDose } from '@/src/db/actions';
import { findOrCreateTodayLogForSchedule } from '@/src/db/logs';
import { getMedication } from '@/src/db/medications';
import { captureException } from '@/src/observability/sentry';
import { scheduleSnooze } from './scheduler';
import { getNotifications } from './setup';

type NotificationData = { scheduleId?: number; medicationId?: number };

/** Wires the "Taken" / "Snooze" notification action buttons to the database. Call once, near app start. */
export function registerNotificationResponseHandler(): () => void {
  const Notifications = getNotifications();
  if (!Notifications) return () => {};

  try {
    const subscription = Notifications.addNotificationResponseReceivedListener(async (response) => {
      const data = response.notification.request.content.data as NotificationData | undefined;
      if (!data?.scheduleId || !data?.medicationId) return;

      try {
        if (response.actionIdentifier === 'taken') {
          const log = await findOrCreateTodayLogForSchedule(data.scheduleId);
          await markDose(log.id, 'taken');
        } else if (response.actionIdentifier === 'snooze') {
          const medication = await getMedication(data.medicationId);
          await scheduleSnooze({
            scheduleId: data.scheduleId,
            medicationId: data.medicationId,
            medicationName: medication?.name ?? 'your medication',
            dosage: medication?.dosage ?? null,
          });
        }
      } catch (error) {
        // An unhandled throw here is an unhandled promise rejection with no user-visible
        // effect — the notification action button would silently do nothing.
        console.warn('[notifications] failed to handle response action:', error);
        captureException(error);
      }
    });

    return () => subscription.remove();
  } catch (error) {
    console.warn('[notifications] failed to register response handler:', error);
    return () => {};
  }
}

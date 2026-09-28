import { useEffect, useRef } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { markDose } from '@/src/db/actions';
import { findOrCreateTodayLogForSchedule } from '@/src/db/logs';
import { captureException } from '@/src/observability/sentry';
import { colors } from '@/src/theme';

/**
 * Landing point for the native alarm activity's Taken/Snooze buttons
 * (tymed://dose-action?action=taken|snooze&scheduleId=&medicationId=).
 * Snooze itself is already handled natively (Chain B alarm armed before this screen
 * ever loads) — this just needs to record Taken and bounce back to Today.
 */
export default function DoseActionScreen() {
  const { action, scheduleId } = useLocalSearchParams<{
    action?: string;
    scheduleId?: string;
    medicationId?: string;
  }>();
  const router = useRouter();
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    (async () => {
      try {
        const id = Number(scheduleId);
        if (action === 'taken' && Number.isFinite(id)) {
          const log = await findOrCreateTodayLogForSchedule(id);
          await markDose(log.id, 'taken');
        }
      } catch (error) {
        // An unhandled throw here would leave the user stuck on this screen's spinner
        // forever, with the dose silently never marked taken — always bounce home instead.
        console.warn('[dose-action] failed to mark dose taken:', error);
        captureException(error);
      }
      router.replace('/');
    })();
  }, [action, scheduleId, router]);

  return (
    <View style={styles.container}>
      <ActivityIndicator color={colors.primary} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.background,
  },
});

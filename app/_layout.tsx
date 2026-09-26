import { useEffect } from 'react';
import { Platform } from 'react-native';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { getFollowUpMinutes, getUse24HourFormat } from '@/src/db/settings';
import { setNativeFollowUpMinutes } from '@/src/native/alarmModule';
import { initNotifications } from '@/src/notifications/setup';
import { registerNotificationResponseHandler } from '@/src/notifications/responseHandler';
import { rearmAllScheduleAlarms } from '@/src/notifications/scheduler';
import { initSentry, wrapRootComponent } from '@/src/observability/sentry';
import { setTimeFormatPreference } from '@/src/utils/date';

initSentry();

function RootLayout() {
  useEffect(() => {
    initNotifications();
    const unsubscribe = registerNotificationResponseHandler();
    getUse24HourFormat().then(setTimeFormatPreference);

    if (Platform.OS === 'android') {
      // Backstop for a native BootReceiver (modules/tymed-alarm) that already re-arms alarms
      // immediately on reboot by reading the DB directly — this covers anything it missed
      // (e.g. a dev-client reinstall, which doesn't fire BOOT_COMPLETED).
      rearmAllScheduleAlarms();
      getFollowUpMinutes().then(setNativeFollowUpMinutes);
    }

    return unsubscribe;
  }, []);

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" />
      </Stack>
    </SafeAreaProvider>
  );
}

export default wrapRootComponent(RootLayout);

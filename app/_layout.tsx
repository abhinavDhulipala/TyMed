import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { initNotifications } from '@/src/notifications/setup';
import { registerNotificationResponseHandler } from '@/src/notifications/responseHandler';

export default function RootLayout() {
  useEffect(() => {
    initNotifications();
    const unsubscribe = registerNotificationResponseHandler();
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

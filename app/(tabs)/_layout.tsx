import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import type { ColorValue } from 'react-native';
import { colors } from '@/src/theme';

type IoniconName = ComponentProps<typeof Ionicons>['name'];

function TabIcon(name: IoniconName) {
  return ({ color, size }: { color: ColorValue; size: number }) => (
    <Ionicons name={name} color={color as string} size={size} />
  );
}

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.text,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Today', tabBarIcon: TabIcon('today-outline') }} />
      <Tabs.Screen
        name="medications"
        options={{ title: 'Medications', headerShown: false, tabBarIcon: TabIcon('medkit-outline') }}
      />
      <Tabs.Screen
        name="assistant"
        options={{ title: 'Assistant', tabBarIcon: TabIcon('chatbubble-ellipses-outline') }}
      />
      <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarIcon: TabIcon('settings-outline') }} />
    </Tabs>
  );
}

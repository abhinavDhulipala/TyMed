import { Stack } from 'expo-router';
import { colors } from '@/src/theme';

export default function MedicationsStackLayout() {
  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.text,
      }}
    >
      <Stack.Screen name="index" options={{ title: 'Medications' }} />
      <Stack.Screen name="new" options={{ title: 'Add medication', presentation: 'modal' }} />
      <Stack.Screen name="[id]" options={{ title: 'Edit medication' }} />
    </Stack>
  );
}

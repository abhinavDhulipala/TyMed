import { FlatList, StyleSheet } from 'react-native';
import { Stack, useLocalSearchParams } from 'expo-router';
import { DoseRow } from '@/src/components/DoseRow';
import { EmptyState } from '@/src/components/EmptyState';
import { useDayDoses } from '@/src/hooks/useDayDoses';
import { colors, spacing } from '@/src/theme';
import { formatFullDateLabel } from '@/src/utils/date';

export default function DayDetailScreen() {
  const { date } = useLocalSearchParams<{ date: string }>();
  const dateStr = date ?? '';
  const { doses, loading, refresh } = useDayDoses(dateStr);

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: formatFullDateLabel(dateStr),
          headerStyle: { backgroundColor: colors.background },
          headerTintColor: colors.text,
        }}
      />
      <FlatList
        style={styles.container}
        contentContainerStyle={styles.list}
        data={doses}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <DoseRow dose={item} onChange={refresh} />}
        ListEmptyComponent={
          !loading ? <EmptyState title="Nothing scheduled" subtitle="No doses were scheduled on this day." /> : null
        }
      />
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  list: { padding: spacing.md, flexGrow: 1 },
});

import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useDayDoses } from '@/src/hooks/useDayDoses';
import { AdherenceCalendar } from '@/src/components/AdherenceCalendar';
import { DoseRow } from '@/src/components/DoseRow';
import { EmptyState } from '@/src/components/EmptyState';
import { colors, spacing } from '@/src/theme';
import { todayDateString } from '@/src/utils/date';

// The home screen is just today's day-detail view, plus the calendar and an empty-state CTA —
// a day is a day, today isn't a special case for the data or the row UI.
export default function TodayScreen() {
  const today = todayDateString();
  const { doses, loading, refresh } = useDayDoses(today);
  const router = useRouter();

  return (
    <View style={styles.container}>
      <FlatList
        contentContainerStyle={styles.list}
        data={doses}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <DoseRow dose={item} onChange={refresh} />}
        ListHeaderComponent={<AdherenceCalendar />}
        ListEmptyComponent={
          !loading ? (
            <EmptyState
              title="No medications scheduled yet"
              subtitle="Add a medication with reminder times to see today's doses here."
            />
          ) : null
        }
      />
      {!loading && doses.length === 0 ? (
        <Pressable
          style={styles.addButton}
          onPress={() => {
            // Seed the Medications tab's own stack with its list first, so switching tabs
            // or pressing back afterwards lands on the list instead of a stale form.
            router.push('/medications');
            router.push('/medications/new');
          }}
        >
          <Text style={styles.addButtonText}>Add a medication</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  list: {
    padding: spacing.md,
    flexGrow: 1,
  },
  addButton: {
    marginHorizontal: spacing.md,
    marginBottom: spacing.md,
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  addButtonText: {
    color: '#fff',
    fontWeight: '700',
  },
});

import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useTodayDoses } from '@/src/hooks/useTodayDoses';
import { DoseListItem } from '@/src/components/DoseListItem';
import { EmptyState } from '@/src/components/EmptyState';
import { markDose } from '@/src/db/actions';
import { colors, spacing } from '@/src/theme';
import type { DoseStatus } from '@/src/types';

export default function TodayScreen() {
  const { doses, loading, refresh } = useTodayDoses();
  const router = useRouter();

  const handleMark = async (logId: number, status: DoseStatus) => {
    await markDose(logId, status);
    await refresh();
  };

  return (
    <View style={styles.container}>
      <FlatList
        contentContainerStyle={styles.list}
        data={doses}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => <DoseListItem dose={item} onMark={(status) => handleMark(item.id, status)} />}
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

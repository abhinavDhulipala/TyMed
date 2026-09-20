import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useMedications } from '@/src/hooks/useMedications';
import { MedicationCard } from '@/src/components/MedicationCard';
import { EmptyState } from '@/src/components/EmptyState';
import { colors, spacing } from '@/src/theme';

export default function MedicationsScreen() {
  const { medications, loading } = useMedications();
  const router = useRouter();

  return (
    <View style={styles.container}>
      <FlatList
        contentContainerStyle={styles.list}
        data={medications}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <MedicationCard medication={item} onPress={() => router.push(`/medications/${item.id}`)} />
        )}
        ListEmptyComponent={
          !loading ? <EmptyState title="No medications yet" subtitle="Tap + to add your first medication." /> : null
        }
      />
      <Pressable style={styles.fab} onPress={() => router.push('/medications/new')}>
        <Text style={styles.fabText}>+</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  list: { padding: spacing.md, paddingBottom: 96, flexGrow: 1 },
  fab: {
    position: 'absolute',
    right: spacing.lg,
    bottom: spacing.lg,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
  },
  fabText: {
    color: '#fff',
    fontSize: 28,
    lineHeight: 30,
    fontWeight: '600',
  },
});

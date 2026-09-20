import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useHistory } from '@/src/hooks/useHistory';
import { EmptyState } from '@/src/components/EmptyState';
import { colors, radii, spacing } from '@/src/theme';
import { formatDateLabel, formatTime } from '@/src/utils/date';
import type { DoseWithMedication } from '@/src/types';

export default function HistoryScreen() {
  const { history, loading } = useHistory();

  return (
    <FlatList
      style={styles.container}
      contentContainerStyle={styles.list}
      data={history}
      keyExtractor={(item) => String(item.id)}
      renderItem={({ item }) => <HistoryRow dose={item} />}
      ListEmptyComponent={
        !loading ? (
          <EmptyState title="No history yet" subtitle="Doses you mark taken or skipped will show up here." />
        ) : null
      }
    />
  );
}

function HistoryRow({ dose }: { dose: DoseWithMedication }) {
  const isTaken = dose.status === 'taken';
  return (
    <View style={styles.row}>
      <View style={[styles.dot, isTaken ? styles.dotTaken : styles.dotSkipped]} />
      <View style={styles.rowContent}>
        <Text style={styles.rowTitle}>{dose.medicationName}</Text>
        <Text style={styles.rowSubtitle}>
          {formatDateLabel(dose.scheduledDate)} · {formatTime(dose.scheduledTime)} · {isTaken ? 'Taken' : 'Skipped'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  list: { padding: spacing.md, flexGrow: 1 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.sm,
    gap: spacing.sm,
  },
  dot: { width: 10, height: 10, borderRadius: 5 },
  dotTaken: { backgroundColor: colors.success },
  dotSkipped: { backgroundColor: colors.danger },
  rowContent: { flex: 1 },
  rowTitle: { fontSize: 16, fontWeight: '700', color: colors.text },
  rowSubtitle: { fontSize: 13, color: colors.textMuted, marginTop: 2 },
});

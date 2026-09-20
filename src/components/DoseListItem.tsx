import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing } from '@/src/theme';
import { formatTime } from '@/src/utils/date';
import type { DoseStatus, DoseWithMedication } from '@/src/types';

interface Props {
  dose: DoseWithMedication;
  onMark: (status: DoseStatus) => void;
}

const STATUS_LABEL: Record<DoseStatus, string> = {
  pending: 'Pending',
  taken: 'Taken',
  skipped: 'Skipped',
};

export function DoseListItem({ dose, onMark }: Props) {
  return (
    <View style={styles.card}>
      <View style={styles.row}>
        <Text style={styles.time}>{formatTime(dose.scheduledTime)}</Text>
        <StatusPill status={dose.status} />
      </View>
      <Text style={styles.name}>{dose.medicationName}</Text>
      {dose.dosage ? <Text style={styles.dosage}>{dose.dosage}</Text> : null}

      {dose.status === 'pending' ? (
        <View style={styles.actions}>
          <Pressable style={[styles.button, styles.takenButton]} onPress={() => onMark('taken')}>
            <Text style={styles.takenButtonText}>Mark taken</Text>
          </Pressable>
          <Pressable style={[styles.button, styles.skipButton]} onPress={() => onMark('skipped')}>
            <Text style={styles.skipButtonText}>Skip</Text>
          </Pressable>
        </View>
      ) : (
        <Pressable style={styles.undoButton} onPress={() => onMark('pending')}>
          <Text style={styles.undoButtonText}>Undo</Text>
        </Pressable>
      )}
    </View>
  );
}

function StatusPill({ status }: { status: DoseStatus }) {
  const style =
    status === 'taken' ? styles.pillTaken : status === 'skipped' ? styles.pillSkipped : styles.pillPending;
  const textStyle =
    status === 'taken'
      ? styles.pillTextTaken
      : status === 'skipped'
        ? styles.pillTextSkipped
        : styles.pillTextPending;
  return (
    <View style={[styles.pill, style]}>
      <Text style={[styles.pillText, textStyle]}>{STATUS_LABEL[status]}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.card,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.md,
    marginBottom: spacing.sm,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  time: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.textMuted,
  },
  name: {
    fontSize: 18,
    fontWeight: '700',
    color: colors.text,
    marginTop: spacing.xs,
  },
  dosage: {
    fontSize: 14,
    color: colors.textMuted,
    marginTop: 2,
  },
  actions: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  button: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: radii.sm,
    alignItems: 'center',
  },
  takenButton: {
    backgroundColor: colors.primary,
  },
  takenButtonText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  skipButton: {
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
  },
  skipButtonText: {
    color: colors.textMuted,
    fontWeight: '600',
  },
  undoButton: {
    marginTop: spacing.md,
    alignSelf: 'flex-start',
  },
  undoButtonText: {
    color: colors.primary,
    fontWeight: '600',
  },
  pill: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
    borderRadius: radii.sm,
  },
  pillPending: { backgroundColor: colors.background },
  pillTaken: { backgroundColor: colors.successMuted },
  pillSkipped: { backgroundColor: colors.dangerMuted },
  pillText: { fontSize: 12, fontWeight: '700' },
  pillTextPending: { color: colors.textMuted },
  pillTextTaken: { color: colors.success },
  pillTextSkipped: { color: colors.danger },
});

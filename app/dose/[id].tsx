import { useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { markDose } from '@/src/db/actions';
import { setLogTakenAt } from '@/src/db/logs';
import { useDose } from '@/src/hooks/useDose';
import { useTimeFormat } from '@/src/hooks/useTimeFormat';
import { colors, radii, spacing } from '@/src/theme';
import { formatFullDateLabel, formatTime } from '@/src/utils/date';
import type { DoseStatus } from '@/src/types';

function isoToHHMM(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

const STATUS_LABEL: Record<DoseStatus, string> = {
  pending: 'Pending',
  taken: 'Taken',
  skipped: 'Skipped',
};

export default function DoseDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const logId = Number(id);
  const router = useRouter();
  const { dose, loading, refresh } = useDose(logId);
  const [showTakenAtPicker, setShowTakenAtPicker] = useState(false);
  const is24Hour = useTimeFormat();

  const handleMark = async (status: DoseStatus) => {
    await markDose(logId, status);
    await refresh();
  };

  const handleTakenAtChange = async (event: DateTimePickerEvent, selected?: Date) => {
    setShowTakenAtPicker(Platform.OS === 'ios');
    if (event.type === 'set' && selected && dose?.takenAt) {
      const next = new Date(dose.takenAt);
      next.setHours(selected.getHours(), selected.getMinutes(), 0, 0);
      await setLogTakenAt(logId, next.toISOString());
      await refresh();
    }
  };

  if (loading || !dose) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: dose.medicationName,
          headerStyle: { backgroundColor: colors.background },
          headerTintColor: colors.text,
        }}
      />
      <View style={styles.container}>
        <View style={styles.card}>
          <Text style={styles.dateLabel}>
            {formatFullDateLabel(dose.scheduledDate)} · {formatTime(dose.scheduledTime)}
          </Text>
          <Text style={styles.name}>{dose.medicationName}</Text>
          {dose.dosage ? <Text style={styles.dosage}>{dose.dosage}</Text> : null}

          <View style={[styles.statusPill, styles[`pill_${dose.status}`]]}>
            <Text style={[styles.statusPillText, styles[`pillText_${dose.status}`]]}>
              {STATUS_LABEL[dose.status]}
            </Text>
          </View>

          {dose.status === 'taken' && dose.takenAt ? (
            <View style={styles.takenAtRow}>
              <Text style={styles.takenAtText}>Taken at {formatTime(isoToHHMM(dose.takenAt))}</Text>
              <Pressable onPress={() => setShowTakenAtPicker(true)}>
                <Text style={styles.editLink}>Edit</Text>
              </Pressable>
            </View>
          ) : null}

          {showTakenAtPicker && dose.takenAt ? (
            <DateTimePicker
              value={new Date(dose.takenAt)}
              mode="time"
              display="default"
              is24Hour={is24Hour}
              onChange={handleTakenAtChange}
            />
          ) : null}
        </View>

        {dose.status === 'pending' ? (
          <View style={styles.actions}>
            <Pressable style={[styles.button, styles.takenButton]} onPress={() => handleMark('taken')}>
              <Text style={styles.takenButtonText}>Mark taken</Text>
            </Pressable>
            <Pressable style={[styles.button, styles.skipButton]} onPress={() => handleMark('skipped')}>
              <Text style={styles.skipButtonText}>Skip</Text>
            </Pressable>
          </View>
        ) : (
          <Pressable style={styles.undoButton} onPress={() => handleMark('pending')}>
            <Text style={styles.undoButtonText}>Undo — mark pending again</Text>
          </Pressable>
        )}

        <Pressable style={styles.editMedButton} onPress={() => router.push(`/medications/${dose.medicationId}`)}>
          <Text style={styles.editMedButtonText}>Edit {dose.medicationName}</Text>
        </Pressable>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
  container: { flex: 1, backgroundColor: colors.background, padding: spacing.md },
  card: {
    backgroundColor: colors.card,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.lg,
  },
  dateLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textMuted,
    marginBottom: spacing.xs,
  },
  name: {
    fontSize: 24,
    fontWeight: '700',
    color: colors.text,
  },
  dosage: {
    fontSize: 16,
    color: colors.textMuted,
    marginTop: 2,
  },
  statusPill: {
    alignSelf: 'flex-start',
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    borderRadius: radii.sm,
    marginTop: spacing.md,
  },
  pill_pending: { backgroundColor: colors.background },
  pill_taken: { backgroundColor: colors.successMuted },
  pill_skipped: { backgroundColor: colors.dangerMuted },
  statusPillText: { fontSize: 13, fontWeight: '700' },
  pillText_pending: { color: colors.textMuted },
  pillText_taken: { color: colors.success },
  pillText_skipped: { color: colors.danger },
  takenAtRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  takenAtText: {
    fontSize: 14,
    color: colors.textMuted,
  },
  editLink: {
    fontSize: 14,
    color: colors.primary,
    fontWeight: '600',
  },
  actions: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  button: {
    flex: 1,
    paddingVertical: spacing.md,
    borderRadius: radii.sm,
    alignItems: 'center',
  },
  takenButton: { backgroundColor: colors.primary },
  takenButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 16 },
  skipButton: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
  },
  skipButtonText: { color: colors.textMuted, fontWeight: '600', fontSize: 16 },
  undoButton: {
    alignItems: 'center',
    paddingVertical: spacing.md,
    marginBottom: spacing.md,
  },
  undoButtonText: { color: colors.primary, fontWeight: '600', fontSize: 16 },
  editMedButton: {
    alignItems: 'center',
    paddingVertical: spacing.sm,
  },
  editMedButtonText: { color: colors.textMuted, fontWeight: '600' },
});

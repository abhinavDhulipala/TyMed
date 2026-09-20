import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing } from '@/src/theme';
import { PillCountBadge } from './PillCountBadge';
import type { Medication } from '@/src/types';

interface Props {
  medication: Medication;
  onPress: () => void;
}

export function MedicationCard({ medication, onPress }: Props) {
  return (
    <Pressable style={styles.card} onPress={onPress}>
      <View style={styles.header}>
        <Text style={styles.name}>{medication.name}</Text>
        {medication.dosage ? <Text style={styles.dosage}>{medication.dosage}</Text> : null}
      </View>
      {medication.form ? <Text style={styles.form}>{medication.form}</Text> : null}
      <PillCountBadge pillsRemaining={medication.pillsRemaining} refillThreshold={medication.refillThreshold} />
    </Pressable>
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
    gap: spacing.xs,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  name: {
    fontSize: 18,
    fontWeight: '700',
    color: colors.text,
  },
  dosage: {
    fontSize: 14,
    color: colors.textMuted,
  },
  form: {
    fontSize: 13,
    color: colors.textMuted,
    textTransform: 'capitalize',
  },
});

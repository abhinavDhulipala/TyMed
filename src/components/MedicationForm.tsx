import { useState, type ReactNode } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { colors, radii, spacing } from '@/src/theme';
import { TimeOfDayRow } from './TimeOfDayRow';
import type { MedicationInput } from '@/src/types';

export interface MedicationFormValues {
  name: string;
  dosage: string;
  form: string;
  notes: string;
  pillsRemaining: string;
  refillThreshold: string;
  times: string[];
  daysOfWeek: number[]; // 0=Sun..6=Sat; all 7 = every day
}

export const ALL_DAYS = [0, 1, 2, 3, 4, 5, 6];
const DAY_CHIP_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
const DAY_FULL_LABELS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

export const DEFAULT_FORM_VALUES: MedicationFormValues = {
  name: '',
  dosage: '',
  form: '',
  notes: '',
  pillsRemaining: '',
  refillThreshold: '',
  times: ['08:00'],
  daysOfWeek: ALL_DAYS,
};

interface Props {
  initial?: MedicationFormValues;
  submitLabel: string;
  submitting?: boolean;
  onSubmit: (input: MedicationInput, times: string[], daysOfWeek: number[]) => Promise<void>;
  onDelete?: () => Promise<void>;
}

function toMedicationInput(values: MedicationFormValues): MedicationInput | null {
  const name = values.name.trim();
  if (!name) return null;

  const pillsRemaining = values.pillsRemaining.trim() === '' ? null : Number(values.pillsRemaining);
  const refillThreshold = values.refillThreshold.trim() === '' ? null : Number(values.refillThreshold);

  return {
    name,
    dosage: values.dosage.trim() || null,
    form: values.form.trim() || null,
    notes: values.notes.trim() || null,
    pillsRemaining: Number.isFinite(pillsRemaining) ? pillsRemaining : null,
    refillThreshold: Number.isFinite(refillThreshold) ? refillThreshold : null,
  };
}

export function MedicationForm({ initial, submitLabel, submitting, onSubmit, onDelete }: Props) {
  const [values, setValues] = useState<MedicationFormValues>(initial ?? DEFAULT_FORM_VALUES);

  const setField = <K extends keyof MedicationFormValues>(key: K, value: MedicationFormValues[K]) => {
    setValues((prev) => ({ ...prev, [key]: value }));
  };

  const addTime = () => setField('times', [...values.times, '08:00']);
  const removeTime = (index: number) =>
    setField(
      'times',
      values.times.filter((_, i) => i !== index)
    );
  const updateTime = (index: number, time: string) =>
    setField(
      'times',
      values.times.map((t, i) => (i === index ? time : t))
    );

  const toggleDay = (day: number) => {
    const isSelected = values.daysOfWeek.includes(day);
    if (isSelected && values.daysOfWeek.length === 1) return; // keep at least one day active
    const next = isSelected
      ? values.daysOfWeek.filter((d) => d !== day)
      : [...values.daysOfWeek, day].sort();
    setField('daysOfWeek', next);
  };

  const handleSubmit = async () => {
    const input = toMedicationInput(values);
    if (!input) {
      Alert.alert('Name required', 'Please enter a medication name.');
      return;
    }
    await onSubmit(input, values.times, values.daysOfWeek);
  };

  const handleDelete = () => {
    if (!onDelete) return;
    Alert.alert('Delete medication', 'This will remove the medication, its schedule, and its reminders.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => onDelete() },
    ]);
  };

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Field label="Name">
        <TextInput
          style={styles.input}
          value={values.name}
          onChangeText={(t) => setField('name', t)}
          placeholder="e.g. Lisinopril"
          placeholderTextColor={colors.textMuted}
        />
      </Field>

      <Field label="Dosage">
        <TextInput
          style={styles.input}
          value={values.dosage}
          onChangeText={(t) => setField('dosage', t)}
          placeholder="e.g. 10mg"
          placeholderTextColor={colors.textMuted}
        />
      </Field>

      <Field label="Form">
        <TextInput
          style={styles.input}
          value={values.form}
          onChangeText={(t) => setField('form', t)}
          placeholder="e.g. tablet, capsule, liquid"
          placeholderTextColor={colors.textMuted}
        />
      </Field>

      <Field label="Reminder times">
        {values.times.map((time, index) => (
          <TimeOfDayRow
            key={index}
            value={time}
            onChange={(t) => updateTime(index, t)}
            onRemove={() => removeTime(index)}
          />
        ))}
        <Pressable style={styles.addTimeButton} onPress={addTime}>
          <Text style={styles.addTimeText}>+ Add time</Text>
        </Pressable>
      </Field>

      <Field label="Repeats on">
        <View style={styles.dayRow}>
          {DAY_CHIP_LABELS.map((label, day) => {
            const selected = values.daysOfWeek.includes(day);
            return (
              <Pressable
                key={day}
                style={[styles.dayChip, selected && styles.dayChipSelected]}
                onPress={() => toggleDay(day)}
                accessibilityLabel={DAY_FULL_LABELS[day]}
              >
                <Text style={[styles.dayChipText, selected && styles.dayChipTextSelected]}>{label}</Text>
              </Pressable>
            );
          })}
        </View>
        <Text style={styles.help}>
          {values.daysOfWeek.length === 7
            ? 'Every day'
            : values.daysOfWeek.map((d) => DAY_FULL_LABELS[d].slice(0, 3)).join(', ')}
        </Text>
      </Field>

      <View style={styles.pillRow}>
        <Field label="Pills remaining" style={styles.pillField}>
          <TextInput
            style={styles.input}
            value={values.pillsRemaining}
            onChangeText={(t) => setField('pillsRemaining', t.replace(/[^0-9]/g, ''))}
            placeholder="e.g. 30"
            placeholderTextColor={colors.textMuted}
            keyboardType="number-pad"
          />
        </Field>
        <Field label="Refill at" style={styles.pillField}>
          <TextInput
            style={styles.input}
            value={values.refillThreshold}
            onChangeText={(t) => setField('refillThreshold', t.replace(/[^0-9]/g, ''))}
            placeholder="e.g. 5"
            placeholderTextColor={colors.textMuted}
            keyboardType="number-pad"
          />
        </Field>
      </View>

      <Field label="Notes">
        <TextInput
          style={[styles.input, styles.notesInput]}
          value={values.notes}
          onChangeText={(t) => setField('notes', t)}
          placeholder="Optional notes"
          placeholderTextColor={colors.textMuted}
          multiline
        />
      </Field>

      <Pressable style={styles.submitButton} onPress={handleSubmit} disabled={submitting}>
        <Text style={styles.submitText}>{submitting ? 'Saving…' : submitLabel}</Text>
      </Pressable>

      {onDelete ? (
        <Pressable style={styles.deleteButton} onPress={handleDelete}>
          <Text style={styles.deleteText}>Delete medication</Text>
        </Pressable>
      ) : null}
    </ScrollView>
  );
}

function Field({ label, children, style }: { label: string; children: ReactNode; style?: object }) {
  return (
    <View style={[styles.field, style]}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: spacing.md,
    paddingBottom: spacing.xl * 2,
  },
  field: {
    marginBottom: spacing.md,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textMuted,
    marginBottom: spacing.xs,
  },
  input: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    fontSize: 16,
    color: colors.text,
  },
  notesInput: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  pillRow: {
    flexDirection: 'row',
    gap: spacing.md,
  },
  pillField: {
    flex: 1,
  },
  addTimeButton: {
    alignSelf: 'flex-start',
    paddingVertical: spacing.xs,
  },
  addTimeText: {
    color: colors.primary,
    fontWeight: '700',
  },
  dayRow: {
    flexDirection: 'row',
    gap: spacing.xs,
    marginBottom: spacing.xs,
  },
  dayChip: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
  },
  dayChipSelected: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  dayChipText: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.textMuted,
  },
  dayChipTextSelected: {
    color: '#FFFFFF',
  },
  help: {
    fontSize: 13,
    color: colors.textMuted,
  },
  submitButton: {
    backgroundColor: colors.primary,
    borderRadius: radii.sm,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.md,
  },
  submitText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 16,
  },
  deleteButton: {
    alignItems: 'center',
    marginTop: spacing.lg,
  },
  deleteText: {
    color: colors.danger,
    fontWeight: '600',
  },
});

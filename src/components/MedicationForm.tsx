import { useState, type ReactNode } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { colors, radii, spacing } from '@/src/theme';
import { TimeOfDayRow } from './TimeOfDayRow';
import type { DuplicateCheck } from '@/src/db/medications';
import type { MedicationInput, RecurrenceType } from '@/src/types';
import { todayDateString } from '@/src/utils/date';

export interface MedicationFormValues {
  name: string;
  dosage: string;
  form: string;
  notes: string;
  pillsRemaining: string;
  refillThreshold: string;
  times: string[];
  recurrenceType: RecurrenceType;
  daysOfWeek: number[]; // 0=Sun..6=Sat; only used when recurrenceType === 'weekly'
  hasEndDate: boolean;
  endDate: string; // "YYYY-MM-DD"; only used when hasEndDate
}

/** What the form hands back on submit — the screen resolves this into DB fields (in particular
 * the monthly start-date anchor, which depends on whether an existing schedule already has one). */
export interface RecurrenceSelection {
  recurrenceType: RecurrenceType;
  daysOfWeek: number[] | null;
  endDate: string | null;
}

export const ALL_DAYS = [0, 1, 2, 3, 4, 5, 6];
const DAY_CHIP_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
const DAY_FULL_LABELS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const RECURRENCE_OPTIONS: { type: RecurrenceType; label: string }[] = [
  { type: 'daily', label: 'Daily' },
  { type: 'weekly', label: 'Weekly' },
  { type: 'monthly', label: 'Monthly' },
];

function defaultEndDate(): string {
  const d = new Date();
  d.setDate(d.getDate() + 30);
  return todayDateString(d);
}

export const DEFAULT_FORM_VALUES: MedicationFormValues = {
  name: '',
  dosage: '',
  form: '',
  notes: '',
  pillsRemaining: '',
  refillThreshold: '',
  times: ['08:00'],
  recurrenceType: 'daily',
  daysOfWeek: ALL_DAYS,
  hasEndDate: false,
  endDate: '',
};

interface Props {
  initial?: MedicationFormValues;
  submitLabel: string;
  submitting?: boolean;
  onSubmit: (input: MedicationInput, times: string[], recurrence: RecurrenceSelection) => Promise<void>;
  onDelete?: () => Promise<void>;
  /** Checks for an existing medication that collides with this one, so the form can block an
   * exact duplicate and warn on a likely-mistake partial match before saving. */
  checkDuplicate: (input: Pick<MedicationInput, 'name' | 'dosage' | 'form'>) => Promise<DuplicateCheck>;
  /** Day-of-month (1-31) the "Monthly" option repeats on — today's for a new medication, or the
   * existing schedule's anchor when editing one that's already monthly. */
  monthlyAnchorDay: number;
}

function confirmAsync(title: string, message: string, confirmLabel: string): Promise<boolean> {
  return new Promise((resolve) => {
    Alert.alert(title, message, [
      { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
      { text: confirmLabel, onPress: () => resolve(true) },
    ]);
  });
}

function describeMedication(dosage: string | null, form: string | null): string {
  return [dosage, form].filter(Boolean).join(', ') || 'no dosage or form set';
}

function ordinal(n: number): string {
  const suffixes: Record<number, string> = { 1: 'st', 2: 'nd', 3: 'rd' };
  const suffix = n % 100 >= 11 && n % 100 <= 13 ? 'th' : (suffixes[n % 10] ?? 'th');
  return `${n}${suffix}`;
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

export function MedicationForm({
  initial,
  submitLabel,
  submitting,
  onSubmit,
  onDelete,
  checkDuplicate,
  monthlyAnchorDay,
}: Props) {
  const [values, setValues] = useState<MedicationFormValues>(initial ?? DEFAULT_FORM_VALUES);
  const [checking, setChecking] = useState(false);
  const [showEndDatePicker, setShowEndDatePicker] = useState(false);

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

  const toggleHasEndDate = (value: boolean) => {
    setField('hasEndDate', value);
    if (value && !values.endDate) {
      setField('endDate', defaultEndDate());
    }
  };

  const handleEndDateChange = (event: DateTimePickerEvent, selected?: Date) => {
    setShowEndDatePicker(Platform.OS === 'ios');
    if (event.type === 'set' && selected) {
      setField('endDate', todayDateString(selected));
    }
  };

  const handleSubmit = async () => {
    const input = toMedicationInput(values);
    if (!input) {
      Alert.alert('Name required', 'Please enter a medication name.');
      return;
    }

    setChecking(true);
    const duplicate = await checkDuplicate(input);
    setChecking(false);

    if (duplicate.exact) {
      Alert.alert(
        'Already added',
        `${input.name} (${describeMedication(input.dosage, input.form)}) is already in your medications. Edit the existing one instead of adding it again.`
      );
      return;
    }

    if (duplicate.partial.length > 0) {
      const existing = duplicate.partial[0];
      const proceed = await confirmAsync(
        'Similar medication exists',
        `You already have "${existing.name}" (${describeMedication(existing.dosage, existing.form)}). This one is (${describeMedication(input.dosage, input.form)}) — different enough to be a mistake. Add it as a separate medication anyway?`,
        'Add anyway'
      );
      if (!proceed) return;
    }

    await onSubmit(input, values.times, {
      recurrenceType: values.recurrenceType,
      daysOfWeek: values.recurrenceType === 'weekly' ? values.daysOfWeek : null,
      endDate: values.hasEndDate ? values.endDate : null,
    });
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

      <Field label="Repeats">
        <View style={styles.segmentedRow}>
          {RECURRENCE_OPTIONS.map((option) => {
            const selected = values.recurrenceType === option.type;
            return (
              <Pressable
                key={option.type}
                style={[styles.segment, selected && styles.segmentSelected]}
                onPress={() => setField('recurrenceType', option.type)}
              >
                <Text style={[styles.segmentText, selected && styles.segmentTextSelected]}>{option.label}</Text>
              </Pressable>
            );
          })}
        </View>

        {values.recurrenceType === 'weekly' ? (
          <>
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
          </>
        ) : null}

        {values.recurrenceType === 'monthly' ? (
          <Text style={styles.help}>Repeats on the {ordinal(monthlyAnchorDay)} of every month.</Text>
        ) : null}
      </Field>

      <Field label="Ends">
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>{values.hasEndDate ? 'On a specific date' : 'Never'}</Text>
          <Switch
            value={values.hasEndDate}
            onValueChange={toggleHasEndDate}
            trackColor={{ false: colors.border, true: colors.primary }}
            thumbColor="#FFFFFF"
          />
        </View>
        {values.hasEndDate ? (
          <>
            <Pressable style={styles.endDateButton} onPress={() => setShowEndDatePicker(true)}>
              <Text style={styles.endDateText}>{values.endDate || defaultEndDate()}</Text>
            </Pressable>
            {showEndDatePicker ? (
              <DateTimePicker
                value={values.endDate ? new Date(`${values.endDate}T00:00:00`) : new Date()}
                mode="date"
                display="default"
                minimumDate={new Date()}
                onChange={handleEndDateChange}
              />
            ) : null}
          </>
        ) : null}
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

      <Pressable style={styles.submitButton} onPress={handleSubmit} disabled={submitting || checking}>
        <Text style={styles.submitText}>{checking ? 'Checking…' : submitting ? 'Saving…' : submitLabel}</Text>
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
  segmentedRow: {
    flexDirection: 'row',
    backgroundColor: colors.background,
    borderRadius: radii.sm,
    padding: 3,
    marginBottom: spacing.sm,
  },
  segment: {
    flex: 1,
    paddingVertical: spacing.sm,
    alignItems: 'center',
    borderRadius: radii.sm - 2,
  },
  segmentSelected: {
    backgroundColor: colors.primary,
  },
  segmentText: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textMuted,
  },
  segmentTextSelected: {
    color: '#FFFFFF',
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
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  switchLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: colors.text,
  },
  endDateButton: {
    marginTop: spacing.sm,
    backgroundColor: colors.primaryMuted,
    borderRadius: radii.sm,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    alignSelf: 'flex-start',
  },
  endDateText: {
    color: colors.primary,
    fontWeight: '700',
    fontSize: 15,
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

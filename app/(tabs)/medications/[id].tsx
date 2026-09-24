import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ALL_DAYS, MedicationForm, type MedicationFormValues, type RecurrenceSelection } from '@/src/components/MedicationForm';
import { deleteMedication, findDuplicateMedication, getMedication, updateMedication } from '@/src/db/medications';
import { listSchedulesForMedication } from '@/src/db/schedules';
import { syncMedicationSchedules } from '@/src/db/syncSchedules';
import { cancelDoseReminders } from '@/src/notifications/scheduler';
import { colors } from '@/src/theme';
import { parseDateStr } from '@/src/utils/date';
import type { MedicationInput } from '@/src/types';

export default function EditMedicationScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const medicationId = Number(id);
  const router = useRouter();
  const [initial, setInitial] = useState<MedicationFormValues | null>(null);
  const [monthlyAnchorDay, setMonthlyAnchorDay] = useState(new Date().getDate());

  useEffect(() => {
    (async () => {
      const medication = await getMedication(medicationId);
      if (!medication) {
        router.back();
        return;
      }
      const schedules = await listSchedulesForMedication(medicationId);
      const first = schedules[0];
      const recurrenceType = first?.recurrenceType ?? 'daily';

      if (recurrenceType === 'monthly' && first?.startDate) {
        setMonthlyAnchorDay(parseDateStr(first.startDate).getDate());
      }

      setInitial({
        name: medication.name,
        dosage: medication.dosage ?? '',
        form: medication.form ?? '',
        notes: medication.notes ?? '',
        pillsRemaining: medication.pillsRemaining?.toString() ?? '',
        refillThreshold: medication.refillThreshold?.toString() ?? '',
        times: schedules.length ? schedules.map((s) => s.timeOfDay) : ['08:00'],
        // All schedules for a medication share one repeat pattern, set from the first.
        recurrenceType,
        daysOfWeek: recurrenceType === 'weekly' && first?.daysOfWeek ? first.daysOfWeek : ALL_DAYS,
        hasEndDate: Boolean(first?.endDate),
        endDate: first?.endDate ?? '',
      });
    })();
  }, [medicationId]);

  const handleSubmit = async (input: MedicationInput, times: string[], recurrence: RecurrenceSelection) => {
    await updateMedication(medicationId, input);

    await syncMedicationSchedules(medicationId, input, times, recurrence);
    router.back();
  };

  const handleDelete = async () => {
    const existing = await listSchedulesForMedication(medicationId);
    await cancelDoseReminders(existing);
    await deleteMedication(medicationId);
    router.back();
  };

  if (!initial) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <MedicationForm
      initial={initial}
      submitLabel="Save changes"
      onSubmit={handleSubmit}
      onDelete={handleDelete}
      checkDuplicate={(input) => findDuplicateMedication(input, medicationId)}
      monthlyAnchorDay={monthlyAnchorDay}
    />
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
});

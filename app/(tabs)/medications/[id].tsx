import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { MedicationForm, type MedicationFormValues } from '@/src/components/MedicationForm';
import { deleteMedication, getMedication, updateMedication } from '@/src/db/medications';
import { createSchedule, deleteSchedulesForMedication, listSchedulesForMedication } from '@/src/db/schedules';
import { cancelDoseReminders, scheduleDoseReminders } from '@/src/notifications/scheduler';
import { colors } from '@/src/theme';
import type { MedicationInput } from '@/src/types';

export default function EditMedicationScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const medicationId = Number(id);
  const router = useRouter();
  const [initial, setInitial] = useState<MedicationFormValues | null>(null);

  useEffect(() => {
    (async () => {
      const medication = await getMedication(medicationId);
      if (!medication) {
        router.back();
        return;
      }
      const schedules = await listSchedulesForMedication(medicationId);
      setInitial({
        name: medication.name,
        dosage: medication.dosage ?? '',
        form: medication.form ?? '',
        notes: medication.notes ?? '',
        pillsRemaining: medication.pillsRemaining?.toString() ?? '',
        refillThreshold: medication.refillThreshold?.toString() ?? '',
        times: schedules.length ? schedules.map((s) => s.timeOfDay) : ['08:00'],
      });
    })();
  }, [medicationId]);

  const handleSubmit = async (input: MedicationInput, times: string[]) => {
    await updateMedication(medicationId, input);

    // Simplest correct approach for a POC: replace all schedules/notifications on every save
    // rather than diffing which times changed.
    const existing = await deleteSchedulesForMedication(medicationId);
    await cancelDoseReminders(existing.flatMap((s) => s.notificationIds));

    for (const time of times) {
      const scheduleId = await createSchedule(medicationId, time);
      await scheduleDoseReminders({
        scheduleId,
        medicationId,
        medicationName: input.name,
        dosage: input.dosage,
        timeOfDay: time,
      });
    }
    router.back();
  };

  const handleDelete = async () => {
    const existing = await listSchedulesForMedication(medicationId);
    await cancelDoseReminders(existing.flatMap((s) => s.notificationIds));
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
    <MedicationForm initial={initial} submitLabel="Save changes" onSubmit={handleSubmit} onDelete={handleDelete} />
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
});

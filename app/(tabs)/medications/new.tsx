import { useRouter } from 'expo-router';
import { MedicationForm, DEFAULT_FORM_VALUES } from '@/src/components/MedicationForm';
import { createMedication } from '@/src/db/medications';
import { createSchedule } from '@/src/db/schedules';
import { scheduleDoseReminders } from '@/src/notifications/scheduler';
import type { MedicationInput } from '@/src/types';

export default function NewMedicationScreen() {
  const router = useRouter();

  const handleSubmit = async (input: MedicationInput, times: string[], daysOfWeek: number[]) => {
    const medicationId = await createMedication(input);
    const storedDays = daysOfWeek.length === 7 ? null : daysOfWeek;
    for (const time of times) {
      const scheduleId = await createSchedule(medicationId, time, storedDays);
      await scheduleDoseReminders({
        scheduleId,
        medicationId,
        medicationName: input.name,
        dosage: input.dosage,
        timeOfDay: time,
        daysOfWeek: storedDays,
      });
    }
    router.back();
  };

  return <MedicationForm initial={DEFAULT_FORM_VALUES} submitLabel="Add medication" onSubmit={handleSubmit} />;
}

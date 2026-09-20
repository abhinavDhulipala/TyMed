import { useRouter } from 'expo-router';
import { MedicationForm, DEFAULT_FORM_VALUES, type RecurrenceSelection } from '@/src/components/MedicationForm';
import { createMedication, findDuplicateMedication } from '@/src/db/medications';
import { createSchedule } from '@/src/db/schedules';
import { scheduleDoseReminders } from '@/src/notifications/scheduler';
import { todayDateString } from '@/src/utils/date';
import type { MedicationInput } from '@/src/types';

export default function NewMedicationScreen() {
  const router = useRouter();

  const handleSubmit = async (input: MedicationInput, times: string[], recurrence: RecurrenceSelection) => {
    const medicationId = await createMedication(input);
    const startDate = recurrence.recurrenceType === 'monthly' ? todayDateString() : null;

    for (const time of times) {
      const scheduleId = await createSchedule(medicationId, time, {
        recurrenceType: recurrence.recurrenceType,
        daysOfWeek: recurrence.daysOfWeek,
        startDate,
        endDate: recurrence.endDate,
      });
      await scheduleDoseReminders({
        scheduleId,
        medicationId,
        medicationName: input.name,
        dosage: input.dosage,
        timeOfDay: time,
        recurrenceType: recurrence.recurrenceType,
        daysOfWeek: recurrence.daysOfWeek,
        startDate,
        endDate: recurrence.endDate,
      });
    }
    router.back();
  };

  return (
    <MedicationForm
      initial={DEFAULT_FORM_VALUES}
      submitLabel="Add medication"
      onSubmit={handleSubmit}
      checkDuplicate={(input) => findDuplicateMedication(input)}
      monthlyAnchorDay={new Date().getDate()}
    />
  );
}

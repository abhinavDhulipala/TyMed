import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ALL_DAYS, MedicationForm, type MedicationFormValues, type RecurrenceSelection } from '@/src/components/MedicationForm';
import { deleteMedication, findDuplicateMedication, getMedication, updateMedication } from '@/src/db/medications';
import {
  createSchedule,
  deleteSchedule,
  listSchedulesForMedication,
  updateScheduleRecurrence,
  type RecurrenceInput,
} from '@/src/db/schedules';
import { cancelDoseReminders, scheduleDoseReminders } from '@/src/notifications/scheduler';
import { colors } from '@/src/theme';
import { recurrenceRuleEquals } from '@/src/utils/schedule';
import { parseDateStr, todayDateString } from '@/src/utils/date';
import type { MedicationInput, RecurrenceType, Schedule } from '@/src/types';

/** The monthly day-of-month anchor: preserve an existing monthly schedule's own anchor (so
 * editing something unrelated doesn't shift when it fires), otherwise anchor to today — either
 * a brand new schedule, or one just switched to monthly from another recurrence type. */
function resolveStartDate(existing: Schedule | undefined, recurrenceType: RecurrenceType): string | null {
  if (recurrenceType !== 'monthly') return null;
  if (existing && existing.recurrenceType === 'monthly' && existing.startDate) return existing.startDate;
  return todayDateString();
}

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

    const uniqueTimes = Array.from(new Set(times));
    const nextTimeSet = new Set(uniqueTimes);

    // Diff against what's already there instead of replacing everything on every save: a
    // schedule for a time slot that still exists keeps its id, which keeps its intake_logs
    // history (including any already-taken doses) and lets the native alarm be updated in
    // place (idempotent re-arm) rather than cancelled and re-created. Only truly removed time
    // slots get deleted/cancelled — this is what actually fixes duplicated/orphaned reminders
    // after an edit.
    const existingSchedules = await listSchedulesForMedication(medicationId);
    const existingByTime = new Map(existingSchedules.map((s) => [s.timeOfDay, s]));

    const removed = existingSchedules.filter((s) => !nextTimeSet.has(s.timeOfDay));
    if (removed.length > 0) {
      await cancelDoseReminders(removed);
      for (const schedule of removed) {
        await deleteSchedule(schedule.id);
      }
    }

    for (const time of uniqueTimes) {
      const existing: Schedule | undefined = existingByTime.get(time);
      const startDate = resolveStartDate(existing, recurrence.recurrenceType);
      const nextRule: RecurrenceInput = {
        recurrenceType: recurrence.recurrenceType,
        daysOfWeek: recurrence.daysOfWeek,
        startDate,
        endDate: recurrence.endDate,
      };

      let scheduleId: number;
      if (existing) {
        scheduleId = existing.id;
        if (!recurrenceRuleEquals(existing, nextRule)) {
          await updateScheduleRecurrence(existing.id, nextRule);
        }
      } else {
        scheduleId = await createSchedule(medicationId, time, nextRule);
      }

      // Re-arming is idempotent (same schedule id => same native request code => the platform
      // updates the existing alarm/notification in place), so it's safe to do unconditionally —
      // covers the medication name/dosage having changed even when the time didn't.
      await scheduleDoseReminders({
        scheduleId,
        medicationId,
        medicationName: input.name,
        dosage: input.dosage,
        timeOfDay: time,
        recurrenceType: nextRule.recurrenceType,
        daysOfWeek: nextRule.daysOfWeek,
        startDate: nextRule.startDate,
        endDate: nextRule.endDate,
      });
    }

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

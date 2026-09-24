import type { SQLiteDatabase } from 'expo-sqlite';
import { createTestDb } from '@/src/db/testSupport/inMemorySqlite';
import { todayDateString } from '@/src/utils/date';

// Runs the real migrations + real SQL in src/db/*.ts against an in-memory SQLite engine — see
// src/db/__tests__/logs.test.ts for the precedent this follows. tools.ts is where the riskiest
// logic in the whole AI feature lives (duplicate detection, dose matching, confirmation
// gating), so it gets real coverage here rather than only manual on-device verification.
let mockDb: SQLiteDatabase;

jest.mock('@/src/db/client', () => ({
  getDb: async () => mockDb,
}));

// scheduleDoseReminders touches native alarms/notifications — irrelevant to tools.ts's own
// logic, so it's stubbed out and asserted on by call count instead. skipTodaysDoseReminder is
// pulled in transitively through db/actions.ts's markDose (Android alarm suppression) and needs
// stubbing for the same reason, even though tools.ts itself never calls it directly.
jest.mock('@/src/notifications/scheduler', () => ({
  scheduleDoseReminders: jest.fn().mockResolvedValue([]),
  cancelDoseReminders: jest.fn().mockResolvedValue(undefined),
  skipTodaysDoseReminder: jest.fn().mockResolvedValue(undefined),
}));

import {
  addMedication,
  endDateForDuration,
  getTodaysDoses,
  markDoseTaken,
  runTool,
  updateMedicationSchedule,
} from '../tools';
import { cancelDoseReminders, scheduleDoseReminders } from '@/src/notifications/scheduler';
import { createMedication, getMedication } from '@/src/db/medications';
import { createSchedule, listSchedulesForMedication, type RecurrenceInput } from '@/src/db/schedules';
import { getDosesForDate } from '@/src/db/logs';
import type { MedicationInput } from '@/src/types';

const DAILY: RecurrenceInput = { recurrenceType: 'daily', daysOfWeek: null, startDate: null, endDate: null };

function medicationInput(overrides: Partial<MedicationInput> & { name: string }): MedicationInput {
  return { dosage: null, form: null, notes: null, pillsRemaining: null, refillThreshold: null, ...overrides };
}

beforeEach(async () => {
  mockDb = await createTestDb();
  jest.clearAllMocks();
});

describe('addMedication', () => {
  it('asks for confirmation with a summary before writing anything', async () => {
    const result = await addMedication({ name: 'Ibuprofen', dosage: '200mg', times: ['08:00', '20:00'] });
    expect(result).toEqual({
      status: 'needs_confirmation',
      medicationName: 'Ibuprofen',
      dosage: '200mg',
      times: ['08:00', '20:00'],
      endDate: null,
      similar: [],
    });

    const doses = await getDosesForDate(todayDateString());
    expect(doses).toHaveLength(0);
    expect(scheduleDoseReminders).not.toHaveBeenCalled();
  });

  it('creates the medication and one schedule per requested time once confirmed', async () => {
    const result = await addMedication({ name: 'Ibuprofen', dosage: '200mg', times: ['08:00', '20:00'], confirmed: true });
    expect(result).toEqual({ status: 'added', medicationName: 'Ibuprofen', times: ['08:00', '20:00'], endDate: null });

    const doses = await getDosesForDate(todayDateString());
    expect(doses.map((d) => d.scheduledTime).sort()).toEqual(['08:00', '20:00']);
    expect(scheduleDoseReminders).toHaveBeenCalledTimes(2);
  });

  it('defaults to a single 08:00 daily schedule when no times are given', async () => {
    const result = await addMedication({ name: 'Vitamin D', confirmed: true });
    expect(result).toEqual({ status: 'added', medicationName: 'Vitamin D', times: ['08:00'], endDate: null });
  });

  it('turns durationDays into an inclusive end date on every schedule', async () => {
    const result = await addMedication({ name: 'Amoxicillin', times: ['08:00', '20:00'], durationDays: 7, confirmed: true });
    const endDate = endDateForDuration(7);
    expect(result).toEqual({ status: 'added', medicationName: 'Amoxicillin', times: ['08:00', '20:00'], endDate });
    expect(scheduleDoseReminders).toHaveBeenCalledWith(expect.objectContaining({ endDate }));
  });

  it('rejects a malformed time before creating anything', async () => {
    await expect(addMedication({ name: 'Ibuprofen', times: ['8am'] })).rejects.toThrow();
    const doses = await getDosesForDate(todayDateString());
    expect(doses).toHaveLength(0);
  });

  it('reports an exact duplicate instead of creating a second medication', async () => {
    await addMedication({ name: 'Metformin', dosage: '500mg', confirmed: true });
    const result = await addMedication({ name: 'Metformin', dosage: '500mg', confirmed: true });
    expect(result).toEqual({ status: 'exists', medicationName: 'Metformin' });

    const doses = await getDosesForDate(todayDateString());
    expect(doses).toHaveLength(1);
  });

  it('treats an add of an already-tracked medication with new times as a confirmed schedule change', async () => {
    await addMedication({ name: 'Ibuprofen', dosage: '200 mg', confirmed: true });

    const args = { name: 'ibuprofen', dosage: '200 mg', times: ['08:00', '20:00'], durationDays: 7 };
    const pending = await addMedication(args);
    expect(pending).toEqual({
      status: 'needs_confirmation',
      existing: true,
      medicationName: 'Ibuprofen',
      before: { times: ['08:00'], endDate: null },
      after: { times: ['08:00', '20:00'], endDate: endDateForDuration(7) },
    });
    expect((await getDosesForDate(todayDateString())).map((d) => d.scheduledTime)).toEqual(['08:00']);

    const done = await addMedication({ ...args, confirmed: true });
    expect(done.status).toBe('updated');
    expect((await getDosesForDate(todayDateString())).map((d) => d.scheduledTime).sort()).toEqual(['08:00', '20:00']);
  });

  it('lists a same-name different-dosage match in the confirmation, then adds once confirmed', async () => {
    await addMedication({ name: 'Metformin', dosage: '500mg', confirmed: true });

    const needsConfirmation = await addMedication({ name: 'Metformin', dosage: '1000mg' });
    expect(needsConfirmation.status).toBe('needs_confirmation');
    if ('similar' in needsConfirmation) {
      expect(needsConfirmation.similar).toEqual([{ name: 'Metformin', dosage: '500mg', form: null }]);
    }

    const confirmed = await addMedication({ name: 'Metformin', dosage: '1000mg', confirmed: true });
    expect(confirmed).toEqual({ status: 'added', medicationName: 'Metformin', times: ['08:00'], endDate: null });
  });

  it('rejects a blank name', async () => {
    await expect(addMedication({ name: '   ' })).rejects.toThrow();
  });
});

describe('endDateForDuration', () => {
  it('counts today as day one and crosses month boundaries', () => {
    expect(endDateForDuration(1, new Date(2026, 8, 24))).toBe('2026-09-24');
    expect(endDateForDuration(7, new Date(2026, 8, 24))).toBe('2026-09-30');
    expect(endDateForDuration(8, new Date(2026, 8, 24))).toBe('2026-10-01');
  });
});

describe('updateMedicationSchedule', () => {
  async function seedIbuprofen() {
    const medicationId = await createMedication(medicationInput({ name: 'Ibuprofen', dosage: '200 mg' }));
    await createSchedule(medicationId, '08:00', DAILY);
    return medicationId;
  }

  it('asks for confirmation with a before/after summary without writing', async () => {
    const medicationId = await seedIbuprofen();

    const result = await updateMedicationSchedule({ medicationName: 'ibuprofen', times: ['20:00', '08:00'], durationDays: 7 });
    expect(result).toEqual({
      status: 'needs_confirmation',
      medicationName: 'Ibuprofen',
      before: { times: ['08:00'], endDate: null },
      after: { times: ['08:00', '20:00'], endDate: endDateForDuration(7) },
    });

    const schedules = await listSchedulesForMedication(medicationId);
    expect(schedules.map((s) => s.timeOfDay)).toEqual(['08:00']);
  });

  it('adds the new time slot and sets the end date once confirmed, keeping the existing slot', async () => {
    const medicationId = await seedIbuprofen();
    const [original] = await listSchedulesForMedication(medicationId);

    const result = await updateMedicationSchedule({
      medicationName: 'Ibuprofen',
      times: ['08:00', '20:00'],
      durationDays: 7,
      confirmed: true,
    });
    expect(result.status).toBe('updated');

    const schedules = await listSchedulesForMedication(medicationId);
    expect(schedules.map((s) => s.timeOfDay).sort()).toEqual(['08:00', '20:00']);
    expect(schedules.every((s) => s.endDate === endDateForDuration(7))).toBe(true);
    // The kept 08:00 slot is updated in place, so its dose history survives.
    expect(schedules.find((s) => s.timeOfDay === '08:00')?.id).toBe(original.id);
    expect(scheduleDoseReminders).toHaveBeenCalledTimes(2);
  });

  it('removes dropped time slots and cancels their reminders', async () => {
    const medicationId = await seedIbuprofen();
    await createSchedule(medicationId, '20:00', DAILY);

    await updateMedicationSchedule({ medicationName: 'Ibuprofen', times: ['08:00'], confirmed: true });

    const schedules = await listSchedulesForMedication(medicationId);
    expect(schedules.map((s) => s.timeOfDay)).toEqual(['08:00']);
    expect(cancelDoseReminders).toHaveBeenCalledWith([expect.objectContaining({ timeOfDay: '20:00' })]);
  });

  it('keeps the current times when only a duration is given', async () => {
    await seedIbuprofen();
    const result = await updateMedicationSchedule({ medicationName: 'Ibuprofen', durationDays: 3 });
    expect(result).toEqual(
      expect.objectContaining({ status: 'needs_confirmation', after: { times: ['08:00'], endDate: endDateForDuration(3) } })
    );
  });

  it('reports no_change when nothing would differ', async () => {
    await seedIbuprofen();
    const result = await updateMedicationSchedule({ medicationName: 'Ibuprofen', times: ['08:00'], confirmed: true });
    expect(result).toEqual({ status: 'no_change', medicationName: 'Ibuprofen' });
  });

  it('reports not_found with the tracked medication names', async () => {
    await seedIbuprofen();
    const result = await updateMedicationSchedule({ medicationName: 'Aspirin', times: ['09:00'] });
    expect(result).toEqual({ status: 'not_found', medicationName: 'Aspirin', medications: ['Ibuprofen'] });
  });

  it('reports ambiguous between strengths, then resolves once a dosage is given', async () => {
    await seedIbuprofen();
    const otherId = await createMedication(medicationInput({ name: 'Ibuprofen', dosage: '400 mg' }));
    await createSchedule(otherId, '12:00', DAILY);

    const ambiguous = await updateMedicationSchedule({ medicationName: 'Ibuprofen', times: ['09:00'] });
    expect(ambiguous.status).toBe('ambiguous');

    const resolved = await updateMedicationSchedule({ medicationName: 'Ibuprofen', dosage: '400 MG', times: ['09:00'] });
    expect(resolved).toEqual(expect.objectContaining({ status: 'needs_confirmation', before: { times: ['12:00'], endDate: null } }));
  });

  it('rejects a call with nothing to change or a malformed time', async () => {
    await seedIbuprofen();
    await expect(updateMedicationSchedule({ medicationName: 'Ibuprofen' })).rejects.toThrow();
    await expect(updateMedicationSchedule({ medicationName: 'Ibuprofen', times: ['8pm'] })).rejects.toThrow();
  });
});

describe('getTodaysDoses', () => {
  it('returns a compact projection of every dose scheduled today', async () => {
    const medicationId = await createMedication(medicationInput({ name: 'Lisinopril', dosage: '10mg' }));
    await createSchedule(medicationId, '09:00', DAILY);

    const doses = await getTodaysDoses();
    expect(doses).toEqual([{ medicationName: 'Lisinopril', dosage: '10mg', scheduledTime: '09:00', status: 'pending' }]);
  });
});

describe('markDoseTaken', () => {
  it('reports not_found with today\'s medication names when nothing matches', async () => {
    const aId = await createMedication(medicationInput({ name: 'Aspirin' }));
    const bId = await createMedication(medicationInput({ name: 'Biotin' }));
    await createSchedule(aId, '08:00', DAILY);
    await createSchedule(bId, '08:00', DAILY);

    const result = await markDoseTaken({ medicationName: 'Ibuprofen' });
    expect(result.status).toBe('not_found');
    if (result.status === 'not_found') {
      expect(result.todaysMedications.sort()).toEqual(['Aspirin', 'Biotin']);
    }
  });

  it('asks for confirmation before marking an unambiguous match taken', async () => {
    const medicationId = await createMedication(medicationInput({ name: 'Aspirin', pillsRemaining: 10 }));
    await createSchedule(medicationId, '08:00', DAILY);

    const needsConfirmation = await markDoseTaken({ medicationName: 'aspirin' });
    expect(needsConfirmation).toEqual({ status: 'needs_confirmation', medicationName: 'Aspirin', scheduledTime: '08:00' });

    const doses = await getDosesForDate(todayDateString());
    expect(doses[0].status).toBe('pending');
  });

  it('marks the dose taken and decrements pill count once confirmed', async () => {
    const medicationId = await createMedication(medicationInput({ name: 'Aspirin', pillsRemaining: 10 }));
    await createSchedule(medicationId, '08:00', DAILY);

    const result = await markDoseTaken({ medicationName: 'Aspirin', confirmed: true });
    expect(result).toEqual({ status: 'marked_taken', medicationName: 'Aspirin', scheduledTime: '08:00' });

    const doses = await getDosesForDate(todayDateString());
    expect(doses[0].status).toBe('taken');
    const medication = await getMedication(medicationId);
    expect(medication?.pillsRemaining).toBe(9);
  });

  it('reports ambiguous with candidate times, then resolves once a time is given', async () => {
    const medicationId = await createMedication(medicationInput({ name: 'Aspirin' }));
    await createSchedule(medicationId, '08:00', DAILY);
    await createSchedule(medicationId, '20:00', DAILY);

    const ambiguous = await markDoseTaken({ medicationName: 'Aspirin' });
    expect(ambiguous.status).toBe('ambiguous');
    if (ambiguous.status === 'ambiguous') {
      expect(ambiguous.candidates.map((c) => c.scheduledTime).sort()).toEqual(['08:00', '20:00']);
    }

    const resolved = await markDoseTaken({ medicationName: 'Aspirin', scheduledTime: '20:00', confirmed: true });
    expect(resolved).toEqual({ status: 'marked_taken', medicationName: 'Aspirin', scheduledTime: '20:00' });
  });

  it('rejects a blank medicationName', async () => {
    await expect(markDoseTaken({ medicationName: '' })).rejects.toThrow();
  });
});

describe('runTool', () => {
  it('resolves an unknown tool name to an error result instead of throwing', async () => {
    await expect(runTool('delete_everything', {})).resolves.toEqual({ error: 'Unknown tool "delete_everything"' });
  });

  it('catches a validation error from the underlying tool and returns it as an error result', async () => {
    const result = await runTool('mark_dose_taken', { medicationName: '' });
    expect(result).toEqual({ error: expect.stringContaining('medicationName') });
  });
});

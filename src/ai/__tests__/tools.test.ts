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
// logic, so it's stubbed out and asserted on by call count instead.
jest.mock('@/src/notifications/scheduler', () => ({
  scheduleDoseReminders: jest.fn().mockResolvedValue([]),
}));

import { addMedication, getTodaysDoses, markDoseTaken, runTool } from '../tools';
import { scheduleDoseReminders } from '@/src/notifications/scheduler';
import { createMedication, getMedication } from '@/src/db/medications';
import { createSchedule, type RecurrenceInput } from '@/src/db/schedules';
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
  it('creates the medication and one schedule per requested time', async () => {
    const result = await addMedication({ name: 'Ibuprofen', dosage: '200mg', times: ['08:00', '20:00'] });
    expect(result).toEqual({ status: 'added', medicationName: 'Ibuprofen', times: ['08:00', '20:00'] });

    const doses = await getDosesForDate(todayDateString());
    expect(doses.map((d) => d.scheduledTime).sort()).toEqual(['08:00', '20:00']);
    expect(scheduleDoseReminders).toHaveBeenCalledTimes(2);
  });

  it('defaults to a single 08:00 daily schedule when no times are given', async () => {
    const result = await addMedication({ name: 'Vitamin D' });
    expect(result).toEqual({ status: 'added', medicationName: 'Vitamin D', times: ['08:00'] });
  });

  it('rejects a malformed time before creating anything', async () => {
    await expect(addMedication({ name: 'Ibuprofen', times: ['8am'] })).rejects.toThrow();
    const doses = await getDosesForDate(todayDateString());
    expect(doses).toHaveLength(0);
  });

  it('reports an exact duplicate instead of creating a second medication', async () => {
    await addMedication({ name: 'Metformin', dosage: '500mg' });
    const result = await addMedication({ name: 'Metformin', dosage: '500mg' });
    expect(result).toEqual({ status: 'exists', medicationName: 'Metformin' });

    const doses = await getDosesForDate(todayDateString());
    expect(doses).toHaveLength(1);
  });

  it('asks for confirmation on a same-name different-dosage match, then adds once confirmed', async () => {
    await addMedication({ name: 'Metformin', dosage: '500mg' });

    const needsConfirmation = await addMedication({ name: 'Metformin', dosage: '1000mg' });
    expect(needsConfirmation.status).toBe('needs_confirmation');

    const confirmed = await addMedication({ name: 'Metformin', dosage: '1000mg', confirmed: true });
    expect(confirmed).toEqual({ status: 'added', medicationName: 'Metformin', times: ['08:00'] });
  });

  it('rejects a blank name', async () => {
    await expect(addMedication({ name: '   ' })).rejects.toThrow();
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

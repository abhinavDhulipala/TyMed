import type { SQLiteDatabase } from 'expo-sqlite';
import { createTestDb } from '@/src/db/testSupport/inMemorySqlite';
import { generateOnDevice, openAppOnDevice } from './deviceModel';

// End-to-end evals of the chat assistant against the REAL on-device Gemini Nano: the actual
// orchestrator, prompt, parser and tools run here on the host against an in-memory SQLite db,
// and every model call goes to the phone over adb (see deviceModel.ts). Assertions are on what
// ends up in the database, not on the model's wording, which varies run to run.
//
// Not part of `npm test` — needs a phone with Gemini Nano and a build with the eval bridge:
//   (cd android && ./gradlew assembleRelease -PtymedAiEval=true) && adb install -r …
//   npm run eval:ai
let mockDb: SQLiteDatabase;

jest.mock('@/src/db/client', () => ({
  getDb: async () => mockDb,
}));

jest.mock('@/src/notifications/scheduler', () => ({
  scheduleDoseReminders: jest.fn().mockResolvedValue([]),
  cancelDoseReminders: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@/src/native/aiModule', () => ({
  generateNative: (prompt: string) => require('./deviceModel').generateOnDevice(prompt),
}));

import { runTurn, type PendingAction } from '../orchestrator';
import type { HistoryEntry } from '../prompt';
import { endDateForDuration } from '../tools';
import { createMedication, listMedications } from '@/src/db/medications';
import { createSchedule, listSchedulesForMedication, type RecurrenceInput } from '@/src/db/schedules';
import { getDosesForDate } from '@/src/db/logs';
import { todayDateString } from '@/src/utils/date';

jest.setTimeout(600_000);

const DAILY: RecurrenceInput = { recurrenceType: 'daily', daysOfWeek: null, startDate: null, endDate: null };

/** Runs a scripted conversation, printing the transcript so a failure shows what the model said. */
async function chat(messages: string[]) {
  let history: HistoryEntry[] = [];
  let pending: PendingAction | null = null;
  const turns: { user: string; reply: string; pending: PendingAction | null }[] = [];
  for (const message of messages) {
    const result = await runTurn(message, history, pending);
    history = result.history;
    pending = result.pending;
    turns.push({ user: message, reply: result.reply, pending });
  }
  console.log(
    `${expect.getState().currentTestName}\n` +
      history.map((h) => `  ${h.role.padEnd(9)} ${h.content}`).join('\n')
  );
  return turns;
}

async function seedMedication(name: string, dosage: string, times: string[]) {
  const id = await createMedication({ name, dosage, form: null, notes: null, pillsRemaining: null, refillThreshold: null });
  for (const time of times) await createSchedule(id, time, DAILY);
  return id;
}

async function scheduleOf(medicationId: number) {
  const schedules = await listSchedulesForMedication(medicationId);
  return {
    times: schedules.map((s) => s.timeOfDay).sort(),
    endDates: [...new Set(schedules.map((s) => s.endDate))],
  };
}

beforeAll(() => {
  openAppOnDevice();
});

beforeEach(async () => {
  mockDb = await createTestDb();
});

it('adds a new medication with two times and a duration, only after the user says yes', async () => {
  const [ask, confirm] = await chat(['Add amoxicillin 500 mg at 8am and 8pm for 7 days', 'Yes']);

  expect(ask.pending).not.toBeNull();
  expect(confirm.pending).toBeNull();
  const [medication] = await listMedications();
  expect(medication?.name.toLowerCase()).toBe('amoxicillin');
  expect(await scheduleOf(medication.id)).toEqual({ times: ['08:00', '20:00'], endDates: [endDateForDuration(7)] });
});

it('writes nothing before the user confirms', async () => {
  await chat(['Add amoxicillin 500 mg at 8am and 8pm for 7 days']);
  expect(await listMedications()).toHaveLength(0);
});

it('changes an existing medication when asked to "add" it with a new schedule (the reported bug)', async () => {
  const id = await seedMedication('ibuprofen', '200 mg', ['08:00']);
  await chat(['Add ibuprofen 200 mg at 8am and 8pm for 7 days', 'Yes']);

  expect(await listMedications()).toHaveLength(1);
  expect(await scheduleOf(id)).toEqual({ times: ['08:00', '20:00'], endDates: [endDateForDuration(7)] });
});

it('changes an existing medication\'s schedule when asked directly', async () => {
  const id = await seedMedication('ibuprofen', '200 mg', ['08:00']);
  await chat(['Make ibuprofen twice a day at 8am and 8pm for 7 days', 'Yes']);

  expect(await listMedications()).toHaveLength(1);
  expect(await scheduleOf(id)).toEqual({ times: ['08:00', '20:00'], endDates: [endDateForDuration(7)] });
});

it('leaves the schedule alone when the user declines', async () => {
  const id = await seedMedication('ibuprofen', '200 mg', ['08:00']);
  const [, decline] = await chat(['Make ibuprofen twice a day at 8am and 8pm for 7 days', 'No']);

  expect(decline.pending).toBeNull();
  expect(await scheduleOf(id)).toEqual({ times: ['08:00'], endDates: [null] });
});

it('reports what is left today', async () => {
  await seedMedication('aspirin', '81 mg', ['08:00']);
  await seedMedication('metformin', '500 mg', ['20:00']);
  const [answer] = await chat(['What do I have left today?']);

  expect(answer.reply.toLowerCase()).toContain('aspirin');
  expect(answer.reply.toLowerCase()).toContain('metformin');
});

it('marks a dose taken only after the user says yes', async () => {
  await seedMedication('aspirin', '81 mg', ['08:00']);
  const [ask] = await chat(['I just took my aspirin', 'Yes']);

  expect(ask.pending).not.toBeNull();
  const [dose] = await getDosesForDate(todayDateString());
  expect(dose.status).toBe('taken');
});

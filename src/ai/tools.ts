import { createMedication, findDuplicateMedication, listMedications } from '@/src/db/medications';
import { createSchedule, listSchedulesForMedication } from '@/src/db/schedules';
import { syncMedicationSchedules } from '@/src/db/syncSchedules';
import { getDosesForDate } from '@/src/db/logs';
import { markDose } from '@/src/db/actions';
import { scheduleDoseReminders } from '@/src/notifications/scheduler';
import { todayDateString } from '@/src/utils/date';
import type { DoseStatus, MedicationInput } from '@/src/types';

const DEFAULT_TIMES = ['08:00'];
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function toTimes(value: unknown): string[] | undefined {
  return Array.isArray(value) ? value.filter((t): t is string => typeof t === 'string') : undefined;
}

function toDurationDays(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : undefined;
}

function assertValidTimes(tool: string, times: string[]): void {
  for (const time of times) {
    if (!TIME_PATTERN.test(time)) {
      throw new Error(`${tool}: invalid time "${time}", expected 24-hour "HH:MM"`);
    }
  }
}

/** Last day (inclusive) of a course that starts today and runs for `days` days — computed here
 * rather than asking the model for an end date, since small on-device models are unreliable at
 * calendar arithmetic. */
export function endDateForDuration(days: number, today: Date = new Date()): string {
  return todayDateString(new Date(today.getFullYear(), today.getMonth(), today.getDate() + days - 1));
}

// ---- add_medication ----------------------------------------------------

// Deliberately just name/dosage/schedule: evals showed Gemini Nano filling every optional field
// it's offered with invented values (a pill count of 30, "as directed"). Form, notes and pill
// tracking are set from the edit screen.
export interface AddMedicationArgs {
  name: string;
  dosage?: string | null;
  times?: string[];
  /** How many days the course runs, starting today; omitted means no end date. */
  durationDays?: number;
  confirmed?: boolean;
}

export type AddMedicationResult =
  | { status: 'exists'; medicationName: string }
  // An "add" for something already tracked that also names new times/duration is really a schedule
  // change — handled by the update path so the user gets one before/after confirmation.
  | (UpdateScheduleResult & { existing: true })
  | {
      status: 'needs_confirmation';
      medicationName: string;
      dosage: string | null;
      times: string[];
      endDate: string | null;
      similar: { name: string; dosage: string | null; form: string | null }[];
    }
  | { status: 'added'; medicationName: string; times: string[]; endDate: string | null };

export function toAddMedicationArgs(raw: unknown): AddMedicationArgs {
  const obj = isRecord(raw) ? raw : {};
  return {
    name: typeof obj.name === 'string' ? obj.name : '',
    dosage: typeof obj.dosage === 'string' ? obj.dosage : null,
    times: toTimes(obj.times),
    durationDays: toDurationDays(obj.durationDays),
    confirmed: obj.confirmed === true,
  };
}

/** Mirrors app/(tabs)/medications/new.tsx's submit flow: one createMedication, then one
 * createSchedule + scheduleDoseReminders per requested time, all on a fixed daily recurrence —
 * voice/chat add doesn't offer weekly/monthly recurrence in v1. Nothing is written until the
 * user has confirmed the exact summary returned as needs_confirmation. */
export async function addMedication(args: AddMedicationArgs): Promise<AddMedicationResult> {
  const name = args.name.trim();
  if (!name) {
    throw new Error('add_medication requires a non-empty name');
  }

  const times = args.times && args.times.length > 0 ? args.times : DEFAULT_TIMES;
  assertValidTimes('add_medication', times);
  const endDate = args.durationDays ? endDateForDuration(args.durationDays) : null;

  const input: MedicationInput = {
    name,
    dosage: args.dosage?.trim() || null,
    form: null,
    notes: null,
    pillsRemaining: null,
    refillThreshold: null,
  };

  const duplicate = await findDuplicateMedication(input);
  if (duplicate.exact) {
    if (!args.times?.length && !args.durationDays) {
      return { status: 'exists', medicationName: duplicate.exact.name };
    }
    const update = await updateMedicationSchedule({
      medicationName: duplicate.exact.name,
      dosage: duplicate.exact.dosage ?? undefined,
      times: args.times,
      durationDays: args.durationDays,
      confirmed: args.confirmed,
    });
    return { ...update, existing: true };
  }
  if (!args.confirmed) {
    return {
      status: 'needs_confirmation',
      medicationName: name,
      dosage: input.dosage,
      times,
      endDate,
      similar: duplicate.partial.map((m) => ({ name: m.name, dosage: m.dosage, form: m.form })),
    };
  }

  const medicationId = await createMedication(input);
  for (const time of times) {
    const scheduleId = await createSchedule(medicationId, time, {
      recurrenceType: 'daily',
      daysOfWeek: null,
      startDate: null,
      endDate,
    });
    await scheduleDoseReminders({
      scheduleId,
      medicationId,
      medicationName: name,
      dosage: input.dosage,
      timeOfDay: time,
      recurrenceType: 'daily',
      daysOfWeek: null,
      startDate: null,
      endDate,
    });
  }

  return { status: 'added', medicationName: name, times, endDate };
}

// ---- update_medication_schedule ----------------------------------------

export interface UpdateScheduleArgs {
  medicationName: string;
  /** Disambiguates between medications sharing a name (e.g. two strengths). */
  dosage?: string;
  /** Replaces the full set of daily times; omitted keeps the current ones. */
  times?: string[];
  /** Course length from today; omitted keeps the current end date. */
  durationDays?: number;
  confirmed?: boolean;
}

export type UpdateScheduleResult =
  | { status: 'not_found'; medicationName: string; medications: string[] }
  | { status: 'ambiguous'; medicationName: string; dosages: (string | null)[] }
  | { status: 'no_change'; medicationName: string }
  | {
      status: 'needs_confirmation' | 'updated';
      medicationName: string;
      before: { times: string[]; endDate: string | null };
      after: { times: string[]; endDate: string | null };
    };

export function toUpdateScheduleArgs(raw: unknown): UpdateScheduleArgs {
  const obj = isRecord(raw) ? raw : {};
  return {
    medicationName: typeof obj.medicationName === 'string' ? obj.medicationName : '',
    dosage: typeof obj.dosage === 'string' ? obj.dosage : undefined,
    times: toTimes(obj.times),
    durationDays: toDurationDays(obj.durationDays),
    confirmed: obj.confirmed === true,
  };
}

/** Changes an existing medication's reminder times and/or end date through the same
 * syncMedicationSchedules path the edit screen uses, so dose history for kept time slots
 * survives. Keeps the medication's current repeat pattern (daily/weekly/monthly) — only the
 * edit screen changes that. Gated on confirmed:true like every other write. */
export async function updateMedicationSchedule(args: UpdateScheduleArgs): Promise<UpdateScheduleResult> {
  const name = args.medicationName.trim();
  if (!name) {
    throw new Error('update_medication_schedule requires a medicationName');
  }
  if (!args.times?.length && !args.durationDays) {
    throw new Error('update_medication_schedule needs times and/or durationDays to change');
  }

  const all = await listMedications();
  const sameName = all.filter((m) => m.name.trim().toLowerCase() === name.toLowerCase());
  if (sameName.length === 0) {
    return { status: 'not_found', medicationName: name, medications: all.map((m) => m.name) };
  }
  const wantedDosage = args.dosage?.trim().toLowerCase();
  const matches =
    sameName.length > 1 && wantedDosage
      ? sameName.filter((m) => (m.dosage ?? '').trim().toLowerCase() === wantedDosage)
      : sameName;
  if (matches.length !== 1) {
    return { status: 'ambiguous', medicationName: name, dosages: sameName.map((m) => m.dosage) };
  }
  const [medication] = matches;

  const schedules = await listSchedulesForMedication(medication.id);
  const first = schedules[0];
  const before = { times: schedules.map((s) => s.timeOfDay).sort(), endDate: first?.endDate ?? null };

  const nextTimes = args.times?.length ? Array.from(new Set(args.times)).sort() : before.times;
  assertValidTimes('update_medication_schedule', nextTimes);
  const after = {
    times: nextTimes,
    endDate: args.durationDays ? endDateForDuration(args.durationDays) : before.endDate,
  };

  if (after.endDate === before.endDate && after.times.join() === before.times.join()) {
    return { status: 'no_change', medicationName: medication.name };
  }
  if (!args.confirmed) {
    return { status: 'needs_confirmation', medicationName: medication.name, before, after };
  }

  await syncMedicationSchedules(medication.id, medication, after.times, {
    recurrenceType: first?.recurrenceType ?? 'daily',
    daysOfWeek: first?.daysOfWeek ?? null,
    endDate: after.endDate,
  });
  return { status: 'updated', medicationName: medication.name, before, after };
}

// ---- get_todays_doses ----------------------------------------------------

export interface TodaysDose {
  medicationName: string;
  dosage: string | null;
  scheduledTime: string;
  status: DoseStatus;
}

export async function getTodaysDoses(): Promise<TodaysDose[]> {
  const doses = await getDosesForDate(todayDateString());
  return doses.map((d) => ({
    medicationName: d.medicationName,
    dosage: d.dosage,
    scheduledTime: d.scheduledTime,
    status: d.status,
  }));
}

// ---- mark_dose_taken ----------------------------------------------------

export interface MarkDoseTakenArgs {
  medicationName: string;
  scheduledTime?: string;
  confirmed?: boolean;
}

export type MarkDoseTakenResult =
  | { status: 'not_found'; medicationName: string; todaysMedications: string[] }
  | { status: 'ambiguous'; medicationName: string; candidates: { scheduledTime: string }[] }
  | { status: 'needs_confirmation'; medicationName: string; scheduledTime: string }
  | { status: 'marked_taken'; medicationName: string; scheduledTime: string };

export function toMarkDoseTakenArgs(raw: unknown): MarkDoseTakenArgs {
  const obj = isRecord(raw) ? raw : {};
  return {
    medicationName: typeof obj.medicationName === 'string' ? obj.medicationName : '',
    scheduledTime: typeof obj.scheduledTime === 'string' ? obj.scheduledTime : undefined,
    confirmed: obj.confirmed === true,
  };
}

/** Never calls setLogStatus directly — markDose is what keeps pills_remaining in sync. Requires
 * an explicit confirmed:true before the actual write (even for an unambiguous single match): a
 * misparsed voice/chat command silently marking a dose taken has real double-dosing risk, and
 * AICore gives no structured-output guarantee to lean on instead. */
export async function markDoseTaken(args: MarkDoseTakenArgs): Promise<MarkDoseTakenResult> {
  const name = args.medicationName.trim();
  if (!name) {
    throw new Error('mark_dose_taken requires a medicationName');
  }

  const doses = await getDosesForDate(todayDateString());
  const pending = doses.filter((d) => d.status === 'pending');

  const normalized = name.toLowerCase();
  let matches = pending.filter((d) => d.medicationName.toLowerCase() === normalized);

  if (matches.length === 0) {
    return {
      status: 'not_found',
      medicationName: name,
      todaysMedications: [...new Set(doses.map((d) => d.medicationName))],
    };
  }

  if (matches.length > 1 && args.scheduledTime) {
    const narrowed = matches.filter((d) => d.scheduledTime === args.scheduledTime);
    if (narrowed.length === 1) {
      matches = narrowed;
    }
  }

  if (matches.length > 1) {
    return {
      status: 'ambiguous',
      medicationName: name,
      candidates: matches.map((d) => ({ scheduledTime: d.scheduledTime })),
    };
  }

  const [match] = matches;
  if (!args.confirmed) {
    return { status: 'needs_confirmation', medicationName: match.medicationName, scheduledTime: match.scheduledTime };
  }

  await markDose(match.id, 'taken');
  return { status: 'marked_taken', medicationName: match.medicationName, scheduledTime: match.scheduledTime };
}

// ---- dispatch ----------------------------------------------------

export type ToolName = 'add_medication' | 'update_medication_schedule' | 'get_todays_doses' | 'mark_dose_taken';

export const TOOL_DESCRIPTIONS: Record<ToolName, string> = {
  add_medication:
    'add_medication(name: string, dosage?: string, times?: string[] ["HH:MM" 24h, default ["08:00"]], ' +
    'durationDays?: number) — adds a NEW medication with daily reminders; "twice a day" means two times, "for 7 days" means durationDays:7. If ' +
    'the result status is "exists", say it\'s already tracked. If the result has existing:true, it was already ' +
    'tracked and this is a schedule change: describe the before/after.',
  update_medication_schedule:
    'update_medication_schedule(medicationName: string, dosage?: string, times?: string[] ["HH:MM" 24h, the ' +
    'complete new set], durationDays?: number) — changes an EXISTING medication\'s reminder times and/or how many ' +
    'more days it runs. If the user gives a frequency but no times, ask which times first. If "ambiguous", ask ' +
    'which dosage. If "not_found", list the medications.',
  get_todays_doses: 'get_todays_doses() — returns every dose scheduled for today with its status.',
  mark_dose_taken:
    'mark_dose_taken(medicationName: string, scheduledTime?: string ["HH:MM"]) — marks a pending dose as taken. ' +
    'If the result status is "not_found", tell the user and list today\'s medications. If "ambiguous", ask which ' +
    'time and re-call with scheduledTime set.',
};

/** Runs a tool call and always resolves — a bad tool name or invalid arguments becomes a
 * `{ error }` result fed back to the model instead of throwing, so a single malformed model
 * response can't crash the whole conversation turn. */
export async function runTool(name: string, args: unknown): Promise<unknown> {
  try {
    switch (name as ToolName) {
      case 'add_medication':
        return await addMedication(toAddMedicationArgs(args));
      case 'update_medication_schedule':
        return await updateMedicationSchedule(toUpdateScheduleArgs(args));
      case 'get_todays_doses':
        return await getTodaysDoses();
      case 'mark_dose_taken':
        return await markDoseTaken(toMarkDoseTakenArgs(args));
      default:
        return { error: `Unknown tool "${name}"` };
    }
  } catch (error) {
    return { error: error instanceof Error ? error.message : 'Tool call failed' };
  }
}

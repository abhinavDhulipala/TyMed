import { createMedication, findDuplicateMedication } from '@/src/db/medications';
import { createSchedule } from '@/src/db/schedules';
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

// ---- add_medication ----------------------------------------------------

export interface AddMedicationArgs {
  name: string;
  dosage?: string | null;
  form?: string | null;
  notes?: string | null;
  pillsRemaining?: number | null;
  refillThreshold?: number | null;
  times?: string[];
  confirmed?: boolean;
}

export type AddMedicationResult =
  | { status: 'exists'; medicationName: string }
  | {
      status: 'needs_confirmation';
      medicationName: string;
      similar: { name: string; dosage: string | null; form: string | null }[];
    }
  | { status: 'added'; medicationName: string; times: string[] };

export function toAddMedicationArgs(raw: unknown): AddMedicationArgs {
  const obj = isRecord(raw) ? raw : {};
  const times = Array.isArray(obj.times) ? obj.times.filter((t): t is string => typeof t === 'string') : undefined;
  return {
    name: typeof obj.name === 'string' ? obj.name : '',
    dosage: typeof obj.dosage === 'string' ? obj.dosage : null,
    form: typeof obj.form === 'string' ? obj.form : null,
    notes: typeof obj.notes === 'string' ? obj.notes : null,
    pillsRemaining: typeof obj.pillsRemaining === 'number' ? obj.pillsRemaining : null,
    refillThreshold: typeof obj.refillThreshold === 'number' ? obj.refillThreshold : null,
    times,
    confirmed: obj.confirmed === true,
  };
}

/** Mirrors app/(tabs)/medications/new.tsx's submit flow: one createMedication, then one
 * createSchedule + scheduleDoseReminders per requested time, all on a fixed daily recurrence —
 * voice/chat add doesn't offer weekly/monthly recurrence in v1. */
export async function addMedication(args: AddMedicationArgs): Promise<AddMedicationResult> {
  const name = args.name.trim();
  if (!name) {
    throw new Error('add_medication requires a non-empty name');
  }

  const times = args.times && args.times.length > 0 ? args.times : DEFAULT_TIMES;
  for (const time of times) {
    if (!TIME_PATTERN.test(time)) {
      throw new Error(`add_medication: invalid time "${time}", expected 24-hour "HH:MM"`);
    }
  }

  const input: MedicationInput = {
    name,
    dosage: args.dosage?.trim() || null,
    form: args.form?.trim() || null,
    notes: args.notes?.trim() || null,
    pillsRemaining: args.pillsRemaining ?? null,
    refillThreshold: args.refillThreshold ?? null,
  };

  const duplicate = await findDuplicateMedication(input);
  if (duplicate.exact) {
    return { status: 'exists', medicationName: duplicate.exact.name };
  }
  if (duplicate.partial.length > 0 && !args.confirmed) {
    return {
      status: 'needs_confirmation',
      medicationName: name,
      similar: duplicate.partial.map((m) => ({ name: m.name, dosage: m.dosage, form: m.form })),
    };
  }

  const medicationId = await createMedication(input);
  for (const time of times) {
    const scheduleId = await createSchedule(medicationId, time, {
      recurrenceType: 'daily',
      daysOfWeek: null,
      startDate: null,
      endDate: null,
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
      endDate: null,
    });
  }

  return { status: 'added', medicationName: name, times };
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

export type ToolName = 'add_medication' | 'get_todays_doses' | 'mark_dose_taken';

export const TOOL_DESCRIPTIONS: Record<ToolName, string> = {
  add_medication:
    'add_medication(name: string, dosage?: string, form?: string, notes?: string, pillsRemaining?: number, ' +
    'refillThreshold?: number, times?: string[] ["HH:MM" 24h, default ["08:00"]], confirmed?: boolean) — adds a ' +
    'new medication with daily reminders. If the result status is "exists", tell the user it\'s already tracked. ' +
    'If "needs_confirmation", relay the similar medication(s) and re-call with confirmed:true only if they agree.',
  get_todays_doses: 'get_todays_doses() — returns every dose scheduled for today with its status.',
  mark_dose_taken:
    'mark_dose_taken(medicationName: string, scheduledTime?: string ["HH:MM"], confirmed?: boolean) — marks a ' +
    'pending dose as taken. If the result status is "not_found", tell the user and list today\'s medications. If ' +
    '"ambiguous", ask which time and re-call with scheduledTime set. If "needs_confirmation", ask the user to ' +
    'confirm and re-call with confirmed:true only if they agree — never set confirmed:true unless the user just did.',
};

/** Runs a tool call and always resolves — a bad tool name or invalid arguments becomes a
 * `{ error }` result fed back to the model instead of throwing, so a single malformed model
 * response can't crash the whole conversation turn. */
export async function runTool(name: string, args: unknown): Promise<unknown> {
  try {
    switch (name as ToolName) {
      case 'add_medication':
        return await addMedication(toAddMedicationArgs(args));
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

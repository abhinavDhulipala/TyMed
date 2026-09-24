import { formatFullDateLabel, formatTime } from '@/src/utils/date';

// The assistant's confirmation questions and "done" messages are rendered here from the tool
// result, not phrased by the model: on-device evals showed Gemini Nano describing a
// not-yet-saved change as done ("I've updated your schedule… is that correct?") and sometimes
// skipping the question entirely. For health data the exact change being confirmed must be
// stated accurately, so it's deterministic.

type Schedule = { times: string[]; endDate: string | null };

function describeTimes(times: string[]): string {
  const formatted = times.map(formatTime);
  return formatted.length <= 2 ? formatted.join(' and ') : `${formatted.slice(0, -1).join(', ')} and ${formatted.at(-1)}`;
}

function describeSchedule({ times, endDate }: Schedule): string {
  const label = endDate ? formatFullDateLabel(endDate) : null;
  const until = label ? ` through ${label === 'Today' ? 'today' : label}` : '';
  return `${describeTimes(times)} every day${until}`;
}

function nameWithDosage(name: string, dosage: string | null | undefined): string {
  return dosage ? `${name} ${dosage}` : name;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** The yes/no question for a needs_confirmation result, or null for a result shape it doesn't
 * know (the model phrases those instead). */
export function confirmationQuestion(result: unknown): string | null {
  if (!isRecord(result) || result.status !== 'needs_confirmation') return null;
  const name = String(result.medicationName);

  if (isRecord(result.before) && isRecord(result.after)) {
    return `Change ${name} from ${describeSchedule(result.before as Schedule)} to ${describeSchedule(result.after as Schedule)}?`;
  }
  if (Array.isArray(result.times)) {
    const similar = Array.isArray(result.similar) ? (result.similar as { name: string; dosage: string | null }[]) : [];
    const note = similar.length
      ? ` Note: you already track ${similar.map((m) => nameWithDosage(m.name, m.dosage)).join(', ')} — this would be a separate medication.`
      : '';
    const schedule = describeSchedule({ times: result.times as string[], endDate: (result.endDate as string | null) ?? null });
    return `Add ${nameWithDosage(name, result.dosage as string | null)} at ${schedule}?${note}`;
  }
  if (typeof result.scheduledTime === 'string') {
    return `Mark your ${formatTime(result.scheduledTime)} ${name} dose as taken?`;
  }
  return null;
}

/** The confirmation message after a write went through, or null if nothing was saved. */
export function doneMessage(result: unknown): string | null {
  if (!isRecord(result)) return null;
  const name = String(result.medicationName);
  switch (result.status) {
    case 'added':
      return `Done — added ${name} at ${describeSchedule(result as unknown as Schedule)}.`;
    case 'updated':
      return `Done — ${name} is now at ${describeSchedule(result.after as Schedule)}.`;
    case 'marked_taken':
      return `Done — marked your ${formatTime(String(result.scheduledTime))} ${name} dose as taken.`;
    default:
      return null;
  }
}

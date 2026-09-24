import { todayDateString } from '@/src/utils/date';
import { TOOL_DESCRIPTIONS } from './tools';

export interface HistoryEntry {
  role: 'user' | 'assistant' | 'tool';
  content: string;
}

export interface TrackedMedication {
  name: string;
  dosage: string | null;
}

// Worked examples: on-device evals showed Gemini Nano reaching for add_medication to change an
// existing medication, inventing arguments (pill counts, "as directed"), and answering "I've
// marked it" without calling a tool. A few concrete input -> call pairs fix that far more
// reliably than more rules.
const EXAMPLES = [
  ['I took my aspirin', '{"tool": "mark_dose_taken", "arguments": {"medicationName": "aspirin"}}'],
  [
    'Make ibuprofen twice a day at 9am and 9pm for 5 days',
    '{"tool": "update_medication_schedule", "arguments": {"medicationName": "ibuprofen", "times": ["09:00", "21:00"], "durationDays": 5}}',
  ],
  // Pairs with the example above: on-device evals showed Gemini Nano copying that example's
  // durationDays even when the user never mentioned one, inventing an end date for the change.
  [
    'Make ibuprofen twice a day at 9am and 9pm',
    '{"tool": "update_medication_schedule", "arguments": {"medicationName": "ibuprofen", "times": ["09:00", "21:00"]}}',
  ],
  [
    'Add vitamin D 1000 IU at 8am',
    '{"tool": "add_medication", "arguments": {"name": "vitamin D", "dosage": "1000 IU", "times": ["08:00"]}}',
  ],
  ["What's left today?", '{"tool": "get_todays_doses", "arguments": {}}'],
];

// get_todays_doses is the only tool whose result the model itself turns into a reply — every
// write tool's confirmation question is templated by the app, so the model never has to. Without
// a worked example of that specific "Tool result: ... -> {"reply": ...}" step, on-device evals
// showed Gemini Nano calling get_todays_doses a second time instead of answering from the result.
const TOOL_RESULT_EXAMPLE =
  "User: What's left today?\n" +
  'Assistant: {"tool": "get_todays_doses", "arguments": {}}\n' +
  'Tool result: [{"medicationName": "aspirin", "dosage": "81 mg", "scheduledTime": "08:00", "status": "pending"}]\n' +
  'Assistant: {"reply": "You have aspirin 81 mg at 8:00 AM left today."}';

// generateContent() is a plain stateless prompt->text call with no native session of its own, so
// the full instructions + transcript are rebuilt from scratch on every turn.
function systemPreamble(today: string, medications: TrackedMedication[]): string {
  const toolDocs = Object.values(TOOL_DESCRIPTIONS)
    .map((desc) => `- ${desc}`)
    .join('\n');
  const tracked = medications.length
    ? medications.map((m) => (m.dosage ? `${m.name} ${m.dosage}` : m.name)).join(', ')
    : 'none yet';
  const examples = EXAMPLES.map(([user, call]) => `User: ${user}\nAssistant: ${call}`).join('\n');

  return (
    `You are TyMed's medication assistant, running fully on-device. Today's date is ${today}.\n` +
    `The user already tracks these medications: ${tracked}.\n\n` +
    `You can call these tools:\n${toolDocs}\n\n` +
    'Respond with ONLY a JSON object: either {"tool": "<tool_name>", "arguments": {...}} to call a tool, or ' +
    '{"reply": "<message to show the user>"} to reply directly. No text outside the JSON, no markdown code fences.\n' +
    'To change the schedule of a medication the user already tracks, use update_medication_schedule, never ' +
    'add_medication. Only pass arguments the user actually said — never make up a dosage, durationDays, or other ' +
    'detail; omit durationDays entirely unless the user gave a number of days.\n' +
    'Anything that changes data is confirmed with the user by the app. Never tell the user something was added, ' +
    'changed, or marked unless a tool result says saved:true.\n' +
    'When the transcript already has a "Tool result:" for the question just asked, answer it with a {"reply": ...} ' +
    'using that result — do not call the same tool again.\n\n' +
    `Examples:\n${examples}\n${TOOL_RESULT_EXAMPLE}`
  );
}

// Gemini Nano's context window is small and every turn resends the whole transcript, so this
// caps how much history gets rebuilt into each prompt rather than growing unbounded over a long
// chat.
const MAX_HISTORY_ENTRIES = 12;

export function buildPrompt(history: HistoryEntry[], medications: TrackedMedication[] = []): string {
  const trimmed = history.slice(-MAX_HISTORY_ENTRIES);
  const transcript = trimmed
    .map((entry) => {
      if (entry.role === 'user') return `User: ${entry.content}`;
      if (entry.role === 'assistant') return `Assistant: ${entry.content}`;
      return `Tool result: ${entry.content}`;
    })
    .join('\n');

  return `${systemPreamble(todayDateString(), medications)}\n\n${transcript}\n\nAssistant:`;
}

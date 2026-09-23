import { todayDateString } from '@/src/utils/date';
import { TOOL_DESCRIPTIONS } from './tools';

export interface HistoryEntry {
  role: 'user' | 'assistant' | 'tool';
  content: string;
}

// generateContent() is a plain stateless prompt->text call with no native session of its own, so
// the full instructions + transcript are rebuilt from scratch on every turn.
function systemPreamble(today: string): string {
  const toolDocs = Object.values(TOOL_DESCRIPTIONS)
    .map((desc) => `- ${desc}`)
    .join('\n');

  return (
    `You are TyMed's medication assistant, running fully on-device. Today's date is ${today}.\n\n` +
    `You can call these tools:\n${toolDocs}\n\n` +
    'Respond with ONLY a JSON object: either {"tool": "<tool_name>", "arguments": {...}} to call a tool, or ' +
    '{"reply": "<message to show the user>"} to reply directly. No text outside the JSON, no markdown code fences.\n' +
    'Only set confirmed:true on a tool call immediately after the user has explicitly agreed to what you asked them.'
  );
}

// Gemini Nano's context window is small and every turn resends the whole transcript, so this
// caps how much history gets rebuilt into each prompt rather than growing unbounded over a long
// chat.
const MAX_HISTORY_ENTRIES = 12;

export function buildPrompt(history: HistoryEntry[]): string {
  const trimmed = history.slice(-MAX_HISTORY_ENTRIES);
  const transcript = trimmed
    .map((entry) => {
      if (entry.role === 'user') return `User: ${entry.content}`;
      if (entry.role === 'assistant') return `Assistant: ${entry.content}`;
      return `Tool result: ${entry.content}`;
    })
    .join('\n');

  return `${systemPreamble(todayDateString())}\n\n${transcript}\n\nAssistant:`;
}

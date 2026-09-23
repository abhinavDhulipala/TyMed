import { generateNative } from '@/src/native/aiModule';
import { buildPrompt, type HistoryEntry } from './prompt';
import { parseTurn } from './parseTurn';
import { runTool } from './tools';

const MAX_ITERATIONS = 4;
const FALLBACK_REPLY = "I didn't catch that — could you try rephrasing?";

export interface RunTurnResult {
  reply: string;
  history: HistoryEntry[];
}

/** Drives one user message through the model, running any tool calls it makes and looping back
 * with the result, until it produces a direct reply. Bounded so a model stuck retrying the same
 * malformed call (or looping tool calls) can't hang the conversation. */
export async function runTurn(userMessage: string, history: HistoryEntry[]): Promise<RunTurnResult> {
  let workingHistory: HistoryEntry[] = [...history, { role: 'user', content: userMessage }];
  let usedCorrectiveRetry = false;
  let lastToolCallSignature: string | null = null;

  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const prompt = buildPrompt(workingHistory);
    const raw = await generateNative(prompt);
    const turn = parseTurn(raw);

    if (turn.kind === 'reply') {
      workingHistory = [...workingHistory, { role: 'assistant', content: turn.text }];
      return { reply: turn.text, history: workingHistory };
    }

    if (turn.kind === 'unparseable') {
      if (usedCorrectiveRetry) {
        return { reply: FALLBACK_REPLY, history: workingHistory };
      }
      usedCorrectiveRetry = true;
      workingHistory = [
        ...workingHistory,
        {
          role: 'tool',
          content: 'Your last response was not valid JSON. Respond again with ONLY {"tool": ...} or {"reply": ...}.',
        },
      ];
      continue;
    }

    // A model retrying the exact same tool call after seeing its own result is stuck, not
    // making progress — stop rather than burning the rest of the iteration budget on it.
    const callSignature = `${turn.name}:${JSON.stringify(turn.arguments)}`;
    if (callSignature === lastToolCallSignature) {
      return { reply: FALLBACK_REPLY, history: workingHistory };
    }
    lastToolCallSignature = callSignature;

    const result = await runTool(turn.name, turn.arguments);
    workingHistory = [
      ...workingHistory,
      { role: 'assistant', content: JSON.stringify({ tool: turn.name, arguments: turn.arguments }) },
      { role: 'tool', content: JSON.stringify(result) },
    ];
  }

  return { reply: FALLBACK_REPLY, history: workingHistory };
}

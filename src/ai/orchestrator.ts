import { generateNative } from '@/src/native/aiModule';
import { listMedications } from '@/src/db/medications';
import { confirmationQuestion, doneMessage } from './messages';
import { buildPrompt, type HistoryEntry } from './prompt';
import { parseTurn } from './parseTurn';
import { runTool } from './tools';

const MAX_ITERATIONS = 4;
const FALLBACK_REPLY = "I didn't catch that — could you try rephrasing?";
const CANCELLED_REPLY = "Okay, I won't make that change.";
const UNBACKED_CLAIM_CORRECTION =
  'You have NOT changed anything: no tool result in this turn says saved:true. Call the tool that does what the ' +
  'user asked, or tell them you could not do it.';

// "I've marked…", "I added…" — a claim that something was written, which must be backed by a
// saved tool result this turn. Evals caught Gemini Nano replying "I've marked your aspirin dose
// as taken" without calling any tool.
const WRITE_CLAIM = /\b(i'?ve|i have|i)\s+(just\s+|now\s+)?(added|marked|updated|changed|saved|scheduled|set|removed)\b/i;

// A digit before day(s)/week(s)/month(s) — "for 7 days", "in 2 weeks" — but not "every day" or
// "twice a day", which are frequency, not duration.
const DURATION_MENTION = /\b\d+\s*-?\s*(day|days|week|weeks|month|months)\b/i;

/** Strips a durationDays the user's own message doesn't support. On-device evals showed Gemini
 * Nano copying durationDays from the prompt's worked example even when nothing about how long
 * the change should run was said — a worked example alone didn't fix it reliably enough for
 * health data, so it's enforced here rather than trusted from the model. */
function stripUnmentionedDuration(args: Record<string, unknown>, userMessage: string): Record<string, unknown> {
  if (args.durationDays === undefined || DURATION_MENTION.test(userMessage)) return args;
  const { durationDays: _dropped, ...rest } = args;
  return rest;
}

/** A write the user has been asked to confirm — held by the app, not the model. */
export interface PendingAction {
  name: string;
  arguments: Record<string, unknown>;
}

export interface RunTurnResult {
  reply: string;
  history: HistoryEntry[];
  pending: PendingAction | null;
}

// Deliberately narrow: only a clear, unqualified yes applies a write. Anything with a "but"/"no"
// in it ("yes but make it 9pm") goes back to the model as a normal message instead.
const AFFIRMATIVE =
  /^(y|yes|yeah|yep|yup|sure|ok|okay|confirm(ed)?|correct|right|do it|go ahead|please do|sounds (good|right)|that'?s (right|correct))\b/;
const NEGATIVE = /^(n|no|nope|nah|cancel|stop|don'?t|never ?mind)\b/;
const QUALIFIER = /\b(but|no|not|don'?t|instead|change|except)\b/;

function normalize(message: string): string {
  return message.trim().toLowerCase().replace(/[.!]+$/, '');
}

export function isAffirmative(message: string): boolean {
  const text = normalize(message);
  return AFFIRMATIVE.test(text) && !QUALIFIER.test(text);
}

export function isNegative(message: string): boolean {
  return NEGATIVE.test(normalize(message));
}

function wasSaved(result: unknown): boolean {
  return doneMessage(result) !== null;
}

/** Spells out in the result itself whether anything was written, for the model reading the
 * transcript on later turns. */
function annotateToolResult(result: unknown): unknown {
  if (typeof result !== 'object' || result === null || !('status' in result)) return result;
  if (result.status === 'needs_confirmation') return { ...result, saved: false };
  if (wasSaved(result)) return { ...result, saved: true };
  return result;
}

function needsConfirmation(result: unknown): boolean {
  return typeof result === 'object' && result !== null && 'status' in result && result.status === 'needs_confirmation';
}

/** Drives one user message through the model, running any tool calls it makes and looping back
 * with the result, until it produces a direct reply. Bounded so a model stuck retrying the same
 * malformed call (or looping tool calls) can't hang the conversation.
 *
 * Confirmation is decided here, not by the model: the model's tool calls never carry
 * confirmed:true (it's stripped), a needs_confirmation result is remembered as `pending`, and
 * only the user's next message being a clear yes re-runs that exact call confirmed. Gemini Nano
 * doesn't reliably set the flag itself — and for health data it shouldn't be the one deciding. */
export async function runTurn(
  userMessage: string,
  history: HistoryEntry[],
  pending: PendingAction | null = null
): Promise<RunTurnResult> {
  let workingHistory: HistoryEntry[] = [...history, { role: 'user', content: userMessage }];
  let nextPending: PendingAction | null = null;

  if (pending && isNegative(userMessage)) {
    workingHistory = [...workingHistory, { role: 'assistant', content: CANCELLED_REPLY }];
    return { reply: CANCELLED_REPLY, history: workingHistory, pending: null };
  }

  let savedThisTurn = false;

  if (pending && isAffirmative(userMessage)) {
    const confirmedArgs = { ...pending.arguments, confirmed: true };
    const result = await runTool(pending.name, confirmedArgs);
    workingHistory = [
      ...workingHistory,
      { role: 'assistant', content: JSON.stringify({ tool: pending.name, arguments: confirmedArgs }) },
      { role: 'tool', content: JSON.stringify(annotateToolResult(result)) },
    ];
    const done = doneMessage(result);
    if (done) {
      workingHistory = [...workingHistory, { role: 'assistant', content: done }];
      return { reply: done, history: workingHistory, pending: null };
    }
    // An error or an unexpected result — let the model explain it.
  }

  const medications = (await listMedications()).map((m) => ({ name: m.name, dosage: m.dosage }));
  let usedCorrectiveRetry = false;
  let usedClaimRetry = false;
  let lastToolCallSignature: string | null = null;

  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const prompt = buildPrompt(workingHistory, medications);
    const raw = await generateNative(prompt);
    const turn = parseTurn(raw);

    if (turn.kind === 'reply') {
      if (!savedThisTurn && WRITE_CLAIM.test(turn.text)) {
        if (usedClaimRetry) {
          return { reply: FALLBACK_REPLY, history: workingHistory, pending: nextPending };
        }
        usedClaimRetry = true;
        workingHistory = [
          ...workingHistory,
          { role: 'assistant', content: JSON.stringify({ reply: turn.text }) },
          { role: 'tool', content: UNBACKED_CLAIM_CORRECTION },
        ];
        continue;
      }
      workingHistory = [...workingHistory, { role: 'assistant', content: turn.text }];
      return { reply: turn.text, history: workingHistory, pending: nextPending };
    }

    if (turn.kind === 'unparseable') {
      if (usedCorrectiveRetry) {
        return { reply: FALLBACK_REPLY, history: workingHistory, pending: nextPending };
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

    const { confirmed: _ignored, ...rawArgs } = turn.arguments;
    const args = stripUnmentionedDuration(rawArgs, userMessage);

    // A model retrying the exact same tool call after seeing its own result is stuck, not
    // making progress — stop rather than burning the rest of the iteration budget on it.
    const callSignature = `${turn.name}:${JSON.stringify(args)}`;
    if (callSignature === lastToolCallSignature) {
      return { reply: FALLBACK_REPLY, history: workingHistory, pending: nextPending };
    }
    lastToolCallSignature = callSignature;

    const result = await runTool(turn.name, args);
    savedThisTurn ||= wasSaved(result);
    nextPending = needsConfirmation(result) ? { name: turn.name, arguments: args } : null;
    workingHistory = [
      ...workingHistory,
      { role: 'assistant', content: JSON.stringify({ tool: turn.name, arguments: args }) },
      { role: 'tool', content: JSON.stringify(annotateToolResult(result)) },
    ];

    const question = nextPending ? confirmationQuestion(result) : null;
    if (question) {
      workingHistory = [...workingHistory, { role: 'assistant', content: question }];
      return { reply: question, history: workingHistory, pending: nextPending };
    }
  }

  return { reply: FALLBACK_REPLY, history: workingHistory, pending: nextPending };
}

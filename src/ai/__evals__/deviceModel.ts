import { execFileSync } from 'node:child_process';

// Host-side client for the eval bridge in modules/tymed-ai (TymedAiModule.kt): sends a prompt
// to the real Gemini Nano on a USB-connected phone over adb and waits for its answer. Gemini
// Nano only runs inside AICore on-device — there is no off-device build of the model — so
// this is the only way to exercise the orchestrator against the model it actually ships with.

export const EVAL_PACKAGE = process.env.TYMED_EVAL_PACKAGE ?? 'com.tymed.app.dev';
const RESULT_DIR = `/sdcard/Android/data/${EVAL_PACKAGE}/files/ai-eval`;
const TIMEOUT_MS = 120_000;

function adb(args: string[]): string {
  const serial = process.env.ANDROID_SERIAL ? ['-s', process.env.ANDROID_SERIAL] : [];
  return execFileSync('adb', [...serial, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Brings the app to the foreground — AICore refuses inference for background apps. */
export function openAppOnDevice(): void {
  adb(['shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', 'tymed://assistant', EVAL_PACKAGE]);
}

export async function generateOnDevice(prompt: string): Promise<string> {
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const promptB64 = Buffer.from(prompt, 'utf8').toString('base64');
  adb(['shell', 'am', 'broadcast', '-a', 'expo.modules.tymedai.EVAL', '-p', EVAL_PACKAGE, '--es', 'id', id, '--es', 'prompt_b64', promptB64]);

  const resultPath = `${RESULT_DIR}/${id}.txt`;
  const deadline = Date.now() + TIMEOUT_MS;
  while (Date.now() < deadline) {
    await sleep(500);
    let output: string;
    try {
      output = adb(['shell', 'cat', resultPath]);
    } catch {
      continue; // not written yet
    }
    adb(['shell', 'rm', '-f', resultPath]);
    if (output.startsWith('OK\n')) return output.slice(3);
    throw new Error(`On-device model failed: ${output}`);
  }
  throw new Error(
    `No response from ${EVAL_PACKAGE} within ${TIMEOUT_MS / 1000}s — is the phone unlocked with the app open, ` +
      'and was the app built with -PtymedAiEval=true?'
  );
}

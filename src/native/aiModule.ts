import { Platform } from 'react-native';
import { captureException } from '@/src/observability/sentry';
import type { AiPrepareStatus } from '../../modules/tymed-ai/src/TymedAi.types';

type TymedAiNativeModule = {
  prepare(): Promise<AiPrepareStatus>;
  generate(prompt: string): Promise<string>;
};

// Guarded require (not a static import): the native module only exists once the dev client has
// been rebuilt with it. A static import would throw at module-evaluation time and take the whole
// app down before that rebuild — same pattern as src/native/alarmModule.ts.
let nativeModule: TymedAiNativeModule | null = null;

if (Platform.OS === 'android') {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    nativeModule = require('../../modules/tymed-ai/src/TymedAiModule').default as TymedAiNativeModule;
  } catch (error) {
    console.warn('[ai] TymedAi native module unavailable:', error);
    captureException(error);
  }
}

/** True once the native module is linked at all (Android + rebuilt dev client) — says nothing
 * about whether the device actually supports Gemini Nano, see prepareNativeAi() for that. */
export function isNativeAiAvailable(): boolean {
  return nativeModule !== null;
}

/** Prepares (and, the first time, downloads) the on-device model. Resolves false if the module
 * isn't linked, the device doesn't support AICore, or preparation otherwise fails — there's no
 * finer-grained reason to report today, so this collapses "unsupported" and "download failed"
 * into the same result. */
export async function prepareNativeAi(): Promise<boolean> {
  if (!nativeModule) return false;
  try {
    const status = await nativeModule.prepare();
    return status === 'available';
  } catch (error) {
    console.warn('[ai] failed to prepare on-device model:', error);
    captureException(error);
    return false;
  }
}

/** Runs one prompt->text call against the already-prepared model. Throws if the module is
 * unavailable or prepare() hasn't succeeded yet — callers are expected to have gated on
 * prepareNativeAi() first, so this failing is a real bug rather than an expected state. */
export async function generateNative(prompt: string): Promise<string> {
  if (!nativeModule) {
    throw new Error('TymedAi native module unavailable');
  }
  return nativeModule.generate(prompt);
}

import { Platform } from 'react-native';
import { captureException } from '@/src/observability/sentry';
import type { AlarmRequest } from '../../modules/tymed-alarm/src/TymedAlarm.types';

type TymedAlarmNativeModule = {
  scheduleAlarm(request: AlarmRequest): void;
  cancelAlarm(requestCode: number): void;
  setFollowUpMinutes(minutes: number): void;
};

// Guarded require (not a static import): the native module only exists once the dev client
// has been rebuilt with it. A static import would throw at module-evaluation time and take
// the whole app down before that rebuild — same failure mode we hit with expo-notifications
// in Expo Go, so the fix is the same guarded pattern.
let nativeModule: TymedAlarmNativeModule | null = null;

if (Platform.OS === 'android') {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    nativeModule = require('../../modules/tymed-alarm/src/TymedAlarmModule').default as TymedAlarmNativeModule;
  } catch (error) {
    console.warn('[alarm] TymedAlarm native module unavailable:', error);
    captureException(error);
  }
}

export function isNativeAlarmAvailable(): boolean {
  return nativeModule !== null;
}

export function scheduleNativeAlarm(request: AlarmRequest): void {
  try {
    nativeModule?.scheduleAlarm(request);
  } catch (error) {
    // A silent failure here means a dose alarm just never rings — worth real visibility.
    console.warn('[alarm] failed to schedule native alarm:', error);
    captureException(error);
  }
}

export function cancelNativeAlarm(requestCode: number): void {
  try {
    nativeModule?.cancelAlarm(requestCode);
  } catch (error) {
    console.warn('[alarm] failed to cancel native alarm:', error);
    captureException(error);
  }
}

export function setNativeFollowUpMinutes(minutes: number): void {
  try {
    nativeModule?.setFollowUpMinutes(minutes);
  } catch (error) {
    console.warn('[alarm] failed to set follow-up minutes:', error);
    captureException(error);
  }
}

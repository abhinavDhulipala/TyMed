import { NativeModule, requireNativeModule } from 'expo';
import type { AlarmRequest } from './TymedAlarm.types';

declare class TymedAlarmModule extends NativeModule<{}> {
  scheduleAlarm(request: AlarmRequest): void;
  cancelAlarm(requestCode: number): void;
  stopRinging(): void;
  setFollowUpMinutes(minutes: number): void;
}

export default requireNativeModule<TymedAlarmModule>('TymedAlarm');

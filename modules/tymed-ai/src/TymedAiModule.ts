import { NativeModule, requireNativeModule } from 'expo';
import type { AiPrepareStatus } from './TymedAi.types';

declare class TymedAiModule extends NativeModule<{}> {
  prepare(): Promise<AiPrepareStatus>;
  generate(prompt: string): Promise<string>;
}

export default requireNativeModule<TymedAiModule>('TymedAi');

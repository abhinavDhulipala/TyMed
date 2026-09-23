import { registerWebModule, NativeModule } from 'expo';

// TymedAiModule is not available on the web platform.
class TymedAiModule extends NativeModule<{}> {}

export default registerWebModule(TymedAiModule, 'TymedAiModule');

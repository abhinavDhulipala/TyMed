import { registerWebModule, NativeModule } from 'expo';

// TymedAlarmModule is not available on the web platform.
class TymedAlarmModule extends NativeModule<{}> {}

export default registerWebModule(TymedAlarmModule, 'TymedAlarmModule');

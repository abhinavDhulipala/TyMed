/** What TymedAiModule.prepare() resolves to — the native side runs ML Kit's checkStatus() and,
 * if needed, downloads the on-device model, so this doubles as both the availability check and
 * the trigger for that download. */
export type AiPrepareStatus = 'available' | 'unavailable';

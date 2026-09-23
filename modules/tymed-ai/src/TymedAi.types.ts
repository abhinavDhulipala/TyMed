/** What TymedAiModule.prepare() resolves to — the real AICore SDK has no side-effect-free
 * "is it available" query, so this doubles as both the availability check and the trigger for
 * downloading the on-device model if needed. */
export type AiPrepareStatus = 'available' | 'unavailable';

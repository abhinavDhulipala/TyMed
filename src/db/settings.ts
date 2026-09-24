import { getDb } from './client';

export const FOLLOW_UP_MINUTES_KEY = 'follow_up_minutes';
export const DEFAULT_FOLLOW_UP_MINUTES = 5;

export const USE_24_HOUR_KEY = 'use_24_hour_format';

export const AI_ASSISTANT_ENABLED_KEY = 'ai_assistant_enabled';

export async function getSetting(key: string): Promise<string | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ value: string }>('SELECT value FROM app_settings WHERE key = ?', key);
  return row?.value ?? null;
}

export async function setSetting(key: string, value: string): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    'INSERT INTO app_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
    key,
    value
  );
}

export async function getFollowUpMinutes(): Promise<number> {
  const raw = await getSetting(FOLLOW_UP_MINUTES_KEY);
  const parsed = raw ? Number(raw) : NaN;
  return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_FOLLOW_UP_MINUTES;
}

export async function setFollowUpMinutes(minutes: number): Promise<void> {
  await setSetting(FOLLOW_UP_MINUTES_KEY, String(minutes));
}

export async function getUse24HourFormat(): Promise<boolean> {
  const raw = await getSetting(USE_24_HOUR_KEY);
  return raw === '1';
}

export async function setUse24HourFormat(value: boolean): Promise<void> {
  await setSetting(USE_24_HOUR_KEY, value ? '1' : '0');
}

// Opt-in (default off): enabling it is the first time the app talks to anything beyond its own
// local SQLite — even though inference stays on-device, the first prepare() may need to download
// the model, so this shouldn't happen silently just because the user opened the Assistant tab.
export async function getAiAssistantEnabled(): Promise<boolean> {
  const raw = await getSetting(AI_ASSISTANT_ENABLED_KEY);
  return raw === '1';
}

export async function setAiAssistantEnabled(value: boolean): Promise<void> {
  await setSetting(AI_ASSISTANT_ENABLED_KEY, value ? '1' : '0');
}
